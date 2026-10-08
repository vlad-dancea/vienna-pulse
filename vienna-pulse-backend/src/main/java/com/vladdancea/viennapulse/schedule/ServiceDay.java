package com.vladdancea.viennapulse.schedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.vladdancea.viennapulse.gtfs.GtfsTime;

/**
 * One GTFS service day. GTFS times count seconds from "noon minus 12 hours" local time.
 * That is midnight on normal days, but 23:00 the evening before when clocks spring
 * forward and 01:00 when they fall back. Times can pass 24:00, so a trip of yesterday's
 * service day can still be running today.
 *
 * @param date the service date
 * @param origin the instant that GTFS time 00:00:00 refers to on this date
 */
public record ServiceDay(LocalDate date, Instant origin) {

	private static final LocalTime NOON = LocalTime.NOON;

	public static ServiceDay of(LocalDate date, ZoneId zone) {
		// ZonedDateTime.minusHours works on the instant time line, so DST is handled.
		return new ServiceDay(date, date.atTime(NOON).atZone(zone).minusHours(12).toInstant());
	}

	/** The instant a GTFS time of this service day happens. */
	public Instant at(int seconds) {
		return origin.plusSeconds(seconds);
	}

	/** Seconds from this day's origin to {@code instant}, negative before the origin. */
	public long secondsAt(Instant instant) {
		return Duration.between(origin, instant).getSeconds();
	}

	/**
	 * The service days whose trips can overlap {@code [from, to]}. A trip of day D runs
	 * between D's origin and at most {@link GtfsTime#MAX_SECONDS} later.
	 *
	 * @return days in date order
	 */
	public static List<ServiceDay> overlapping(Instant from, Instant to, ZoneId zone) {
		if (to.isBefore(from)) {
			throw new IllegalArgumentException("to is before from");
		}
		long daysBack = Math.ceilDiv(GtfsTime.MAX_SECONDS, 86_400) + 1;
		LocalDate first = from.atZone(zone).toLocalDate().minusDays(daysBack);
		LocalDate last = to.atZone(zone).toLocalDate().plusDays(1);
		List<ServiceDay> days = new ArrayList<>();
		for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
			ServiceDay day = of(date, zone);
			if (!day.origin().isAfter(to) && !day.at(GtfsTime.MAX_SECONDS).isBefore(from)) {
				days.add(day);
			}
		}
		return days;
	}

}
