import type { TrainStop, TripShape } from './trains';

/** Show a train this long before it leaves its first stop (it waits at the platform). */
export const SHOW_BEFORE_DEPARTURE_S = 60;
/** Keep a train this long after it reached its last stop. */
export const SHOW_AFTER_ARRIVAL_S = 30;

/**
 * Planned distance along the shape at `now` (seconds). Phase 1 uses the timetable only:
 * the train waits at a stop between arrival and departure and runs at constant speed
 * between two stops.
 */
export function distanceAt(stops: readonly TrainStop[], now: number): number {
  const first = stops[0];
  if (now <= first.departure) {
    return first.distM;
  }
  for (let i = 0; i < stops.length - 1; i++) {
    const from = stops[i];
    const to = stops[i + 1];
    if (now <= from.departure) {
      return from.distM;
    }
    if (now < to.arrival) {
      const travel = to.arrival - from.departure;
      const progress = travel > 0 ? (now - from.departure) / travel : 1;
      return from.distM + (to.distM - from.distM) * progress;
    }
  }
  return stops[stops.length - 1].distM;
}

/** Whether the train belongs on the map at `now`. */
export function isOnMap(stops: readonly TrainStop[], now: number): boolean {
  return (
    stops.length > 0 &&
    now >= stops[0].departure - SHOW_BEFORE_DEPARTURE_S &&
    now <= stops[stops.length - 1].arrival + SHOW_AFTER_ARRIVAL_S
  );
}

/** The point at `distM` along the shape, interpolated between its two neighbours. */
export function pointAt(shape: TripShape, distM: number): [number, number] {
  const d = shape.distancesM;
  const c = shape.coordinates;
  let lo = 0;
  let hi = d.length - 1;
  if (distM <= d[lo]) {
    return c[lo];
  }
  if (distM >= d[hi]) {
    return c[hi];
  }
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (d[mid] <= distM) {
      lo = mid;
    } else {
      hi = mid;
    }
  }
  const span = d[hi] - d[lo];
  const f = span > 0 ? (distM - d[lo]) / span : 0;
  return [c[lo][0] + (c[hi][0] - c[lo][0]) * f, c[lo][1] + (c[hi][1] - c[lo][1]) * f];
}
