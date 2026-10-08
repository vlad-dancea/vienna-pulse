package com.vladdancea.viennapulse.trains;

import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StopTimeRepository {

	private final JdbcClient jdbc;

	StopTimeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Stop times of the given trips, ordered by trip and sequence. */
	List<StopTimeRow> stopTimes(long version, Collection<Integer> tripKeys) {
		if (tripKeys.isEmpty()) {
			return List.of();
		}
		return jdbc.sql("""
				SELECT st.trip_key, s.stop_id, s.name, st.arrival_s, st.departure_s, st.dist_m
				FROM stop_time st
				JOIN stop s ON s.feed_version_id = st.feed_version_id AND s.stop_key = st.stop_key
				WHERE st.feed_version_id = :version AND st.trip_key = ANY(:trips)
				ORDER BY st.trip_key, st.stop_sequence
				""")
			.param("version", version)
			.param("trips", tripKeys.stream().mapToInt(Integer::intValue).toArray())
			.query((rs, row) -> new StopTimeRow(rs.getInt("trip_key"), rs.getString("stop_id"), rs.getString("name"),
					rs.getInt("arrival_s"), rs.getInt("departure_s"), rs.getDouble("dist_m")))
			.list();
	}

	record StopTimeRow(int tripKey, String stopId, String name, int arrivalS, int departureS, double distM) {
	}

}
