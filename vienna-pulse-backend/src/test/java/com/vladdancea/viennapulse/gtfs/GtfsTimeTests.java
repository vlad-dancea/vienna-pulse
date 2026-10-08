package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GtfsTimeTests {

	@Test
	void parsesTimesBeforeAndAfterMidnight() {
		assertThat(GtfsTime.toSeconds("04:57:00")).isEqualTo(17_820);
		assertThat(GtfsTime.toSeconds("4:57:00")).isEqualTo(17_820);
		assertThat(GtfsTime.toSeconds("25:10:00")).isEqualTo(90_600);
		assertThat(GtfsTime.toSeconds("29:00:59")).isEqualTo(104_459);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "12:00", "12:60:00", "12:00:60", "ab:00:00", "12:0:00", "-1:00:00" })
	void rejectsInvalidTimes(String value) {
		assertThatIllegalArgumentException().isThrownBy(() -> GtfsTime.toSeconds(value));
	}

}
