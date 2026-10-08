package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;

import javax.sql.DataSource;

import com.vladdancea.viennapulse.TestcontainersConfiguration;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.NotModified;
import com.vladdancea.viennapulse.gtfs.GtfsFeedUpdater.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GtfsFeedUpdaterTests {

	private static final FeedValidators V1 = new FeedValidators(Instant.parse("2026-10-05T04:09:05Z"), "\"v1\"");

	private static final FeedValidators V2 = new FeedValidators(Instant.parse("2026-11-02T04:00:00Z"), "\"v2\"");

	private static final Downloaded FEED_A = new Downloaded(Path.of("gtfs.zip"), "a".repeat(64), 100, V1);

	private static final Downloaded FEED_B = new Downloaded(Path.of("gtfs.zip"), "b".repeat(64), 200, V2);

	@Autowired
	private GtfsFeedUpdater updater;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private DataSource dataSource;

	@MockitoBean
	private GtfsDownloader downloader;

	@MockitoBean
	private GtfsImporter importer;

	@BeforeEach
	void emptyDatabase() {
		jdbc.sql("TRUNCATE gtfs_feed_version").update();
	}

	@Test
	void importsTheFirstFeed() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);

		assertThat(updater.update(false)).isEqualTo(Outcome.IMPORTED);

		verify(importer).importFeed(anyLong(), eq(FEED_A.file()));
		assertThat(activeSha256()).isEqualTo(FEED_A.sha256());
	}

	@Test
	void sendsTheStoredValidatorsAndStopsOnNotModified() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);
		given(downloader.download(V1)).willReturn(new NotModified());

		assertThat(updater.update(false)).isEqualTo(Outcome.NOT_MODIFIED);

		verify(importer, times(1)).importFeed(anyLong(), any());
	}

	@Test
	void skipsTheImportWhenTheContentIsTheSame() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);
		Downloaded sameContentNewEtag = new Downloaded(FEED_A.file(), FEED_A.sha256(), FEED_A.sizeBytes(), V2);
		given(downloader.download(V1)).willReturn(sameContentNewEtag);

		assertThat(updater.update(false)).isEqualTo(Outcome.UNCHANGED);

		verify(importer, times(1)).importFeed(anyLong(), any());
		assertThat(jdbc.sql("SELECT etag FROM gtfs_feed_version WHERE active").query(String.class).single())
			.isEqualTo("\"v2\"");
	}

	@Test
	void replacesTheActiveFeedWithANewOne() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);
		given(downloader.download(V1)).willReturn(FEED_B);

		assertThat(updater.update(false)).isEqualTo(Outcome.IMPORTED);

		assertThat(activeSha256()).isEqualTo(FEED_B.sha256());
		assertThat(jdbc.sql("SELECT count(*) FROM gtfs_feed_version").query(Long.class).single()).isEqualTo(2);
	}

	@Test
	void keepsOnlyTheActiveAndThePreviousVersion() {
		FeedValidators v3 = new FeedValidators(Instant.parse("2026-12-01T04:00:00Z"), "\"v3\"");
		Downloaded feedC = new Downloaded(Path.of("gtfs.zip"), "c".repeat(64), 300, v3);
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);
		given(downloader.download(V1)).willReturn(FEED_B);
		updater.update(false);
		given(downloader.download(V2)).willReturn(feedC);

		assertThat(updater.update(false)).isEqualTo(Outcome.IMPORTED);

		assertThat(jdbc.sql("SELECT sha256 FROM gtfs_feed_version ORDER BY id").query(String.class).list())
			.containsExactly(FEED_B.sha256(), feedC.sha256());
		assertThat(activeSha256()).isEqualTo(feedC.sha256());
	}

	@Test
	void keepsTheOldFeedActiveWhenTheImportFails() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);
		given(downloader.download(V1)).willReturn(FEED_B);
		willThrow(new IllegalStateException("broken stop_times.txt")).given(importer)
			.importFeed(anyLong(), eq(FEED_B.file()));

		assertThatIllegalStateException().isThrownBy(() -> updater.update(false)).withMessage("broken stop_times.txt");

		assertThat(activeSha256()).isEqualTo(FEED_A.sha256());
	}

	@Test
	void startupRunSkipsWhenAFeedIsActive() {
		given(downloader.download(FeedValidators.NONE)).willReturn(FEED_A);
		updater.update(false);

		assertThat(updater.update(true)).isEqualTo(Outcome.ALREADY_HAS_FEED);

		verify(downloader, times(1)).download(any());
	}

	@Test
	void doesNothingWhileAnotherRunHoldsTheLock() throws Exception {
		try (Connection other = dataSource.getConnection();
				PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_lock(?)")) {
			lock.setLong(1, GtfsFeedUpdater.LOCK_KEY);
			lock.execute();

			assertThat(updater.update(false)).isEqualTo(Outcome.ALREADY_RUNNING);

			verifyNoInteractions(downloader);
			try (PreparedStatement unlock = other.prepareStatement("SELECT pg_advisory_unlock(?)")) {
				unlock.setLong(1, GtfsFeedUpdater.LOCK_KEY);
				unlock.execute();
			}
		}
		verify(importer, never()).importFeed(anyLong(), any());
	}

	private String activeSha256() {
		return jdbc.sql("SELECT sha256 FROM gtfs_feed_version WHERE active").query(String.class).single();
	}

}
