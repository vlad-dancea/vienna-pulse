import type { LineGeometry } from './line-geometry';
import { linesForMap } from './map-layers';
import { type DrawnLine, drawnLine, snappedPointAt, snapShape } from './snap';
import type { TrainPath } from './train-layer';
import { pointAt } from './train-motion';
import type { Train, TripShape } from './trains';

/**
 * Builds a path per trip shape. Shapes of a drawn line are snapped onto it, so trains of
 * both directions sit exactly on the line. Shapes that cannot be snapped (no drawn line,
 * or a detour away from it) keep their own geometry.
 *
 * @param cache keeps earlier results, snapping runs once per shape and line
 */
export function trainPaths(
  lines: LineGeometry | undefined,
  trains: readonly Train[],
  shapes: ReadonlyMap<string, TripShape>,
  cache: Map<string, TrainPath>,
): ReadonlyMap<string, TrainPath> {
  const drawn = new Map<string, DrawnLine>();
  for (const feature of lines ? linesForMap(lines).features : []) {
    drawn.set(feature.properties.line, drawnLine(feature.geometry.coordinates));
  }
  const lineOfShape = new Map(trains.map((train) => [train.shapeId, train.line]));
  const paths = new Map<string, TrainPath>();
  for (const [shapeId, shape] of shapes) {
    const line = lineOfShape.get(shapeId);
    const key = `${lines?.serviceDate ?? 'none'}/${line}/${shapeId}`;
    let path = cache.get(key);
    if (!path) {
      const target = line ? drawn.get(line) : undefined;
      const snapped = target ? snapShape(shape, target) : null;
      path = snapped ? (distM) => snappedPointAt(snapped, distM) : (distM) => pointAt(shape, distM);
      cache.set(key, path);
    }
    paths.set(shapeId, path);
  }
  return paths;
}
