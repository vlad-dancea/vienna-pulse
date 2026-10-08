package com.vladdancea.viennapulse.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.vladdancea.viennapulse.TestcontainersConfiguration;
import com.vladdancea.viennapulse.gtfs.GtfsImporter;
import com.vladdancea.viennapulse.gtfs.GtfsTestFeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs against the test feed described in {@link GtfsTestFeed}. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TimetableServiceTests {

	private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

	@TempDir
	Path dir;

	@Autowired
	private TimetableService timetable;

	@Autowired
	private GtfsImporter importer;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@BeforeEach
	void importTheTestFeed() throws IOException {
		jdbc.sql("TRUNCATE gtfs_feed_version, stop_time CASCADE").update();
		long version = jdbc
			.sql("INSERT INTO gtfs_feed_version (sha256, size_bytes) VALUES (repeat('t', 64), 1) RETURNING id")
			.query(Long.class)
			.single();
		Path zip = GtfsTestFeed.zip(dir, GtfsTestFeed.files());
		transaction.executeWithoutResult(status -> {
			importer.importFeed(version, zip);
			jdbc.sql("UPDATE gtfs_feed_version SET active = true, imported_at = now() WHERE id = :id")
				.param("id", version)
				.update();
		});
	}

	@Test
	void findsTheTripOnTheRoadAtAnInstant() {
		Instant fiveAm = vienna("2026-10-08T05:00");

		assertThat(timetable.activeTrips(fiveAm, fiveAm)).singleElement().satisfies(trip -> {
			assertThat(trip.tripId()).isEqualTo("t1");
			assertThat(trip.line()).isEqualTo("U1");
			assertThat(trip.serviceDate()).isEqualTo(LocalDate.of(2026, 10, 8));
			assertThat(trip.departsAt()).isEqualTo(vienna("2026-10-08T04:57"));
			assertThat(trip.arrivesAt()).isEqualTo(vienna("2026-10-08T05:03"));
		});
	}

	@Test
	void findsTripsOfYesterdaysServiceDayAfterMidnight() {
		// Saturday 01:00. t2 belongs to Friday's service and runs 24:58 to 25:10.
		Instant oneAm = vienna("2026-10-10T01:00");

		assertThat(timetable.activeTrips(oneAm, oneAm)).singleElement().satisfies(trip -> {
			assertThat(trip.tripId()).isEqualTo("t2");
			assertThat(trip.serviceDate()).isEqualTo(LocalDate.of(2026, 10, 9));
			assertThat(trip.arrivesAt()).isEqualTo(vienna("2026-10-10T01:10"));
		});
	}

	@Test
	void respectsRemovedServiceDates() {
		Instant fiveAm = vienna("2026-10-12T05:00");

		assertThat(timetable.activeTrips(fiveAm, fiveAm)).isEmpty();
	}

	@Test
	void includesTripsThatStartInsideTheWindow() {
		assertThat(timetable.activeTrips(vienna("2026-10-08T04:50"), vienna("2026-10-08T04:58")))
			.extracting(ActiveTrip::tripId)
			.containsExactly("t1");
		assertThat(timetable.activeTrips(vienna("2026-10-08T04:50"), vienna("2026-10-08T04:56"))).isEmpty();
	}

	@Test
	void isEmptyWithoutAnActiveFeed() {
		jdbc.sql("UPDATE gtfs_feed_version SET active = false").update();
		Instant fiveAm = vienna("2026-10-08T05:00");

		assertThat(timetable.activeTrips(fiveAm, fiveAm)).isEmpty();
	}

	private static Instant vienna(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(VIENNA).toInstant();
	}

}
