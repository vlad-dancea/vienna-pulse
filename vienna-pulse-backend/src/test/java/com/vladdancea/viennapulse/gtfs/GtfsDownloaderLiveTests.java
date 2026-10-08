package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.NotModified;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

/**
 * Downloads the real Wiener Linien feed (about 90 MB). Off by default so CI does not
 * depend on the network. Run with {@code GTFS_LIVE=true ./gradlew test --tests '*LiveTests'}.
 */
@EnabledIfEnvironmentVariable(named = "GTFS_LIVE", matches = "true")
class GtfsDownloaderLiveTests {

	private static final URI FEED_URL = URI.create("https://www.wienerlinien.at/ogd_realtime/doku/ogd/gtfs/gtfs.zip");

	@TempDir
	Path workDir;

	@Test
	void downloadsTheRealFeedAndThenGetsNotModified() {
		GtfsDownloader downloader = new GtfsDownloader(RestClient.builder(),
				new GtfsProperties(FEED_URL, workDir, Duration.ofSeconds(10), Duration.ofSeconds(60)));

		GtfsDownloadResult first = downloader.download(FeedValidators.NONE);
		assertThat(first).isInstanceOf(Downloaded.class);
		Downloaded feed = (Downloaded) first;
		System.out.printf("GTFS live: %d bytes, sha256 %s, validators %s%n", feed.sizeBytes(), feed.sha256(),
				feed.validators());

		assertThat(downloader.download(feed.validators())).isInstanceOf(NotModified.class);
	}
}
