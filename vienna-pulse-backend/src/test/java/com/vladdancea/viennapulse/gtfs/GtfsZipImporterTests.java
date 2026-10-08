package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.vladdancea.viennapulse.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports a small hand-made feed: one U-Bahn line (U1) and one bus (13A, filtered out),
 * a trip after midnight, an exception-only service and a shape stored out of order.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GtfsZipImporterTests {

	@TempDir
	Path dir;

	@Autowired
	private GtfsZipImporter importer;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate transaction;

	private long version;

	@BeforeEach
	void newFeedVersion() {
		jdbc.sql("DELETE FROM stop_time").update();
		jdbc.sql("DELETE FROM gtfs_feed_version").update();
		version = jdbc.sql("INSERT INTO gtfs_feed_version (sha256, size_bytes) VALUES (repeat('a', 64), 1) RETURNING id")
			.query(Long.class)
			.single();
	}

	@Test
	void importsOnlyTheUBahnAndEverythingItUses() throws IOException {
		Path zip = GtfsTestFeed.zip(dir, GtfsTestFeed.files());

		transaction.executeWithoutResult(status -> importer.importFeed(version, zip));

		assertThat(count("agency")).isEqualTo(2);
		assertThat(count("route")).isEqualTo(1);
		assertThat(count("trip")).isEqualTo(3);
		assertThat(count("stop")).isEqualTo(3);
		assertThat(count("stop_time")).isEqualTo(7);
		assertThat(count("shape")).isEqualTo(2);
		assertThat(count("service")).isEqualTo(2);
		assertThat(count("service_calendar")).isEqualTo(1);
		assertThat(count("service_date")).isEqualTo(11);
	}

	@Test
	void storesTripsAfterMidnightAsSecondsAbove24Hours() throws IOException {
		Path zip = GtfsTestFeed.zip(dir, GtfsTestFeed.files());

		transaction.executeWithoutResult(status -> importer.importFeed(version, zip));

		var bounds = jdbc.sql("SELECT first_departure_s, last_arrival_s FROM trip WHERE trip_id = 't2'")
			.query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2)))
			.single();
		assertThat(bounds).containsExactly(24 * 3600 + 58 * 60, 25 * 3600 + 10 * 60);
		var dwell = jdbc.sql("""
				SELECT arrival_s, departure_s FROM stop_time st
				JOIN trip t USING (feed_version_id, trip_key)
				JOIN stop s USING (feed_version_id, stop_key)
				WHERE t.trip_id = 't1' AND s.stop_id = 'at:49:2:0:1'
				""").query((rs, row) -> List.of(rs.getInt(1), rs.getInt(2))).single();
		assertThat(dwell).containsExactly(5 * 3600, 5 * 3600 + 30);
	}

	@Test
	void expandsTheCalendar() throws IOException {
		Path zip = GtfsTestFeed.zip(dir, GtfsTestFeed.files());

		transaction.executeWithoutResult(status -> importer.importFeed(version, zip));

		List<LocalDate> weekdays = jdbc
			.sql("SELECT service_date FROM service_date WHERE service_id = 'WD' ORDER BY service_date")
			.query(LocalDate.class)
			.list();
		assertThat(weekdays).hasSize(10)
			.contains(LocalDate.of(2026, 10, 17))
			.doesNotContain(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 11));
		assertThat(jdbc.sql("SELECT service_date FROM service_date WHERE service_id = 'XMAS'")
			.query(LocalDate.class)
			.list()).containsExactly(LocalDate.of(2026, 12, 24));
		assertThat(jdbc.sql("SELECT weekdays::text FROM service_calendar").query(String.class).single())
			.isEqualTo("1111100");
	}

	@Test
	void sortsShapePointsAndKeepsStationRefs() throws IOException {
		Path zip = GtfsTestFeed.zip(dir, GtfsTestFeed.files());

		transaction.executeWithoutResult(status -> importer.importFeed(version, zip));

		var shape = jdbc.sql("SELECT lats, dists_m, length_m FROM shape WHERE shape_id = 's1'")
			.query((rs, row) -> List.of(rs.getArray(1).getArray(), rs.getArray(2).getArray(), rs.getDouble(3)))
			.single();
		assertThat((Double[]) shape.get(0)).containsExactly(48.1, 48.2, 48.3);
		assertThat((Double[]) shape.get(1)).containsExactly(0.0, 600.5, 1200.25);
		assertThat(shape.get(2)).isEqualTo(1200.25);
		assertThat(jdbc.sql("SELECT station_ref FROM stop WHERE stop_id = 'at:49:2:0:1'").query(String.class).single())
			.isEqualTo("at:49:2");
	}

	@Test
	void rollsBackEverythingWhenAStopIsUnknown() throws IOException {
		Map<String, String> files = GtfsTestFeed.files();
		files.put("stop_times.txt", files.get("stop_times.txt").replace("at:49:3:0:1", "at:49:404:0:1"));
		Path zip = GtfsTestFeed.zip(dir, files);

		assertThatExceptionOfType(GtfsImportException.class)
			.isThrownBy(() -> transaction.executeWithoutResult(status -> importer.importFeed(version, zip)))
			.withMessageContaining("unknown stop at:49:404:0:1");

		assertThat(count("route") + count("stop_time") + count("agency")).isZero();
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table + " WHERE feed_version_id = :v")
			.param("v", version)
			.query(Long.class)
			.single();
	}

}
