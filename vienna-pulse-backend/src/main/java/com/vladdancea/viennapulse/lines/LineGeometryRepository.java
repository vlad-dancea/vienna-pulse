package com.vladdancea.viennapulse.lines;

import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class LineGeometryRepository {

	private final JdbcClient jdbc;

	LineGeometryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Picks one shape per line and direction among the trips running on {@code serviceDate}:
	 * the one that serves the most stops, then the most used. "Longest shape" is not enough
	 * because old or diversion variants can be longer and still miss stations served today.
	 */
	List<LineRow> linesOn(long version, LocalDate serviceDate) {
		return jdbc.sql("""
				WITH used AS (
				    SELECT r.short_name, t.direction_id, t.shape_id,
				           count(*) AS trips, min(t.trip_key) AS sample_trip, min(r.color) AS color
				    FROM service_date d
				    JOIN trip t ON t.feed_version_id = d.feed_version_id AND t.service_id = d.service_id
				    JOIN route r ON r.feed_version_id = t.feed_version_id AND r.route_id = t.route_id
				    WHERE d.feed_version_id = :version AND d.service_date = :date
				    GROUP BY r.short_name, t.direction_id, t.shape_id
				), scored AS (
				    SELECT used.*,
				           (SELECT count(*) FROM stop_time st
				            WHERE st.feed_version_id = :version AND st.trip_key = used.sample_trip) AS stops
				    FROM used
				), picked AS (
				    SELECT DISTINCT ON (short_name, direction_id) *
				    FROM scored
				    ORDER BY short_name, direction_id, stops DESC, trips DESC, shape_id
				)
				SELECT p.short_name, p.direction_id, p.color, p.shape_id, s.lats, s.lons, s.length_m
				FROM picked p
				JOIN shape s ON s.feed_version_id = :version AND s.shape_id = p.shape_id
				ORDER BY p.short_name, p.direction_id
				""")
			.param("version", version)
			.param("date", serviceDate)
			.query((rs, row) -> new LineRow(rs.getString("short_name"), rs.getInt("direction_id"), rs.getString("color"),
					rs.getString("shape_id"), doubles(rs.getArray("lats")), doubles(rs.getArray("lons")),
					rs.getDouble("length_m")))
			.list();
	}

	private static double[] doubles(Array array) throws SQLException {
		Double[] boxed = (Double[]) array.getArray();
		double[] values = new double[boxed.length];
		for (int i = 0; i < boxed.length; i++) {
			values[i] = boxed[i];
		}
		return values;
	}

	record LineRow(String line, int directionId, String color, String shapeId, double[] lats, double[] lons,
			double lengthM) {
	}

}
