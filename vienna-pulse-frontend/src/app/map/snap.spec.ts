import { drawnLine, pointOnLine, snappedPointAt, snapShape } from './snap';
import type { TripShape } from './trains';

// Around Vienna: 0.001° latitude is about 111 m, 0.001° longitude about 74 m.
const LAT = 48.2;
const EAST_WEST = drawnLine([
  [16.3, LAT],
  [16.31, LAT],
  [16.32, LAT],
]);

describe('snapping trip shapes onto the drawn line', () => {
  it('measures the drawn line in meters', () => {
    expect(EAST_WEST.cumulativeM[2]).toBeCloseTo(1484, -1);
  });

  it('moves a parallel shape 30 m away onto the line', () => {
    const shape = shapeOf([
      [16.301, LAT + 0.00027],
      [16.319, LAT + 0.00027],
    ]);

    const snapped = snapShape(shape, EAST_WEST)!;
    const middle = snappedPointAt(snapped, shape.distancesM[1] / 2);

    expect(middle[1]).toBeCloseTo(LAT, 9);
    expect(middle[0]).toBeCloseTo(16.31, 6);
  });

  it('follows the opposite direction', () => {
    const shape = shapeOf([
      [16.319, LAT - 0.0002],
      [16.301, LAT - 0.0002],
    ]);

    const snapped = snapShape(shape, EAST_WEST)!;

    expect(snappedPointAt(snapped, 0)[0]).toBeCloseTo(16.319, 6);
    expect(snappedPointAt(snapped, 99999)[0]).toBeCloseTo(16.301, 6);
  });

  it('refuses shapes that leave the drawn line', () => {
    const detour = shapeOf([
      [16.301, LAT],
      [16.305, LAT + 0.005],
    ]);

    expect(snapShape(detour, EAST_WEST)).toBeNull();
  });

  it('stays on the right leg of a line that folds back', () => {
    // A hairpin: east along LAT, then back west 89 m further north.
    const hairpin = drawnLine([
      [16.3, LAT],
      [16.31, LAT],
      [16.31, LAT + 0.0008],
      [16.3, LAT + 0.0008],
    ]);
    // The trip runs the whole hairpin 40 m inside, so on the way back it is
    // closer to the first leg's end region than a naive search would allow.
    const shape = shapeOf([
      [16.3, LAT + 0.00036],
      [16.3095, LAT + 0.00036],
      [16.3095, LAT + 0.00044],
      [16.3, LAT + 0.00044],
    ]);

    const snapped = snapShape(shape, hairpin)!;
    const end = snappedPointAt(snapped, 99999);

    expect(end[1]).toBeCloseTo(LAT + 0.0008, 6);
    expect(snapped.arcM.every((arc, i) => i === 0 || arc >= snapped.arcM[i - 1])).toBe(true);
  });

  it('clamps positions to the ends of the line', () => {
    expect(pointOnLine(EAST_WEST, -5)).toEqual([16.3, LAT]);
    expect(pointOnLine(EAST_WEST, 1e9)).toEqual([16.32, LAT]);
  });
});

function shapeOf(coordinates: [number, number][]): TripShape {
  const line = drawnLine(coordinates);
  return { coordinates, distancesM: line.cumulativeM };
}
