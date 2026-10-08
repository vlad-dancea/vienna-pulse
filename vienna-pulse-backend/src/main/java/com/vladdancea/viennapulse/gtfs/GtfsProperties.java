package com.vladdancea.viennapulse.gtfs;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the Wiener Linien GTFS feed comes from, where it is stored while it is imported,
 * and when the backend checks for a new one.
 *
 * @param feedUrl the GTFS zip published by Wiener Linien
 * @param workDir directory for the downloaded zip
 * @param connectTimeout time allowed to open the connection
 * @param readTimeout longest pause allowed between two received chunks
 * @param routeTypes GTFS route types to import, 1 = metro (U-Bahn only for now)
 * @param updater schedule of the feed update
 */
@ConfigurationProperties("pulse.gtfs")
public record GtfsProperties(
		@DefaultValue("https://www.wienerlinien.at/ogd_realtime/doku/ogd/gtfs/gtfs.zip") URI feedUrl,
		@DefaultValue("${java.io.tmpdir}/vienna-pulse/gtfs") Path workDir,
		@DefaultValue("10s") Duration connectTimeout,
		@DefaultValue("60s") Duration readTimeout,
		@DefaultValue("1") Set<Integer> routeTypes,
		@DefaultValue Updater updater) {

	/**
	 * @param enabled whether the updater runs at all
	 * @param cron when to check for a new feed (Spring cron with seconds, {@code -} disables it)
	 * @param zone time zone of the cron expression
	 * @param onStartup whether to import right after startup when no feed is active yet
	 */
	public record Updater(
			@DefaultValue("true") boolean enabled,
			@DefaultValue("0 0 7 * * *") String cron,
			@DefaultValue("Europe/Vienna") String zone,
			@DefaultValue("true") boolean onStartup) {
	}

}
