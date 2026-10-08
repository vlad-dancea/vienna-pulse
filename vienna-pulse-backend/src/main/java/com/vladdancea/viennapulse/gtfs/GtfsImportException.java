package com.vladdancea.viennapulse.gtfs;

/** The feed breaks a rule the import relies on. Nothing of it is kept. */
public class GtfsImportException extends RuntimeException {

	GtfsImportException(String message) {
		super(message);
	}

	GtfsImportException(String message, Throwable cause) {
		super(message, cause);
	}

}
