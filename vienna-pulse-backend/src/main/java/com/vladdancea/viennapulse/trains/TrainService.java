package com.vladdancea.viennapulse.trains;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vladdancea.viennapulse.schedule.ActiveFeed;
import com.vladdancea.viennapulse.schedule.ActiveTrip;
import com.vladdancea.viennapulse.schedule.ServiceDay;
import com.vladdancea.viennapulse.schedule.TimetableService;
import com.vladdancea.viennapulse.trains.StopTimeRepository.StopTimeRow;
import com.vladdancea.viennapulse.trains.Trains.Stop;
import com.vladdancea.viennapulse.trains.Trains.Train;
import org.springframework.stereotype.Service;

/** Builds the planned trains for a window (Phase 1: timetable only, no live delays). */
@Service
public class TrainService {

	private final TimetableService timetable;

	private final StopTimeRepository stopTimes;

	private final Clock clock;

	TrainService(TimetableService timetable, StopTimeRepository stopTimes, Clock clock) {
		this.timetable = timetable;
		this.stopTimes = stopTimes;
		this.clock = clock;
	}

	/** @return empty while no feed is imported */
	public Optional<Trains> trains(Instant from, Instant to) {
		Optional<ActiveFeed> active = timetable.activeFeed();
		if (active.isEmpty()) {
			return Optional.empty();
		}
		ActiveFeed feed = active.get();
		List<ActiveTrip> trips = timetable.activeTrips(from, to);
		Map<Integer, List<StopTimeRow>> byTrip = new LinkedHashMap<>();
		LinkedHashSet<Integer> keys = new LinkedHashSet<>();
		trips.forEach(trip -> keys.add(trip.tripKey()));
		for (StopTimeRow row : stopTimes.stopTimes(feed.id(), keys)) {
			byTrip.computeIfAbsent(row.tripKey(), key -> new ArrayList<>()).add(row);
		}
		List<Train> trains = new ArrayList<>(trips.size());
		for (ActiveTrip trip : trips) {
			ServiceDay day = ServiceDay.of(trip.serviceDate(), feed.zone());
			List<Stop> stops = byTrip.getOrDefault(trip.tripKey(), List.of())
				.stream()
				.map(row -> new Stop(row.stopId(), row.name(), day.at(row.arrivalS()).getEpochSecond(),
						day.at(row.departureS()).getEpochSecond(), Math.round(row.distM() * 10) / 10.0))
				.toList();
			trains.add(new Train(trip.serviceDate() + "/" + trip.tripId(), trip.serviceDate(), trip.line(),
					trip.directionId(), trip.headsign(), trip.shapeId(),
					around(stops, from.getEpochSecond(), to.getEpochSecond())));
		}
		return Optional.of(new Trains(clock.instant().getEpochSecond(), from.getEpochSecond(), to.getEpochSecond(),
				feed.id(), trains));
	}

	/**
	 * Keeps the stops the client needs for the window: from the last stop departed at or
	 * before {@code from} to the first stop reached at or after {@code to}.
	 */
	static List<Stop> around(List<Stop> stops, long from, long to) {
		int first = 0;
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).departure() <= from) {
				first = i;
			}
		}
		int last = stops.size() - 1;
		for (int i = first; i < stops.size(); i++) {
			if (stops.get(i).arrival() >= to) {
				last = i;
				break;
			}
		}
		return stops.subList(first, last + 1);
	}

}
