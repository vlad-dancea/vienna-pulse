package com.vladdancea.viennapulse.gtfs;

import java.nio.file.Path;

/**
 * Loads a downloaded GTFS zip into the database. Called by {@link GtfsFeedUpdater} inside
 * the transaction that also activates the feed version, so a failed import leaves the
 * previous version active and no partial data behind.
 */
@FunctionalInterface
public interface GtfsImporter {

	/**
	 * @param feedVersionId the {@code gtfs_feed_version} row the imported data belongs to
	 * @param zip the validated GTFS zip
	 */
	void importFeed(long feedVersionId, Path zip);

}
