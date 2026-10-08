import type { LayerSpecification } from 'maplibre-gl';
import type { LineFeature, LineGeometry, LineProperties } from './line-geometry';
import { lineColor } from './line-colors';

export const LINES_SOURCE = 'lines';

/** The lines as drawn: one direction per line, each with its display color. */
export interface MapLines {
  type: 'FeatureCollection';
  features: (Omit<LineFeature, 'properties'> & { properties: LineProperties & { neon: string } })[];
}

/**
 * Adds the neon color to every feature and keeps one direction per line: both directions
 * run on almost the same track, drawing both would double the glow.
 */
export function linesForMap(lines: LineGeometry): MapLines {
  return {
    type: 'FeatureCollection',
    features: lines.features
      .filter((feature) => feature.properties.directionId === 0)
      .map((feature) => ({
        type: 'Feature' as const,
        geometry: feature.geometry,
        properties: {
          ...feature.properties,
          neon: lineColor(feature.properties.line, feature.properties.color),
        },
      })),
  };
}

/** A wide blurred line under a thin sharp one gives the neon look. */
export const LINE_LAYERS: LayerSpecification[] = [
  {
    id: 'line-glow',
    type: 'line',
    source: LINES_SOURCE,
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: {
      'line-color': ['get', 'neon'],
      'line-width': ['interpolate', ['linear'], ['zoom'], 10, 8, 14, 18],
      'line-blur': ['interpolate', ['linear'], ['zoom'], 10, 6, 14, 12],
      'line-opacity': 0.35,
    },
  },
  {
    id: 'line-core',
    type: 'line',
    source: LINES_SOURCE,
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: {
      'line-color': ['get', 'neon'],
      'line-width': ['interpolate', ['linear'], ['zoom'], 10, 2, 14, 4],
      'line-opacity': 0.9,
    },
  },
];

/** [west, south, east, north] around all lines, null when there are none. */
export function boundsOf(lines: MapLines): [number, number, number, number] | null {
  let west = Infinity;
  let south = Infinity;
  let east = -Infinity;
  let north = -Infinity;
  for (const feature of lines.features) {
    for (const [lon, lat] of feature.geometry.coordinates) {
      west = Math.min(west, lon);
      east = Math.max(east, lon);
      south = Math.min(south, lat);
      north = Math.max(north, lat);
    }
  }
  return west === Infinity ? null : [west, south, east, north];
}
