package com.vladdancea.viennapulse.gtfs;

import java.nio.file.Path;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** Outcome of one feed download attempt. */
public sealed interface GtfsDownloadResult {

	/**
	 * A new feed was downloaded and checked.
	 *
	 * @param file the validated zip
	 * @param sha256 hex SHA-256 of the zip, identifies the feed content
	 * @param sizeBytes size of the zip
	 * @param validators HTTP validators to send with the next request
	 */
	record Downloaded(Path file, String sha256, long sizeBytes, FeedValidators validators) implements GtfsDownloadResult {
	}

	/** The server confirmed that the feed has not changed since the given validators. */
	record NotModified() implements GtfsDownloadResult {
	}

	/**
	 * HTTP validators for a conditional request. Either value may be missing.
	 *
	 * @param lastModified the {@code Last-Modified} response header
	 * @param etag the {@code ETag} response header
	 */
	record FeedValidators(@Nullable Instant lastModified, @Nullable String etag) {

		public static final FeedValidators NONE = new FeedValidators(null, null);

	}

}
