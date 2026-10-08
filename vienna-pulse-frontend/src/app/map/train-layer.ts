import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl';
import { lineColor } from './line-colors';
import { distanceAt, isOnMap, pointAt } from './train-motion';
import type { Train, TripShape } from './trains';

const SOURCE = 'trains';

interface TrainFeature {
  type: 'Feature';
  id: string;
  geometry: { type: 'Point'; coordinates: [number, number] };
  properties: { line: string; headsign: string | null; neon: string };
}
/** About 30 updates per second are smooth for trains and cheap for MapLibre. */
const FRAME_INTERVAL_MS = 33;
/** With reduced motion, positions jump once per second instead of gliding. */
const REDUCED_MOTION_INTERVAL_MS = 1000;

/**
 * Draws the trains as circles and moves them along their shapes. Positions come from the
 * clock alone, so a tab that was hidden shows the right positions on its first new frame.
 */
export class TrainLayer {
  private trains: readonly Train[] = [];
  private shapes: ReadonlyMap<string, TripShape> = new Map();
  private clockOffsetS = 0;
  private frameId: number | null = null;
  private lastDraw = 0;

  constructor(
    private readonly map: MapLibreMap,
    private readonly reducedMotion: boolean,
  ) {
    map.addSource(SOURCE, { type: 'geojson', data: { type: 'FeatureCollection', features: [] } });
    map.addLayer({
      id: 'train-halo',
      type: 'circle',
      source: SOURCE,
      paint: {
        'circle-radius': ['interpolate', ['linear'], ['zoom'], 10, 6, 15, 14],
        'circle-color': ['get', 'neon'],
        'circle-blur': 0.8,
        'circle-opacity': 0.55,
      },
    });
    map.addLayer({
      id: 'train-core',
      type: 'circle',
      source: SOURCE,
      paint: {
        'circle-radius': ['interpolate', ['linear'], ['zoom'], 10, 3, 15, 7],
        'circle-color': '#ffffff',
        'circle-stroke-color': ['get', 'neon'],
        'circle-stroke-width': ['interpolate', ['linear'], ['zoom'], 10, 1.5, 15, 3],
      },
    });
  }

  /**
   * @param clockOffsetS server time minus browser time, corrects a wrong local clock
   */
  update(
    trains: readonly Train[],
    shapes: ReadonlyMap<string, TripShape>,
    clockOffsetS: number,
  ): void {
    this.trains = trains;
    this.shapes = shapes;
    this.clockOffsetS = clockOffsetS;
    this.draw();
  }

  start(): void {
    if (this.frameId === null) {
      this.frameId = requestAnimationFrame(this.frame);
    }
  }

  stop(): void {
    if (this.frameId !== null) {
      cancelAnimationFrame(this.frameId);
      this.frameId = null;
    }
  }

  /** Trains currently drawn, for the summary shown to users. */
  visibleCount(): number {
    const now = this.now();
    return this.trains.filter(
      (train) => this.shapes.has(train.shapeId) && isOnMap(train.stops, now),
    ).length;
  }

  private readonly frame = (time: number) => {
    const interval = this.reducedMotion ? REDUCED_MOTION_INTERVAL_MS : FRAME_INTERVAL_MS;
    if (time - this.lastDraw >= interval) {
      this.lastDraw = time;
      this.draw();
    }
    this.frameId = requestAnimationFrame(this.frame);
  };

  private now(): number {
    return Date.now() / 1000 + this.clockOffsetS;
  }

  private draw(): void {
    const now = this.now();
    const features: TrainFeature[] = [];
    for (const train of this.trains) {
      const shape = this.shapes.get(train.shapeId);
      if (!shape || !isOnMap(train.stops, now)) {
        continue;
      }
      features.push({
        type: 'Feature',
        id: train.id,
        geometry: { type: 'Point', coordinates: pointAt(shape, distanceAt(train.stops, now)) },
        properties: { line: train.line, headsign: train.headsign, neon: lineColor(train.line) },
      });
    }
    this.map.getSource<GeoJSONSource>(SOURCE)?.setData({ type: 'FeatureCollection', features });
  }
}
