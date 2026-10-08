package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PgCopyTests {

	@Test
	void encodesValuesInCopyTextFormat() {
		assertThat(encode(null)).isEqualTo("\\N");
		assertThat(encode("Wien\tMitte\\U4\nx\r")).isEqualTo("Wien\\tMitte\\\\U4\\nx\\r");
		assertThat(encode(42)).isEqualTo("42");
		assertThat(encode(19053.77)).isEqualTo("19053.77");
		assertThat(encode(true)).isEqualTo("t");
		assertThat(encode(LocalDate.of(2026, 10, 8))).isEqualTo("2026-10-08");
		assertThat(encode(new double[] { 48.2, 16.43, 0.0 })).isEqualTo("{48.2,16.43,0.0}");
	}

	@Test
	void derivesTheStationFromTheStopId() {
		assertThat(GtfsZipImporter.stationRef("at:49:282:0:4")).isEqualTo("at:49:282");
		assertThat(GtfsZipImporter.stationRef("at:43:3134:1:2")).isEqualTo("at:43:3134");
		assertThat(GtfsZipImporter.stationRef("12345")).isEqualTo("12345");
	}

	private static String encode(Object value) {
		StringBuilder out = new StringBuilder();
		PgCopy.encode(value, out);
		return out.toString();
	}

}
