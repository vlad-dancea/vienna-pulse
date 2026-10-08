import type { LineGeometry } from './line-geometry';
import { lineColor } from './line-colors';
import { boundsOf, linesForMap } from './map-layers';

const LINES: LineGeometry = {
  type: 'FeatureCollection',
  serviceDate: '2026-10-08',
  feedVersion: 1,
  features: [
    feature('U1', 0, '#E3000F', [
      [16.3, 48.1],
      [16.4, 48.3],
    ]),
    feature('U1', 1, '#E3000F', [
      [16.4, 48.3],
      [16.3, 48.1],
    ]),
    feature('U9', 0, '#123456', [
      [16.2, 48.2],
      [16.5, 48.2],
    ]),
  ],
};

describe('map layers', () => {
  it('keeps one direction per line and adds the display color', () => {
    const lines = linesForMap(LINES);

    expect(lines.features.map((f) => f.properties.line)).toEqual(['U1', 'U9']);
    expect(lines.features[0].properties.neon).toBe(lineColor('U1'));
    // Unknown lines fall back to the official color from the API.
    expect(lines.features[1].properties.neon).toBe('#123456');
  });

  it('computes the bounds of all lines', () => {
    expect(boundsOf(linesForMap(LINES))).toEqual([16.2, 48.1, 16.5, 48.3]);
    expect(boundsOf({ type: 'FeatureCollection', features: [] })).toBeNull();
  });
});

function feature(line: string, directionId: 0 | 1, color: string, coordinates: [number, number][]) {
  return {
    type: 'Feature' as const,
    geometry: { type: 'LineString' as const, coordinates },
    properties: { line, directionId, color, shapeId: `${line}-${directionId}`, lengthM: 1000 },
  };
}
