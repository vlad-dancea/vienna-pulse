package com.vladdancea.viennapulse.gtfs;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the Wiener Linien GTFS feed comes from and where it is stored while it is imported.
 *
 * @param feedUrl the GTFS zip published by Wiener Linien
 * @param workDir directory for the downloaded zip
 * @param connectTimeout time allowed to open the connection
 * @param readTimeout longest pause allowed between two received chunks
 */
@ConfigurationProperties("pulse.gtfs")
public record GtfsProperties(
		@DefaultValue("https://www.wienerlinien.at/ogd_realtime/doku/ogd/gtfs/gtfs.zip") URI feedUrl,
		@DefaultValue("${java.io.tmpdir}/vienna-pulse/gtfs") Path workDir,
		@DefaultValue("10s") Duration connectTimeout,
		@DefaultValue("60s") Duration readTimeout) {
}
