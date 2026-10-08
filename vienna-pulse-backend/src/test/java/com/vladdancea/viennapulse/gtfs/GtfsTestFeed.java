package com.vladdancea.viennapulse.gtfs;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A small hand-made GTFS feed for tests: U1 with trips t1 (weekdays 04:57 to 05:03), t2
 * (weekdays 24:58 to 25:10, after midnight) and t4 (24 December only), plus bus 13A (t3),
 * which the U-Bahn filter drops. Weekdays run 5 to 16 October 2026 without the 12th, plus
 * Saturday the 17th.
 */
public final class GtfsTestFeed {

	private GtfsTestFeed() {
	}

	/** The feed files in the same style as the Wiener Linien one (quoted fields, CRLF, BOM). */
	public static Map<String, String> files() {
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

	/**
	 * Empties the GTFS tables, imports this feed and makes it the active version.
	 *
	 * @return the feed version id
	 */
	public static long importActive(Path dir, GtfsImporter importer, JdbcClient jdbc, TransactionTemplate transaction)
			throws IOException {
		jdbc.sql("TRUNCATE gtfs_feed_version, stop_time CASCADE").update();
		long version = jdbc
			.sql("INSERT INTO gtfs_feed_version (sha256, size_bytes) VALUES (repeat('t', 64), 1) RETURNING id")
			.query(Long.class)
			.single();
		Path zip = zip(dir, files());
		transaction.executeWithoutResult(status -> {
			importer.importFeed(version, zip);
			jdbc.sql("UPDATE gtfs_feed_version SET active = true, imported_at = now() WHERE id = :id")
				.param("id", version)
				.update();
		});
		return version;
	}

	public static Path zip(Path dir, Map<String, String> files) throws IOException {
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
