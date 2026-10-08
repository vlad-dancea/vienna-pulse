package com.vladdancea.viennapulse.gtfs;

/** The feed could not be downloaded, or the downloaded file is not a usable GTFS feed. */
public class GtfsDownloadException extends RuntimeException {

	GtfsDownloadException(String message) {
		super(message);
	}

	GtfsDownloadException(String message, Throwable cause) {
		super(message, cause);
	}

}
