package com.vladdancea.viennapulse.schedule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vladdancea.viennapulse.schedule.TimetableRepository.ActiveFeed;
import com.vladdancea.viennapulse.schedule.TimetableRepository.TripRow;
import org.springframework.stereotype.Service;

/** Answers "which planned trips are running" for real instants, across midnight and DST. */
@Service
public class TimetableService {

	private final TimetableRepository repository;

	TimetableService(TimetableRepository repository) {
		this.repository = repository;
	}

	/**
	 * Trips that are on the road at some moment in {@code [from, to]}: already departed from
	 * their first stop by {@code to} and not yet arrived at their last stop by {@code from}.
	 *
	 * @return trips ordered by departure, empty while no feed is imported
	 */
	public List<ActiveTrip> activeTrips(Instant from, Instant to) {
		ActiveFeed feed = repository.activeFeed().orElse(null);
		if (feed == null) {
			return List.of();
		}
		List<ActiveTrip> trips = new ArrayList<>();
		for (ServiceDay day : ServiceDay.overlapping(from, to, feed.zone())) {
			int fromS = clamp(day.secondsAt(from));
			int toS = clamp(day.secondsAt(to));
			for (TripRow row : repository.tripsRunning(feed.id(), day.date(), fromS, toS)) {
				trips.add(new ActiveTrip(day.date(), row.tripKey(), row.tripId(), row.line(), row.directionId(),
						row.headsign(), row.shapeId(), day.at(row.firstDepartureS()), day.at(row.lastArrivalS())));
			}
		}
		trips.sort(Comparator.comparing(ActiveTrip::departsAt).thenComparing(ActiveTrip::tripId));
		return trips;
	}

	private static int clamp(long seconds) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, seconds));
	}

}
