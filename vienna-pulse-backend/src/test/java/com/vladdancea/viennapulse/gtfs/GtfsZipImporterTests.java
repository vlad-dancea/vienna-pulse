package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
		Path zip = zip(feed());

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
		Path zip = zip(feed());

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
		Path zip = zip(feed());

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
		Path zip = zip(feed());

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
		Map<String, String> files = feed();
		files.put("stop_times.txt", files.get("stop_times.txt").replace("at:49:3:0:1", "at:49:404:0:1"));
		Path zip = zip(files);

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

	/** A small feed in the same style as the Wiener Linien one (quoted fields, CRLF, BOM). */
	private static Map<String, String> feed() {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("agency.txt", """
				agency_id,agency_name,agency_url,agency_timezone
				"04","Wiener Linien","https://www.wienerlinien.at","Europe/Vienna"
				"03","Wiener Lokalbahnen","https://www.wlb.at","Europe/Vienna"
				""");
		files.put("routes.txt", """
				route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color
				"21-U1","04","U1","Leopoldau - Oberlaa","1","E3000F","FFFFFF"
				"22-13A","04","13A","","3","",""
				""");
		files.put("trips.txt", """
				route_id,service_id,trip_id,shape_id,trip_headsign,direction_id,block_id
				"21-U1","WD","t1","s1","Oberlaa","0",""
				"21-U1","WD","t2","s2","Leopoldau","1",""
				"21-U1","XMAS","t4","s1","Oberlaa","0",""
				"22-13A","WD","t3","s3","Alser Str.","0",""
				""");
		files.put("stops.txt", """
				stop_id,stop_name,stop_lat,stop_lon,zone_id
				"at:49:1:0:1","Alpha","48.1","16.1","0100"
				"at:49:2:0:1","Beta","48.2","16.2","0100"
				"at:49:3:0:1","Gamma","48.3","16.3","0100"
				"at:49:9:0:1","Bus only","48.4","16.4","0100"
				"at:49:8:0:1","Unused","48.5","16.5","0100"
				""");
		files.put("stop_times.txt", """
				trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type,shape_dist_traveled
				"t1","04:57:00","04:57:00","at:49:1:0:1","1","0","0","0.00"
				"t1","05:00:00","05:00:30","at:49:2:0:1","2","0","0","600.50"
				"t1","05:03:00","05:03:00","at:49:3:0:1","3","0","0","1200.25"
				"t3","06:00:00","06:00:00","at:49:9:0:1","1","0","0","0.00"
				"t3","06:05:00","06:05:00","at:49:1:0:1","2","0","0","900.00"
				"t2","24:58:00","24:58:00","at:49:3:0:1","1","0","0","0.00"
				"t2","25:10:00","25:10:00","at:49:1:0:1","2","0","0","1200.25"
				"t4","10:00:00","10:00:00","at:49:1:0:1","1","0","0","0.00"
				"t4","10:03:00","10:03:00","at:49:2:0:1","2","","","600.50"
				""");
		files.put("shapes.txt", """
				shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence,shape_dist_traveled
				"s1","48.3","16.3","3","1200.25"
				"s1","48.1","16.1","1","0.00"
				"s1","48.2","16.2","2","600.50"
				"s2","48.3","16.3","1","0.00"
				"s2","48.1","16.1","2","1200.25"
				"s3","48.4","16.4","1","0.00"
				"s3","48.1","16.1","2","900.00"
				""");
		files.put("calendar.txt", """
				service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
				"WD","1","1","1","1","1","0","0","20261005","20261016"
				""");
		files.put("calendar_dates.txt", """
				service_id,date,exception_type
				"WD","20261012","2"
				"WD","20261017","1"
				"XMAS","20261224","1"
				""");
		return files;
	}

	private Path zip(Map<String, String> files) throws IOException {
		Path zip = dir.resolve("gtfs.zip");
		try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream zipOut = new ZipOutputStream(out)) {
			for (Map.Entry<String, String> file : files.entrySet()) {
				zipOut.putNextEntry(new ZipEntry(file.getKey()));
				String content = "\uFEFF" + file.getValue().replace("\n", "\r\n");
				zipOut.write(content.getBytes(StandardCharsets.UTF_8));
				zipOut.closeEntry();
			}
		}
		return zip;
	}

}
