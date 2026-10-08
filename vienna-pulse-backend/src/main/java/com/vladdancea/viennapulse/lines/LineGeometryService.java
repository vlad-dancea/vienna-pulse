package com.vladdancea.viennapulse.lines;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vladdancea.viennapulse.lines.LineGeometry.Feature;
import com.vladdancea.viennapulse.lines.LineGeometry.LineString;
import com.vladdancea.viennapulse.lines.LineGeometry.Properties;
import com.vladdancea.viennapulse.lines.LineGeometryRepository.LineRow;
import com.vladdancea.viennapulse.schedule.ActiveFeed;
import com.vladdancea.viennapulse.schedule.TimetableService;
import org.springframework.stereotype.Service;

/** Builds the line geometry for a service day. Results are cached per feed version and date. */
@Service
public class LineGeometryService {

	private static final int CACHED_DAYS = 8;

	private final TimetableService timetable;

	private final LineGeometryRepository repository;

	private final Map<String, LineGeometry> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, LineGeometry> eldest) {
			return size() > CACHED_DAYS;
		}
	};

	LineGeometryService(TimetableService timetable, LineGeometryRepository repository) {
		this.timetable = timetable;
		this.repository = repository;
	}

	/** The active feed, needed to resolve "today" in the feed's time zone. */
	public Optional<ActiveFeed> activeFeed() {
		return timetable.activeFeed();
	}

	public LineGeometry linesOn(ActiveFeed feed, LocalDate serviceDate) {
		String key = feed.id() + "/" + serviceDate;
		synchronized (cache) {
			LineGeometry cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
		}
		List<Feature> features = repository.linesOn(feed.id(), serviceDate).stream().map(LineGeometryService::feature).toList();
		LineGeometry lines = new LineGeometry(serviceDate, feed.id(), features);
		synchronized (cache) {
			cache.put(key, lines);
		}
		return lines;
	}

	private static Feature feature(LineRow row) {
		double[][] coordinates = new double[row.lats().length][];
		for (int i = 0; i < coordinates.length; i++) {
			coordinates[i] = new double[] { round(row.lons()[i]), round(row.lats()[i]) };
		}
		String color = row.color() == null ? null : "#" + row.color().toUpperCase();
		return new Feature(new LineString(coordinates),
				new Properties(row.line(), row.directionId(), color, row.shapeId(), Math.round(row.lengthM() * 10) / 10.0));
	}

	/** Six decimals are about 0.1 m, more than enough for drawing. */
	private static double round(double degrees) {
		return Math.round(degrees * 1e6) / 1e6;
	}

}
