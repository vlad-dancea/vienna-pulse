package com.vladdancea.viennapulse.gtfs;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Turns GTFS {@code calendar.txt} rules and {@code calendar_dates.txt} exceptions into the
 * concrete dates each service runs on.
 */
final class ServiceCalendar {

	static final DateTimeFormatter GTFS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private ServiceCalendar() {
	}

	/**
	 * A weekly rule from {@code calendar.txt}.
	 *
	 * @param weekdays one flag per day, Monday first
	 */
	record Rule(String serviceId, boolean[] weekdays, LocalDate start, LocalDate end) {

		Rule {
			if (weekdays.length != 7) {
				throw new IllegalArgumentException("A rule needs 7 weekday flags");
			}
			if (end.isBefore(start)) {
				throw new IllegalArgumentException("Service " + serviceId + " ends before it starts");
			}
		}

		boolean runsOn(DayOfWeek day) {
			return weekdays[day.getValue() - 1];
		}

		/** The flags as a Postgres {@code bit(7)} literal, Monday first. */
		String weekdayBits() {
			StringBuilder bits = new StringBuilder(7);
			for (boolean day : weekdays) {
				bits.append(day ? '1' : '0');
			}
			return bits.toString();
		}

	}

	/**
	 * An exception from {@code calendar_dates.txt}.
	 *
	 * @param added {@code true} for exception type 1 (added), {@code false} for type 2 (removed)
	 */
	record Exception(String serviceId, LocalDate date, boolean added) {
	}

	/**
	 * Applies the weekly rules first, then the exceptions. A service may have only exceptions.
	 *
	 * @return the dates per service, sorted
	 */
	static Map<String, NavigableSet<LocalDate>> expand(Collection<Rule> rules, Collection<Exception> exceptions) {
		Map<String, NavigableSet<LocalDate>> dates = new HashMap<>();
		for (Rule rule : rules) {
			NavigableSet<LocalDate> serviceDates = dates.computeIfAbsent(rule.serviceId(), id -> new TreeSet<>());
			for (LocalDate day = rule.start(); !day.isAfter(rule.end()); day = day.plusDays(1)) {
				if (rule.runsOn(day.getDayOfWeek())) {
					serviceDates.add(day);
				}
			}
		}
		for (Exception exception : exceptions) {
			NavigableSet<LocalDate> serviceDates = dates.computeIfAbsent(exception.serviceId(), id -> new TreeSet<>());
			if (exception.added()) {
				serviceDates.add(exception.date());
			}
			else {
				serviceDates.remove(exception.date());
			}
		}
		return dates;
	}

}
