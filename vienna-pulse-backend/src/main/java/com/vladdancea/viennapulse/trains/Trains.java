package com.vladdancea.viennapulse.trains;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Planned trains on the road in a time window, with the stop times the browser needs to
 * move them. All times are Unix epoch seconds, so the client computes positions with plain
 * arithmetic: {@code progress = (now - departure) / (nextArrival - departure)}.
 *
 * @param serverTime when the response was built, lets the client correct its clock
 * @param from start of the window
 * @param to end of the window
 * @param feedVersion the timetable version the trains come from
 */
public record Trains(long serverTime, long from, long to, long feedVersion, List<Train> trains) {

	/**
	 * @param id stable id of this run, {@code <service date>/<trip_id>}
	 * @param shapeId geometry to move along, see {@code GET /api/shapes/{shapeId}}
	 * @param stops the stops around the window: from the last stop departed before
	 * {@code from} to the first stop reached after {@code to}
	 */
	public record Train(String id, LocalDate serviceDate, String line, int directionId, @Nullable String headsign,
			String shapeId, List<Stop> stops) {
	}

	/**
	 * @param distM distance along the shape in meters
	 */
	public record Stop(String stopId, String name, long arrival, long departure, double distM) {
	}

}
