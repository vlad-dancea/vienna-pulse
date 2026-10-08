import type { TripShape } from './trains';

/**
 * Wiener Linien draws each direction of a line separately, and the two geometries can be
 * up to about 70 m apart. The map draws one geometry per line, so trains of the other
 * direction would float beside it. Snapping maps every point of a trip shape onto the
 * drawn line once, so trains move exactly on the line that is shown.
 */

/** A drawn line with the length along it at every vertex. */
export interface DrawnLine {
  coordinates: [number, number][];
  /** Meters from the first vertex. */
  cumulativeM: number[];
}

/** A trip shape linked to a drawn line: point i of the shape lies at `arcM[i]` on the line. */
export interface SnappedShape {
  distancesM: number[];
  arcM: number[];
  line: DrawnLine;
}

/** Shapes that stray further than this from the drawn line are not snapped. */
export const MAX_SNAP_OFFSET_M = 150;
/** How far along the line, beyond the gap between two shape points, the next point may land. */
const SEARCH_SLACK_M = 300;

const M_PER_DEG_LAT = 110_574;
const M_PER_DEG_LON_EQUATOR = 111_320;

export function drawnLine(coordinates: [number, number][]): DrawnLine {
  const cumulativeM = [0];
  const toXy = localMeters(coordinates[0][1]);
  for (let i = 1; i < coordinates.length; i++) {
    const [ax, ay] = toXy(coordinates[i - 1]);
    const [bx, by] = toXy(coordinates[i]);
    cumulativeM.push(cumulativeM[i - 1] + Math.hypot(bx - ax, by - ay));
  }
  return { coordinates, cumulativeM };
}

/**
 * Projects every point of `shape` onto `line`. The search for each point stays near the
 * previous one, so a line that folds back on itself cannot pull a point to its other leg.
 *
 * @return null when some point is further than {@link MAX_SNAP_OFFSET_M} from the line,
 * for example a diversion that the drawn line does not cover
 */
export function snapShape(shape: TripShape, line: DrawnLine): SnappedShape | null {
  const toXy = localMeters(line.coordinates[0][1]);
  const lineXy = line.coordinates.map(toXy);
  const arcM: number[] = [];
  let previous: number | null = null;
  for (let i = 0; i < shape.coordinates.length; i++) {
    const point = toXy(shape.coordinates[i]);
    let best: Projection | null = null;
    if (previous !== null) {
      const gap = shape.distancesM[i] - shape.distancesM[i - 1];
      best = project(
        point,
        lineXy,
        line.cumulativeM,
        previous - gap - SEARCH_SLACK_M,
        previous + gap + SEARCH_SLACK_M,
      );
    }
    if (!best || best.offsetM > MAX_SNAP_OFFSET_M) {
      best = project(point, lineXy, line.cumulativeM, -Infinity, Infinity);
    }
    if (!best || best.offsetM > MAX_SNAP_OFFSET_M) {
      return null;
    }
    arcM.push(best.arcM);
    previous = best.arcM;
  }
  return { distancesM: shape.distancesM, arcM, line };
}

/** The point at `distM` along the trip, placed on the drawn line. */
export function snappedPointAt(shape: SnappedShape, distM: number): [number, number] {
  const d = shape.distancesM;
  let lo = 0;
  let hi = d.length - 1;
  if (distM <= d[lo]) {
    return pointOnLine(shape.line, shape.arcM[lo]);
  }
  if (distM >= d[hi]) {
    return pointOnLine(shape.line, shape.arcM[hi]);
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
  return pointOnLine(shape.line, shape.arcM[lo] + (shape.arcM[hi] - shape.arcM[lo]) * f);
}

/** The point `arcM` meters along the drawn line. */
export function pointOnLine(line: DrawnLine, arcM: number): [number, number] {
  const cum = line.cumulativeM;
  const c = line.coordinates;
  if (arcM <= 0) {
    return c[0];
  }
  if (arcM >= cum[cum.length - 1]) {
    return c[c.length - 1];
  }
  let lo = 0;
  let hi = cum.length - 1;
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (cum[mid] <= arcM) {
      lo = mid;
    } else {
      hi = mid;
    }
  }
  const span = cum[hi] - cum[lo];
  const f = span > 0 ? (arcM - cum[lo]) / span : 0;
  return [c[lo][0] + (c[hi][0] - c[lo][0]) * f, c[lo][1] + (c[hi][1] - c[lo][1]) * f];
}

interface Projection {
  arcM: number;
  offsetM: number;
}

/** Nearest point on the line among segments that overlap `[minArc, maxArc]`. */
function project(
  [px, py]: [number, number],
  lineXy: [number, number][],
  cum: number[],
  minArc: number,
  maxArc: number,
): Projection | null {
  let best: Projection | null = null;
  for (let j = 0; j < lineXy.length - 1; j++) {
    if (cum[j + 1] < minArc || cum[j] > maxArc) {
      continue;
    }
    const [ax, ay] = lineXy[j];
    const [bx, by] = lineXy[j + 1];
    const dx = bx - ax;
    const dy = by - ay;
    const lengthSq = dx * dx + dy * dy;
    const t =
      lengthSq === 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / lengthSq));
    const offsetM = Math.hypot(px - ax - t * dx, py - ay - t * dy);
    if (!best || offsetM < best.offsetM) {
      best = { arcM: cum[j] + t * (cum[j + 1] - cum[j]), offsetM };
    }
  }
  return best;
}

/** Equirectangular projection to meters around `lat0`. Accurate to well under 1 % across a city. */
function localMeters(lat0: number): (coordinate: [number, number]) => [number, number] {
  const mPerDegLon = M_PER_DEG_LON_EQUATOR * Math.cos((lat0 * Math.PI) / 180);
  return ([lon, lat]) => [lon * mPerDegLon, lat * M_PER_DEG_LAT];
}
