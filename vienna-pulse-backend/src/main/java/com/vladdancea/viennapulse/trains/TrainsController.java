package com.vladdancea.viennapulse.trains;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Planned trains for the next minutes. The client polls about once a minute. */
@RestController
class TrainsController {

	static final int MAX_MINUTES = 30;

	private final TrainService trains;

	private final Clock clock;

	TrainsController(TrainService trains, Clock clock) {
		this.trains = trains;
		this.clock = clock;
	}

	/**
	 * @param at start of the window, now when omitted (for debugging and replays)
	 * @param minutes length of the window, 1 to 30
	 */
	@GetMapping("/api/trains")
	ResponseEntity<Trains> trains(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Nullable Instant at,
			@RequestParam(defaultValue = "10") int minutes) {
		if (minutes < 1 || minutes > MAX_MINUTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minutes must be between 1 and " + MAX_MINUTES);
		}
		Instant from = at != null ? at : clock.instant();
		Trains body = trains.trains(from, from.plus(Duration.ofMinutes(minutes)))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No timetable imported yet"));
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

}
