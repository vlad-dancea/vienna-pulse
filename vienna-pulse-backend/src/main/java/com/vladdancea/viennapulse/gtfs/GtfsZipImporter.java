package com.vladdancea.viennapulse.gtfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.sql.DataSource;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/**
 * Imports a GTFS zip into the V2 tables with COPY. Keeps only routes whose type is in
 * {@code pulse.gtfs.route-types}, and only the trips, stops, shapes and services those
 * routes use. Runs on the connection of the caller's transaction, so a failure leaves
 * nothing behind.
 *
 * <p>
 * Reading order: agencies, routes and trips first, all stops into memory, then
 * {@code stop_times.txt} is streamed once (it is the big file), then shapes and calendars.
 * {@code stop_time} has no foreign keys, so its rows are copied while streaming. The other
 * tables are written parents first.
 */
@Component
class GtfsZipImporter implements GtfsImporter {

	private static final Logger log = LoggerFactory.getLogger(GtfsZipImporter.class);

	private static final List<String> ANALYZED_TABLES = List.of("agency", "route", "stop", "shape", "service",
			"service_calendar", "service_date", "trip", "stop_time");

	private final DataSource dataSource;

	private final Set<Integer> routeTypes;

	GtfsZipImporter(DataSource dataSource, GtfsProperties properties) {
		this.dataSource = dataSource;
		this.routeTypes = Set.copyOf(properties.routeTypes());
	}

