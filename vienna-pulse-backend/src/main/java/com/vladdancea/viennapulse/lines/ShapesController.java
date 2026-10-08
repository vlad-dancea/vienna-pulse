package com.vladdancea.viennapulse.lines;

import java.time.Duration;
import java.util.Optional;

import com.vladdancea.viennapulse.lines.LineGeometryRepository.ShapeRow;
import com.vladdancea.viennapulse.schedule.ActiveFeed;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * One GTFS shape with the distance of every point, so the client can turn a train's
 * distance along its trip into a position. Shapes never change within a feed version.
 */
@RestController
class ShapesController {

	private final LineGeometryService lines;

	private final LineGeometryRepository repository;

	ShapesController(LineGeometryService lines, LineGeometryRepository repository) {
		this.lines = lines;
		this.repository = repository;
	}

	@GetMapping(path = "/api/shapes/{shapeId}", produces = { "application/geo+json", MediaType.APPLICATION_JSON_VALUE })
	@Nullable ResponseEntity<ShapeFeature> shape(@PathVariable String shapeId, WebRequest request) {
		ActiveFeed feed = lines.activeFeed()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No timetable imported yet"));
		String etag = "\"shape-" + feed.id() + "-" + shapeId + "\"";
		if (request.checkNotModified(etag)) {
			return null;
		}
		Optional<ShapeRow> shape = repository.shape(feed.id(), shapeId);
		if (shape.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown shape " + shapeId);
		}
		return ResponseEntity.ok()
			.eTag(etag)
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
			.body(ShapeFeature.of(feed.id(), shape.get()));
	}

	/**
	 * A GeoJSON Feature. {@code properties.distancesM[i]} is the distance of
	 * {@code coordinates[i]} from the start of the shape.
	 */
	record ShapeFeature(String type, long feedVersion, LineGeometry.LineString geometry, Properties properties) {

		static ShapeFeature of(long version, ShapeRow row) {
			double[][] coordinates = LineGeometryService.coordinates(row.lats(), row.lons());
			double[] distances = new double[row.distances().length];
			for (int i = 0; i < distances.length; i++) {
				distances[i] = Math.round(row.distances()[i] * 10) / 10.0;
			}
			return new ShapeFeature("Feature", version, new LineGeometry.LineString(coordinates),
					new Properties(row.shapeId(), Math.round(row.lengthM() * 10) / 10.0, distances));
		}

		record Properties(String shapeId, double lengthM, double[] distancesM) {
		}

	}

}
