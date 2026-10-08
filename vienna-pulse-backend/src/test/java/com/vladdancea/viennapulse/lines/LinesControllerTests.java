package com.vladdancea.viennapulse.lines;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

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
class LinesControllerTests {

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
	void returnsOneGeoJsonLinePerLineAndDirection() {
		MvcTestResult result = mvc.get().uri("/api/lines?date=2026-10-08").exchange();

		assertThat(result).hasStatusOk().hasContentType("application/geo+json");
		assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("FeatureCollection");
		assertThat(result).bodyJson().extractingPath("$.serviceDate").isEqualTo("2026-10-08");
		assertThat(result).bodyJson().extractingPath("$.features.length()").isEqualTo(2);
		assertThat(result).bodyJson().extractingPath("$.features[0].properties.line").isEqualTo("U1");
		assertThat(result).bodyJson().extractingPath("$.features[0].properties.directionId").isEqualTo(0);
		assertThat(result).bodyJson().extractingPath("$.features[0].properties.color").isEqualTo("#E3000F");
		assertThat(result).bodyJson().extractingPath("$.features[0].properties.shapeId").isEqualTo("s1");
		assertThat(result).bodyJson().extractingPath("$.features[0].properties.lengthM").isEqualTo(1200.3);
		assertThat(result).bodyJson().extractingPath("$.features[0].geometry.type").isEqualTo("LineString");
		// GeoJSON order is [lon, lat], points in shape order.
		assertThat(result).bodyJson()
			.extractingPath("$.features[0].geometry.coordinates[0]")
			.asArray()
			.containsExactly(16.1, 48.1);
		assertThat(result).bodyJson().extractingPath("$.features[0].geometry.coordinates.length()").isEqualTo(3);
		assertThat(result).bodyJson().extractingPath("$.features[1].properties.shapeId").isEqualTo("s2");
	}

	@Test
	void answersNotModifiedWhenTheClientHasTheVersion() {
		MvcTestResult first = mvc.get().uri("/api/lines?date=2026-10-08").exchange();
		String etag = first.getResponse().getHeader(HttpHeaders.ETAG);

		assertThat(etag).isNotBlank();
		assertThat(first).hasHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600, public");
		assertThat(mvc.get().uri("/api/lines?date=2026-10-08").header(HttpHeaders.IF_NONE_MATCH, etag)).hasStatus(304);
	}

	@Test
	void isEmptyOnDaysWithoutService() {
		// Sunday: the test feed only runs on weekdays.
		assertThat(mvc.get().uri("/api/lines?date=2026-10-11")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.features.length()")
			.isEqualTo(0);
	}

	@Test
	void defaultsToTodayInTheFeedTimeZone() {
		assertThat(mvc.get().uri("/api/lines")).hasStatusOk().bodyJson().extractingPath("$.serviceDate").isNotNull();
	}

	@Test
	void rejectsInvalidDates() {
		assertThat(mvc.get().uri("/api/lines?date=8.10.2026")).hasStatus(400);
	}

	@Test
	void isUnavailableBeforeTheFirstImport() {
		jdbc.sql("UPDATE gtfs_feed_version SET active = false").update();

		assertThat(mvc.get().uri("/api/lines")).hasStatus(503);
	}

}
