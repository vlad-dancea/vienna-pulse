package com.vladdancea.viennapulse.trains;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs against the test feed described in {@link GtfsTestFeed}. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class TrainsControllerTests {

	private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

	@TempDir
	Path dir;

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private GtfsImporter importer;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private TransactionTemplate transaction;

	@BeforeEach
	void importTheTestFeed() throws IOException {
		GtfsTestFeed.importActive(dir, importer, jdbc, transaction);
	}

	@Test
	void returnsTheTrainsOfTheWindowWithEpochTimes() {
		MvcTestResult result = mvc.get().uri("/api/trains?at=2026-10-08T03:00:00Z&minutes=10").exchange();

		assertThat(result).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		assertThat(result).bodyJson().extractingPath("$.from").convertTo(Long.class).isEqualTo(epoch("2026-10-08T05:00"));
		assertThat(result).bodyJson().extractingPath("$.to").convertTo(Long.class).isEqualTo(epoch("2026-10-08T05:10"));
		assertThat(result).bodyJson().extractingPath("$.trains.length()").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.trains[0].id").isEqualTo("2026-10-08/t1");
		assertThat(result).bodyJson().extractingPath("$.trains[0].line").isEqualTo("U1");
		assertThat(result).bodyJson().extractingPath("$.trains[0].shapeId").isEqualTo("s1");
		assertThat(result).bodyJson().extractingPath("$.trains[0].stops.length()").isEqualTo(3);
		assertThat(result).bodyJson().extractingPath("$.trains[0].stops[1].name").isEqualTo("Beta");
		assertThat(result).bodyJson().extractingPath("$.trains[0].stops[1].arrival").convertTo(Long.class).isEqualTo(epoch("2026-10-08T05:00"));
		assertThat(result).bodyJson()
			.extractingPath("$.trains[0].stops[1].departure").convertTo(Long.class).isEqualTo(epoch("2026-10-08T05:00") + 30);
		assertThat(result).bodyJson().extractingPath("$.trains[0].stops[1].distM").isEqualTo(600.5);
	}

	@Test
	void sendsOnlyTheStopsAroundAShortWindow() {
		// 05:01 to 05:02: the train is between Beta (dep 05:00:30) and Gamma (arr 05:03).
		assertThat(mvc.get().uri("/api/trains?at=2026-10-08T03:01:00Z&minutes=1")).bodyJson()
			.extractingPath("$.trains[0].stops[*].name")
			.asArray()
			.containsExactly("Beta", "Gamma");
	}

	@Test
	void namesTripsAfterMidnightByTheirServiceDay() {
		// Saturday 01:00 in Vienna, t2 belongs to Friday's service.
		assertThat(mvc.get().uri("/api/trains?at=2026-10-09T23:00:00Z&minutes=1")).bodyJson()
			.extractingPath("$.trains[0].id")
			.isEqualTo("2026-10-09/t2");
	}

	@Test
	void rejectsWindowsOutsideOneToThirtyMinutes() {
		assertThat(mvc.get().uri("/api/trains?minutes=0")).hasStatus(400);
		assertThat(mvc.get().uri("/api/trains?minutes=31")).hasStatus(400);
	}

	@Test
	void servesShapesWithTheDistanceOfEveryPoint() {
		MvcTestResult result = mvc.get().uri("/api/shapes/s1").exchange();

		assertThat(result).hasStatusOk().hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=86400, public");
		assertThat(result).bodyJson().extractingPath("$.geometry.coordinates.length()").isEqualTo(3);
		assertThat(result).bodyJson()
			.extractingPath("$.properties.distancesM")
			.asArray()
			.containsExactly(0.0, 600.5, 1200.3);
		assertThat(mvc.get().uri("/api/shapes/does-not-exist")).hasStatus(404);
	}

	@Test
	void servesShapeIdsWithDots() {
		jdbc.sql("""
				INSERT INTO shape (feed_version_id, shape_id, lats, lons, dists_m, length_m)
				SELECT feed_version_id, '21-U1-j26-6.7.H', lats, lons, dists_m, length_m FROM shape WHERE shape_id = 's1'
				""").update();

		assertThat(mvc.get().uri("/api/shapes/21-U1-j26-6.7.H")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.properties.shapeId")
			.isEqualTo("21-U1-j26-6.7.H");
	}

	@Test
	void isUnavailableBeforeTheFirstImport() {
		jdbc.sql("UPDATE gtfs_feed_version SET active = false").update();

		assertThat(mvc.get().uri("/api/trains")).hasStatus(503);
	}

	private static long epoch(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(VIENNA).toEpochSecond();
	}

}
