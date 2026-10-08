package com.vladdancea.viennapulse.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ServiceDayTests {

	private static final ZoneId VIENNA = ZoneId.of("Europe/Vienna");

	@Test
	void startsAtMidnightOnNormalDays() {
		assertThat(ServiceDay.of(LocalDate.of(2026, 10, 8), VIENNA).origin())
			.isEqualTo(vienna("2026-10-08T00:00"));
	}

	@Test
	void startsAt2300TheEveningBeforeWhenClocksSpringForward() {
		// 29 March 2026: 02:00 CET becomes 03:00 CEST.
		assertThat(ServiceDay.of(LocalDate.of(2026, 3, 29), VIENNA).origin())
			.isEqualTo(vienna("2026-03-28T23:00"));
	}

	@Test
	void startsAt0100WhenClocksFallBack() {
		// 25 October 2026: 03:00 CEST becomes 02:00 CET.
		assertThat(ServiceDay.of(LocalDate.of(2026, 10, 25), VIENNA).origin())
			.isEqualTo(Instant.parse("2026-10-24T23:00:00Z"))
			.isEqualTo(vienna("2026-10-25T01:00"));
	}

	@Test
	void placesTimesAfterMidnightOnTheNextCalendarDay() {
		ServiceDay friday = ServiceDay.of(LocalDate.of(2026, 10, 9), VIENNA);

		assertThat(friday.at(25 * 3600 + 10 * 60)).isEqualTo(vienna("2026-10-10T01:10"));
		assertThat(friday.secondsAt(vienna("2026-10-10T01:10"))).isEqualTo(90_600);
	}

	@Test
	void findsYesterdaysServiceDayAfterMidnight() {
		Instant oneAm = vienna("2026-10-10T01:00");

		assertThat(ServiceDay.overlapping(oneAm, oneAm, VIENNA)).map(ServiceDay::date)
			.containsExactly(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));
	}

	@Test
	void findsTomorrowsServiceDayBeforeASpringForward() {
		Instant lateEvening = vienna("2026-03-28T23:30");

		assertThat(ServiceDay.overlapping(lateEvening, lateEvening, VIENNA)).map(ServiceDay::date)
			.containsExactly(LocalDate.of(2026, 3, 27), LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29));
	}

	private static Instant vienna(String localDateTime) {
		return LocalDateTime.parse(localDateTime).atZone(VIENNA).toInstant();
	}

}
