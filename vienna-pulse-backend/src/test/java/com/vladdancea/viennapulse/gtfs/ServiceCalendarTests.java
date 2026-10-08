package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ServiceCalendarTests {

	private static final boolean[] WEEKDAYS = { true, true, true, true, true, false, false };

	@Test
	void appliesWeekdaysThenExceptions() {
		// 2026-10-05 is a Monday. The 26th of October is a public holiday in Austria.
		var rule = new ServiceCalendar.Rule("T0", WEEKDAYS, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 26));
		var holiday = new ServiceCalendar.Exception("T0", LocalDate.of(2026, 10, 26), false);
		var extraSaturday = new ServiceCalendar.Exception("T0", LocalDate.of(2026, 10, 10), true);

		var dates = ServiceCalendar.expand(List.of(rule), List.of(holiday, extraSaturday)).get("T0");

		assertThat(dates).hasSize(16)
			.startsWith(LocalDate.of(2026, 10, 5))
			.contains(LocalDate.of(2026, 10, 10))
			.doesNotContain(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 26))
			.endsWith(LocalDate.of(2026, 10, 23));
	}

	@Test
	void supportsServicesWithOnlyExceptions() {
		var day = LocalDate.of(2026, 12, 24);

		var dates = ServiceCalendar.expand(List.of(), List.of(new ServiceCalendar.Exception("X", day, true)));

		assertThat(dates.get("X")).containsExactly(day);
	}

	@Test
	void writesWeekdaysAsBits() {
		var rule = new ServiceCalendar.Rule("T0", WEEKDAYS, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

		assertThat(rule.weekdayBits()).isEqualTo("1111100");
	}

}
