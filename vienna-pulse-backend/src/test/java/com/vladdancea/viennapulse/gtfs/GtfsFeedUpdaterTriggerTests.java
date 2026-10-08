package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;

import com.vladdancea.viennapulse.TestcontainersConfiguration;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Checks what starts an update: the startup run and the daily cron. */
@SpringBootTest(properties = { "pulse.gtfs.updater.on-startup=true", "pulse.gtfs.updater.cron=0 0 7 * * *" })
@Import(TestcontainersConfiguration.class)
class GtfsFeedUpdaterTriggerTests {

	@MockitoBean
	private GtfsDownloader downloader;

	@MockitoBean
	private GtfsImporter importer;

	@Autowired
	private ScheduledTaskHolder scheduledTasks;

	@Test
	void importsInTheBackgroundAfterStartupAndRegistersTheDailyCron() {
		given(downloader.download(FeedValidators.NONE))
			.willReturn(new Downloaded(Path.of("gtfs.zip"), "c".repeat(64), 1, FeedValidators.NONE));

		verify(importer, timeout(10_000)).importFeed(anyLong(), eq(Path.of("gtfs.zip")));

		assertThat(scheduledTasks.getScheduledTasks()).map(task -> task.getTask())
			.filteredOn(CronTask.class::isInstance)
			.map(task -> ((CronTask) task).getExpression())
			.containsExactly("0 0 7 * * *");
	}

}
