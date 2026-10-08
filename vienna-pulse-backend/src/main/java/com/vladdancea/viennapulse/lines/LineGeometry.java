package com.vladdancea.viennapulse.lines;

import java.time.LocalDate;
import java.util.List;

/**
 * Line geometries as a GeoJSON FeatureCollection, ready for a MapLibre source. Coordinates
 * are {@code [lon, lat]} as GeoJSON requires. {@code serviceDate} and {@code feedVersion}
 * are extra members that GeoJSON allows.
 */
public record LineGeometry(String type, LocalDate serviceDate, long feedVersion, List<Feature> features) {

	public LineGeometry(LocalDate serviceDate, long feedVersion, List<Feature> features) {
		this("FeatureCollection", serviceDate, feedVersion, features);
	}

	public record Feature(String type, LineString geometry, Properties properties) {

		public Feature(LineString geometry, Properties properties) {
			this("Feature", geometry, properties);
		}

	}

	public record LineString(String type, double[][] coordinates) {

		public LineString(double[][] coordinates) {
			this("LineString", coordinates);
		}

	}

	/**
	 * @param line route short name, e.g. U2
	 * @param directionId GTFS direction, 0 or 1
	 * @param color official line color as {@code #RRGGBB}, null when the feed has none
	 * @param shapeId the GTFS shape drawn for this line and direction
	 * @param lengthM length along the shape in meters
	 */
	public record Properties(String line, int directionId, String color, String shapeId, double lengthM) {
	}

}
