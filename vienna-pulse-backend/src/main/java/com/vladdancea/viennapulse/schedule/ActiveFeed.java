package com.vladdancea.viennapulse.schedule;

import java.time.ZoneId;

/**
 * The GTFS feed version that currently serves the map.
 *
 * @param id the {@code gtfs_feed_version} id
 * @param zone the time zone of the feed's agencies, used for service days
 */
public record ActiveFeed(long id, ZoneId zone) {
}
