package com.vladdancea.viennapulse.gtfs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.NotModified;
import com.vladdancea.viennapulse.gtfs.GtfsFeedVersionRepository.FeedVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps the active GTFS feed current: downloads the feed when it changed, imports it and
 * activates the new version. Runs once after startup when no feed is active yet, then on
 * the {@code pulse.gtfs.updater.cron} schedule.
 */
@Service
@ConditionalOnBooleanProperty(name = "pulse.gtfs.updater.enabled", matchIfMissing = true)
public class GtfsFeedUpdater {

	/** Postgres advisory lock key, so only one update runs across all backend instances. */
	static final long LOCK_KEY = 0x6774_6673L; // "gtfs"

	private static final Logger log = LoggerFactory.getLogger(GtfsFeedUpdater.class);

	private final GtfsDownloader downloader;

	private final GtfsImporter importer;

	private final GtfsFeedVersionRepository versions;

	private final TransactionTemplate transaction;

	private final DataSource dataSource;

	private final TaskScheduler scheduler;

	private final GtfsProperties properties;

	GtfsFeedUpdater(GtfsDownloader downloader, GtfsImporter importer, GtfsFeedVersionRepository versions,
			TransactionTemplate transaction, DataSource dataSource, TaskScheduler scheduler,
			GtfsProperties properties) {
		this.downloader = downloader;
		this.importer = importer;
		this.versions = versions;
		this.transaction = transaction;
		this.dataSource = dataSource;
		this.scheduler = scheduler;
		this.properties = properties;
	}

	/** What one update run did. */
	public enum Outcome {

		/** Another run holds the lock. */
		ALREADY_RUNNING,
		/** Startup run, and a feed is already active. */
		ALREADY_HAS_FEED,
		/** The server answered 304. */
		NOT_MODIFIED,
		/** The server sent the active feed again (same checksum). */
		UNCHANGED,
		/** A new feed was imported and is now active. */
		IMPORTED

	}

	/**
	 * Starts the first import in the background once the app is up. Startup and the health
	 * check never wait for it, so a Wiener Linien outage cannot fail a deploy.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void onApplicationReady() {
		if (properties.updater().onStartup()) {
			scheduler.schedule(() -> runLogged(true), Instant.now());
		}
	}

	@Scheduled(cron = "${pulse.gtfs.updater.cron:0 0 7 * * *}", zone = "${pulse.gtfs.updater.zone:Europe/Vienna}")
	void onSchedule() {
		runLogged(false);
	}

	/**
	 * Runs one update.
	 *
	 * @param onlyIfEmpty skip when a feed is already active (used after startup)
	 * @return what the run did
	 * @throws GtfsDownloadException when the download fails, other exceptions when the import fails
	 */
	public Outcome update(boolean onlyIfEmpty) {
		try (Connection lockConnection = dataSource.getConnection()) {
			if (!tryLock(lockConnection)) {
				return Outcome.ALREADY_RUNNING;
			}
			try {
				return updateLocked(onlyIfEmpty);
			}
			finally {
				unlock(lockConnection);
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException("Could not take the GTFS update lock", ex);
		}
	}

	private Outcome updateLocked(boolean onlyIfEmpty) {
		Optional<FeedVersion> active = versions.findActive();
		if (onlyIfEmpty && active.isPresent()) {
			return Outcome.ALREADY_HAS_FEED;
		}
		FeedValidators previous = active.map(FeedVersion::validators).orElse(FeedValidators.NONE);
		return switch (downloader.download(previous)) {
			case NotModified notModified -> Outcome.NOT_MODIFIED;
			case Downloaded feed when active.isPresent() && active.get().sha256().equals(feed.sha256()) -> {
				versions.updateValidators(active.get().id(), feed.validators());
				yield Outcome.UNCHANGED;
			}
			case Downloaded feed -> {
				long id = versions.saveDownloaded(feed);
				transaction.executeWithoutResult(status -> {
					importer.importFeed(id, feed.file());
					versions.activate(id);
				});
				log.info("Imported GTFS feed version {} (sha256 {})", id, feed.sha256());
				yield Outcome.IMPORTED;
			}
		};
	}

	private void runLogged(boolean onlyIfEmpty) {
		try {
			Outcome outcome = update(onlyIfEmpty);
			log.info("GTFS update finished: {}", outcome);
		}
		catch (RuntimeException ex) {
			// The active feed stays in place. The next scheduled run retries.
			log.error("GTFS update failed", ex);
		}
	}

	private static boolean tryLock(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
			statement.setLong(1, LOCK_KEY);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getBoolean(1);
			}
		}
	}

	private static void unlock(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
			statement.setLong(1, LOCK_KEY);
			statement.execute();
		}
	}

}
