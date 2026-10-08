package com.vladdancea.viennapulse.schedule;

import java.time.Instant;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;

/**
 * A planned trip that runs inside a requested time window. The same GTFS trip runs on many
 * service days, so {@code serviceDate} is part of its identity.
 *
 * @param serviceDate the service day the trip belongs to (can be yesterday after midnight)
 * @param tripKey internal key of the trip within the feed version
 * @param tripId the GTFS trip_id
 * @param line route short name, e.g. U2
 * @param directionId GTFS direction, 0 or 1
 * @param headsign destination shown on the train
 * @param shapeId the geometry the trip runs along
 * @param departsAt departure from the first stop
 * @param arrivesAt arrival at the last stop
 */
public record ActiveTrip(LocalDate serviceDate, int tripKey, String tripId, String line, int directionId,
		@Nullable String headsign, String shapeId, Instant departsAt, Instant arrivesAt) {
}
