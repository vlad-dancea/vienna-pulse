package com.vladdancea.viennapulse.lines;

import java.time.Duration;
import java.time.LocalDate;

import com.vladdancea.viennapulse.schedule.ActiveFeed;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/** Line geometry for the map. Static per feed version and day, so browsers cache it. */
@RestController
class LinesController {

	static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

	private final LineGeometryService lines;

	LinesController(LineGeometryService lines) {
		this.lines = lines;
	}

	/**
	 * @param date service date, today in the feed's time zone when omitted
	 * @return one feature per line and direction, 304 when the client already has it
	 */
	@GetMapping(path = "/api/lines", produces = { "application/geo+json", MediaType.APPLICATION_JSON_VALUE })
	@Nullable ResponseEntity<LineGeometry> lines(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
			WebRequest request) {
		ActiveFeed feed = lines.activeFeed()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No timetable imported yet"));
		LocalDate serviceDate = date != null ? date : LocalDate.now(feed.zone());
		String etag = "\"lines-" + feed.id() + "-" + serviceDate + "\"";
		if (request.checkNotModified(etag)) {
			return null;
		}
		return ResponseEntity.ok()
			.eTag(etag)
			.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
			.body(lines.linesOn(feed, serviceDate));
	}

}
