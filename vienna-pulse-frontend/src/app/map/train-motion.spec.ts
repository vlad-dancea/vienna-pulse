import { distanceAt, isOnMap, pointAt } from './train-motion';
import type { TrainStop, TripShape } from './trains';

// A dep 100, B arr 200 dep 230, C arr 300. Distances 0, 1000, 1600.
const STOPS: TrainStop[] = [
  { stopId: 'A', name: 'A', arrival: 100, departure: 100, distM: 0 },
  { stopId: 'B', name: 'B', arrival: 200, departure: 230, distM: 1000 },
  { stopId: 'C', name: 'C', arrival: 300, departure: 300, distM: 1600 },
];

const SHAPE: TripShape = {
  coordinates: [
    [16.0, 48.0],
    [16.1, 48.0],
    [16.1, 48.2],
  ],
  distancesM: [0, 1000, 3000],
};

describe('train motion', () => {
  it('waits at the first stop until departure', () => {
    expect(distanceAt(STOPS, 50)).toBe(0);
    expect(distanceAt(STOPS, 100)).toBe(0);
  });

  it('moves at constant speed between two stops', () => {
    expect(distanceAt(STOPS, 150)).toBe(500);
    expect(distanceAt(STOPS, 265)).toBe(1300);
  });

  it('dwells at a stop between arrival and departure', () => {
    expect(distanceAt(STOPS, 200)).toBe(1000);
    expect(distanceAt(STOPS, 215)).toBe(1000);
  });

  it('stays at the last stop after arriving', () => {
    expect(distanceAt(STOPS, 900)).toBe(1600);
  });

  it('handles two stops with the same planned minute', () => {
    const sameMinute: TrainStop[] = [
      { stopId: 'A', name: 'A', arrival: 100, departure: 100, distM: 0 },
      { stopId: 'B', name: 'B', arrival: 100, departure: 100, distM: 500 },
      { stopId: 'C', name: 'C', arrival: 160, departure: 160, distM: 1100 },
    ];
    expect(distanceAt(sameMinute, 130)).toBe(800);
  });

  it('shows a train shortly before departure until shortly after arrival', () => {
    expect(isOnMap(STOPS, 30)).toBe(false);
    expect(isOnMap(STOPS, 50)).toBe(true);
    expect(isOnMap(STOPS, 320)).toBe(true);
    expect(isOnMap(STOPS, 340)).toBe(false);
    expect(isOnMap([], 100)).toBe(false);
  });

  it('finds the point at a distance along the shape', () => {
    expect(pointAt(SHAPE, 500)).toEqual([16.05, 48.0]);
    expect(pointAt(SHAPE, 2000)[1]).toBeCloseTo(48.1);
    expect(pointAt(SHAPE, -10)).toEqual([16.0, 48.0]);
    expect(pointAt(SHAPE, 99999)).toEqual([16.1, 48.2]);
  });
});
