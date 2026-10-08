package com.vladdancea.viennapulse.gtfs;

/** Parses GTFS times. They can pass 24:00 for trips that run after midnight. */
public final class GtfsTime {

	/**
	 * Latest time of day the import accepts (48:00:00). Wiener Linien goes up to about 29:00.
	 * The bound lets the timetable look back a fixed number of service days.
	 */
	public static final int MAX_SECONDS = 48 * 3600;

	private GtfsTime() {
	}

	/**
	 * @param value {@code H:MM:SS} or {@code HH:MM:SS}, hours may exceed 23
	 * @return seconds after the service day origin
	 */
	static int toSeconds(String value) {
		int first = value.indexOf(':');
		int second = value.indexOf(':', first + 1);
		if (first < 1 || second != first + 3 || value.length() != second + 3) {
			throw new IllegalArgumentException("Not a GTFS time: '" + value + "'");
		}
		int hours = Integer.parseInt(value, 0, first, 10);
		int minutes = Integer.parseInt(value, first + 1, second, 10);
		int seconds = Integer.parseInt(value, second + 1, value.length(), 10);
		if (hours < 0 || minutes > 59 || seconds > 59 || minutes < 0 || seconds < 0) {
			throw new IllegalArgumentException("Not a GTFS time: '" + value + "'");
		}
		return hours * 3600 + minutes * 60 + seconds;
	}

}