	@Override
	public void importFeed(long feedVersionId, Path zip) {
		Connection connection = DataSourceUtils.getConnection(dataSource);
		long start = System.nanoTime();
		try (ZipFile file = new ZipFile(zip.toFile())) {
			Import run = new Import(feedVersionId, file, connection);
			run.execute();
			try (Statement statement = connection.createStatement()) {
				statement.execute("ANALYZE " + String.join(", ", ANALYZED_TABLES));
			}
			log.info("Imported GTFS version {} in {} ms: {}", feedVersionId, (System.nanoTime() - start) / 1_000_000,
					run.counts);
		}
		catch (IOException | SQLException ex) {
			throw new GtfsImportException("GTFS import failed: " + ex.getMessage(), ex);
		}
		finally {
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	/** State of one import. */
	private final class Import {

		private final long version;

		private final ZipFile zip;

		private final Connection connection;

		private final Map<String, Long> counts = new LinkedHashMap<>();

		private final Set<String> agencyIds = new HashSet<>();

		private final Set<String> routeIds = new HashSet<>();

		private final Map<String, Trip> trips = new LinkedHashMap<>();

		private final Map<String, Stop> stops = new HashMap<>();

		Import(long version, ZipFile zip, Connection connection) {
			this.version = version;
			this.zip = zip;
			this.connection = connection;
		}

		void execute() throws IOException, SQLException {
			copyAgencies();
			copyRoutes();
			readTrips();
			readStops();
			copyStopTimes();
			copyShapes();
			copyServices();
			copyTrips();
			copyUsedStops();
		}

		private void copyAgencies() throws IOException, SQLException {
			try (PgCopy copy = PgCopy.into(connection, "agency", "feed_version_id", "agency_id", "name", "timezone")) {
				each("agency.txt", row -> {
					String id = row.getField("agency_id");
					agencyIds.add(id);
					copyRow(copy, version, id, required(row, "agency_name"), required(row, "agency_timezone"));
				});
				count("agency", copy.finish());
			}
		}

		private void copyRoutes() throws IOException, SQLException {
			String onlyAgency = agencyIds.size() == 1 ? agencyIds.iterator().next() : null;
			try (PgCopy copy = PgCopy.into(connection, "route", "feed_version_id", "route_id", "agency_id",
					"short_name", "long_name", "route_type", "color", "text_color")) {
				each("routes.txt", row -> {
					int type = Integer.parseInt(required(row, "route_type"));
					if (!routeTypes.contains(type)) {
						return;
					}
					String id = required(row, "route_id");
					String agency = blankToNull(row.findField("agency_id").orElse(""));
					if (agency == null) {
						agency = onlyAgency;
					}
					if (agency == null || !agencyIds.contains(agency)) {
						throw new GtfsImportException("Route " + id + " has an unknown agency '" + agency + "'");
					}
					routeIds.add(id);
					copyRow(copy, version, id, agency, required(row, "route_short_name"),
							blankToNull(row.findField("route_long_name").orElse("")), type,
							blankToNull(row.findField("route_color").orElse("")),
							blankToNull(row.findField("route_text_color").orElse("")));
				});
				count("route", copy.finish());
			}
		}

		private void readTrips() throws IOException {
			each("trips.txt", row -> {
				if (!routeIds.contains(row.getField("route_id"))) {
					return;
				}
				String id = required(row, "trip_id");
				Trip trip = new Trip(trips.size() + 1, id, row.getField("route_id"), required(row, "service_id"),
						required(row, "shape_id"), Short.parseShort(required(row, "direction_id")),
						blankToNull(row.findField("trip_headsign").orElse("")));
				if (trips.put(id, trip) != null) {
					throw new GtfsImportException("Duplicate trip_id " + id);
				}
			});
		}

		private void readStops() throws IOException {
			each("stops.txt", row -> {
				String id = required(row, "stop_id");
				Stop stop = new Stop(stops.size() + 1, id, stationRef(id), required(row, "stop_name"),
						Double.parseDouble(required(row, "stop_lat")), Double.parseDouble(required(row, "stop_lon")));
				if (stops.put(id, stop) != null) {
					throw new GtfsImportException("Duplicate stop_id " + id);
				}
			});
		}

		/** Streams stop_times.txt. It must be grouped by trip and ordered by stop_sequence. */
		private void copyStopTimes() throws IOException, SQLException {
			Set<String> finishedTrips = new HashSet<>();
			try (PgCopy copy = PgCopy.into(connection, "stop_time", "feed_version_id", "trip_key", "stop_sequence",
					"stop_key", "arrival_s", "departure_s", "dist_m", "pickup_type", "drop_off_type")) {
				Trip[] current = { null };
				each("stop_times.txt", row -> {
					Trip trip = trips.get(row.getField("trip_id"));
					if (trip == null) {
						return;
					}
					if (trip != current[0]) {
						if (current[0] != null) {
							finishedTrips.add(current[0].id);
						}
						if (finishedTrips.contains(trip.id)) {
							throw new GtfsImportException("stop_times.txt is not grouped by trip at trip " + trip.id);
						}
						current[0] = trip;
					}
					Stop stop = stops.get(required(row, "stop_id"));
					if (stop == null) {
						throw new GtfsImportException("Trip " + trip.id + " uses unknown stop " + row.getField("stop_id"));
					}
					stop.used = true;
					int sequence = Integer.parseInt(required(row, "stop_sequence"));
					int arrival = GtfsTime.toSeconds(required(row, "arrival_time"));
					int departure = GtfsTime.toSeconds(required(row, "departure_time"));
					double dist = Double.parseDouble(required(row, "shape_dist_traveled"));
					trip.add(sequence, arrival, departure, dist);
					copyRow(copy, version, trip.key, sequence, stop.key, arrival, departure, dist,
							flag(row, "pickup_type"), flag(row, "drop_off_type"));
				});
				count("stop_time", copy.finish());
			}
			for (Trip trip : trips.values()) {
				if (trip.stopCount < 2) {
					throw new GtfsImportException("Trip " + trip.id + " has " + trip.stopCount + " stop times");
				}
			}
		}

		private void copyShapes() throws IOException, SQLException {
			Map<String, List<double[]>> points = new HashMap<>();
			for (Trip trip : trips.values()) {
				points.putIfAbsent(trip.shapeId, new ArrayList<>());
			}
			each("shapes.txt", row -> {
				List<double[]> shape = points.get(row.getField("shape_id"));
				if (shape != null) {
					shape.add(new double[] { Integer.parseInt(required(row, "shape_pt_sequence")),
							Double.parseDouble(required(row, "shape_pt_lat")),
							Double.parseDouble(required(row, "shape_pt_lon")),
							Double.parseDouble(required(row, "shape_dist_traveled")) });
				}
			});
			try (PgCopy copy = PgCopy.into(connection, "shape", "feed_version_id", "shape_id", "lats", "lons",
					"dists_m", "length_m")) {
				for (Map.Entry<String, List<double[]>> shape : points.entrySet()) {
					List<double[]> sorted = shape.getValue();
					if (sorted.size() < 2) {
						throw new GtfsImportException("Shape " + shape.getKey() + " has " + sorted.size() + " points");
					}
					sorted.sort((a, b) -> Double.compare(a[0], b[0]));
					double[] lats = new double[sorted.size()];
					double[] lons = new double[sorted.size()];
					double[] dists = new double[sorted.size()];
					for (int i = 0; i < sorted.size(); i++) {
						double[] point = sorted.get(i);
						if (i > 0 && point[0] == sorted.get(i - 1)[0]) {
							throw new GtfsImportException("Shape " + shape.getKey() + " repeats sequence " + point[0]);
						}
						if (i > 0 && point[3] < dists[i - 1]) {
							throw new GtfsImportException("Shape " + shape.getKey() + " distance goes backwards");
						}
						lats[i] = point[1];
						lons[i] = point[2];
						dists[i] = point[3];
					}
					copy.row(version, shape.getKey(), lats, lons, dists, dists[dists.length - 1]);
				}
				count("shape", copy.finish());
			}
		}

		private void copyServices() throws IOException, SQLException {
			Set<String> used = new TreeSet<>();
			trips.values().forEach(trip -> used.add(trip.serviceId));
			List<ServiceCalendar.Rule> rules = new ArrayList<>();
			List<ServiceCalendar.Exception> exceptions = new ArrayList<>();
			String[] days = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" };
			each("calendar.txt", row -> {
				String id = row.getField("service_id");
				if (used.contains(id)) {
					boolean[] weekdays = new boolean[7];
					for (int i = 0; i < 7; i++) {
						weekdays[i] = "1".equals(required(row, days[i]));
					}
					rules.add(new ServiceCalendar.Rule(id, weekdays, date(required(row, "start_date")),
							date(required(row, "end_date"))));
				}
			});
			each("calendar_dates.txt", row -> {
				String id = row.getField("service_id");
				if (used.contains(id)) {
					String type = required(row, "exception_type");
					if (!type.equals("1") && !type.equals("2")) {
						throw new GtfsImportException("Unknown exception_type " + type + " for service " + id);
					}
					exceptions.add(new ServiceCalendar.Exception(id, date(required(row, "date")), type.equals("1")));
				}
			});
			Map<String, NavigableSet<LocalDate>> dates = ServiceCalendar.expand(rules, exceptions);
			for (String id : used) {
				if (!dates.containsKey(id)) {
					throw new GtfsImportException("Service " + id + " has no calendar and no calendar dates");
				}
			}
			try (PgCopy copy = PgCopy.into(connection, "service", "feed_version_id", "service_id")) {
				for (String id : used) {
					copy.row(version, id);
				}
				count("service", copy.finish());
			}
			try (PgCopy copy = PgCopy.into(connection, "service_calendar", "feed_version_id", "service_id", "weekdays",
					"start_date", "end_date")) {
				for (ServiceCalendar.Rule rule : rules) {
					copy.row(version, rule.serviceId(), rule.weekdayBits(), rule.start(), rule.end());
				}
				count("service_calendar", copy.finish());
			}
			try (PgCopy copy = PgCopy.into(connection, "service_date", "feed_version_id", "service_id",
					"service_date")) {
				for (String id : used) {
					for (LocalDate day : dates.get(id)) {
						copy.row(version, id, day);
					}
				}
				count("service_date", copy.finish());
			}
		}

		private void copyTrips() throws SQLException {
			try (PgCopy copy = PgCopy.into(connection, "trip", "feed_version_id", "trip_key", "trip_id", "route_id",
					"service_id", "shape_id", "direction_id", "headsign", "first_departure_s", "last_arrival_s")) {
				for (Trip trip : trips.values()) {
					copy.row(version, trip.key, trip.id, trip.routeId, trip.serviceId, trip.shapeId, trip.directionId,
							trip.headsign, trip.firstDeparture, trip.lastArrival);
				}
				count("trip", copy.finish());
			}
		}

		private void copyUsedStops() throws SQLException {
			try (PgCopy copy = PgCopy.into(connection, "stop", "feed_version_id", "stop_key", "stop_id", "station_ref",
					"name", "lat", "lon")) {
				for (Stop stop : stops.values()) {
					if (stop.used) {
						copy.row(version, stop.key, stop.id, stop.stationRef, stop.name, stop.lat, stop.lon);
					}
				}
				count("stop", copy.finish());
			}
		}

		/** Reads one file of the zip row by row. */
		private void each(String name, RowHandler handler) throws IOException {
			ZipEntry entry = zip.getEntry(name);
			if (entry == null) {
				throw new GtfsImportException("GTFS feed is missing " + name);
			}
			long line = 1;
			try (Reader reader = withoutBom(zip, entry);
					CsvReader<NamedCsvRecord> csv = CsvReader.builder().ofNamedCsvRecord(reader)) {
				for (NamedCsvRecord row : csv) {
					line++;
					try {
						handler.handle(row);
					}
					catch (GtfsImportException ex) {
						throw new GtfsImportException(name + " line " + line + ": " + ex.getMessage(), ex);
					}
					catch (RuntimeException | SQLException ex) {
						throw new GtfsImportException(name + " line " + line + ": " + ex, ex);
					}
				}
			}
		}

		private void count(String table, long rows) {
			counts.put(table, rows);
		}

	}

	@FunctionalInterface
	private interface RowHandler {

		void handle(NamedCsvRecord row) throws SQLException;

	}

	/** A trip of the imported routes. Tracks its stop times while they stream by. */
	private static final class Trip {

		final int key;

		final String id;

		final String routeId;

		final String serviceId;

		final String shapeId;

		final short directionId;

		final String headsign;

		int stopCount;

		int lastSequence = -1;

		int lastDeparture;

		double lastDist;

		int firstDeparture;

		int lastArrival;

		Trip(int key, String id, String routeId, String serviceId, String shapeId, short directionId, String headsign) {
			if (directionId != 0 && directionId != 1) {
				throw new GtfsImportException("Trip " + id + " has direction_id " + directionId);
			}
			this.key = key;
			this.id = id;
			this.routeId = routeId;
			this.serviceId = serviceId;
			this.shapeId = shapeId;
			this.directionId = directionId;
			this.headsign = headsign;
		}

		void add(int sequence, int arrival, int departure, double dist) {
			if (sequence <= lastSequence) {
				throw new GtfsImportException("Trip " + id + " stop_sequence " + sequence + " after " + lastSequence);
			}
			if (departure > GtfsTime.MAX_SECONDS) {
				throw new GtfsImportException("Trip " + id + " runs past 48:00:00 at sequence " + sequence);
			}
			if (departure < arrival) {
				throw new GtfsImportException("Trip " + id + " departs before it arrives at sequence " + sequence);
			}
			if (stopCount > 0 && (arrival < lastDeparture || dist < lastDist)) {
				throw new GtfsImportException("Trip " + id + " goes back in time or distance at sequence " + sequence);
			}
			if (stopCount == 0) {
				firstDeparture = departure;
			}
			lastArrival = arrival;
			lastSequence = sequence;
			lastDeparture = departure;
			lastDist = dist;
			stopCount++;
		}

	}

	private static final class Stop {

		final int key;

		final String id;

		final String stationRef;

		final String name;

		final double lat;

		final double lon;

		boolean used;

		Stop(int key, String id, String stationRef, String name, double lat, double lon) {
			this.key = key;
			this.id = id;
			this.stationRef = stationRef;
			this.name = name;
			this.lat = lat;
			this.lon = lon;
		}

	}

	/** {@code at:49:282:0:4} becomes {@code at:49:282}. Ids without that shape stay as they are. */
	static String stationRef(String stopId) {
		int colons = 0;
		for (int i = 0; i < stopId.length(); i++) {
			if (stopId.charAt(i) == ':' && ++colons == 3) {
				return stopId.substring(0, i);
			}
		}
		return stopId;
	}

	private static void copyRow(PgCopy copy, Object... values) throws SQLException {
		copy.row(values);
	}

	private static String required(NamedCsvRecord row, String field) {
		String value = row.findField(field).orElse("");
		if (value.isBlank()) {
			throw new GtfsImportException("missing " + field);
		}
		return value;
	}

	private static String blankToNull(String value) {
		return value.isBlank() ? null : value;
	}

	private static short flag(NamedCsvRecord row, String field) {
		String value = row.findField(field).orElse("");
		return value.isBlank() ? 0 : Short.parseShort(value);
	}

	private static LocalDate date(String value) {
		return LocalDate.parse(value, ServiceCalendar.GTFS_DATE);
	}

	private static Reader withoutBom(ZipFile zip, ZipEntry entry) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8), 1 << 16);
		reader.mark(1);
		if (reader.read() != '\uFEFF') {
			reader.reset();
		}
		return reader;
	}

}
