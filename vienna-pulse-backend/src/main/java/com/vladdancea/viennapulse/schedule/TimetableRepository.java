package com.vladdancea.viennapulse.schedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads the planned timetable of the active GTFS feed version. */
@Repository
class TimetableRepository {

	private final JdbcClient jdbc;

	TimetableRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The active version and its time zone. All agencies of a feed must share one zone. */
	Optional<ActiveFeed> activeFeed() {
		List<ActiveFeed> feeds = jdbc.sql("""
				SELECT DISTINCT v.id, a.timezone
				FROM gtfs_feed_version v
				JOIN agency a ON a.feed_version_id = v.id
				WHERE v.active
				""").query((rs, row) -> new ActiveFeed(rs.getLong("id"), ZoneId.of(rs.getString("timezone")))).list();
		if (feeds.size() > 1) {
			throw new IllegalStateException("The active GTFS feed uses more than one time zone: " + feeds);
		}
		return feeds.stream().findFirst();
	}

	/**
	 * Trips of {@code serviceDate} that are on the road at some point between {@code fromS} and
	 * {@code toS} (seconds after that day's origin).
	 */
	List<TripRow> tripsRunning(long version, LocalDate serviceDate, int fromS, int toS) {
		return jdbc.sql("""
				SELECT t.trip_key, t.trip_id, r.short_name, t.direction_id, t.headsign, t.shape_id,
				       t.first_departure_s, t.last_arrival_s
				FROM service_date d
				JOIN trip t ON t.feed_version_id = d.feed_version_id AND t.service_id = d.service_id
				JOIN route r ON r.feed_version_id = t.feed_version_id AND r.route_id = t.route_id
				WHERE d.feed_version_id = :version
				  AND d.service_date = :date
				  AND t.first_departure_s <= :to
				  AND t.last_arrival_s >= :from
				""")
			.param("version", version)
			.param("date", serviceDate)
			.param("from", fromS)
			.param("to", toS)
			.query((rs, row) -> new TripRow(rs.getInt("trip_key"), rs.getString("trip_id"), rs.getString("short_name"),
					rs.getInt("direction_id"), rs.getString("headsign"), rs.getString("shape_id"),
					rs.getInt("first_departure_s"), rs.getInt("last_arrival_s")))
			.list();
	}

	record ActiveFeed(long id, ZoneId zone) {
	}

	record TripRow(int tripKey, String tripId, String line, int directionId, String headsign, String shapeId,
			int firstDepartureS, int lastArrivalS) {
	}

}
