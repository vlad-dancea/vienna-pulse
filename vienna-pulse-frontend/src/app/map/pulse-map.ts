import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { type GeoJSONSource, Map as MapLibreMap, setWorkerUrl } from 'maplibre-gl';
import { loadBasemapStyle } from './basemap';
import { LinesApi } from './lines-api';
import { boundsOf, LINE_LAYERS, LINES_SOURCE, linesForMap } from './map-layers';
import { TrainLayer } from './train-layer';
import type { TrainsResponse, TripShape } from './trains';
import { TrainsApi } from './trains-api';

/** Served from our own origin (angular.json assets), MapLibre workers must be same-origin. */
setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');

const VIENNA: [number, number] = [16.372, 48.208];
const POLL_INTERVAL_MS = 60_000;
/** The train count is refreshed this often, independent of the animation. */
const SUMMARY_INTERVAL_MS = 10_000;

/** Full-screen map of Vienna with the U-Bahn lines and moving trains. Lazy-loaded. */
@Component({
  selector: 'app-pulse-map',
  host: { class: 'relative block h-dvh w-full bg-[#f2f1ef]' },
  template: `
    <div
      #container
      class="h-full w-full"
      role="region"
      aria-label="Map of the Vienna U-Bahn network"
    ></div>

    <section
      class="bg-background/90 text-foreground absolute top-4 left-4 rounded-md px-3 py-2 text-sm shadow"
      aria-label="Trains"
    >
      <p class="font-medium">{{ summary() }}</p>
      <p class="text-muted-foreground text-xs">Planned positions from the timetable</p>
    </section>

    @if (problem(); as message) {
      <p
        class="bg-background/90 text-foreground absolute top-4 left-1/2 -translate-x-1/2 rounded-md px-3 py-1.5 text-sm shadow"
        role="alert"
      >
        {{ message }}
      </p>
    }
  `,
})
export class PulseMap {
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private readonly linesApi = inject(LinesApi);
  private readonly trainsApi = inject(TrainsApi);
  private readonly map = signal<MapLibreMap | null>(null);
  private readonly basemapFailed = signal(false);
  private readonly trains = signal<TrainsResponse | null>(null);
  private readonly trainsFailed = signal(false);
  private readonly shapes = signal<ReadonlyMap<string, TripShape>>(new Map());
  private readonly visibleTrains = signal<number | null>(null);
  private trainLayer: TrainLayer | null = null;

  protected readonly summary = computed(() => {
    const count = this.visibleTrains();
    if (count === null) {
      return 'Loading trains…';
    }
    return count === 1 ? '1 train on the move' : `${count} trains on the move`;
  });

  protected readonly problem = computed(() => {
    if (this.basemapFailed()) {
      return 'The basemap could not be loaded.';
    }
    if (this.linesApi.lines.error()) {
      return 'The U-Bahn lines are not available right now.';
    }
    if (this.trainsFailed()) {
      return 'Train positions are not available right now.';
    }
    return null;
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      let map: MapLibreMap | null = null;
      let destroyed = false;
      const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

      const poll = setInterval(() => this.loadTrains(), POLL_INTERVAL_MS);
      const summary = setInterval(() => this.updateSummary(), SUMMARY_INTERVAL_MS);
      const onVisible = () => {
        // Hidden tabs may have skipped polls. Positions themselves come from the clock.
        if (document.visibilityState === 'visible') {
          this.loadTrains();
        }
      };
      document.addEventListener('visibilitychange', onVisible);

      destroyRef.onDestroy(() => {
        destroyed = true;
        clearInterval(poll);
        clearInterval(summary);
        document.removeEventListener('visibilitychange', onVisible);
        this.trainLayer?.stop();
        map?.remove();
      });

      this.loadTrains();
      loadBasemapStyle()
        .then((style) => {
          if (destroyed) {
            return;
          }
          map = new MapLibreMap({
            container: this.container().nativeElement,
            style,
            center: VIENNA,
            zoom: 11.5,
            minZoom: 9,
            maxZoom: 17,
            attributionControl: {
              compact: true,
              customAttribution: 'Timetable: Datenquelle Stadt Wien, data.wien.gv.at',
            },
          });
          const loaded = map;
          loaded.on('load', () => {
            this.trainLayer = new TrainLayer(loaded, reducedMotion);
            this.trainLayer.start();
            this.map.set(loaded);
          });
        })
        .catch(() => this.basemapFailed.set(true));
    });

    // Lines: drawn once map and data are ready, below the trains.
    effect(() => {
      const map = this.map();
      const lines = this.linesApi.lines.hasValue() ? this.linesApi.lines.value() : undefined;
      if (!map || !lines) {
        return;
      }
      const data = linesForMap(lines);
      const source = map.getSource<GeoJSONSource>(LINES_SOURCE);
      if (source) {
        source.setData(data);
        return;
      }
      map.addSource(LINES_SOURCE, { type: 'geojson', data });
      LINE_LAYERS.forEach((layer) => map.addLayer(layer, 'train-halo'));
      const bounds = boundsOf(data);
      if (bounds) {
        map.fitBounds(bounds, { padding: 48, duration: 0 });
      }
    });

    // Trains: hand new data and shapes to the layer.
    effect(() => {
      const map = this.map();
      const response = this.trains();
      const shapes = this.shapes();
      if (!map || !response || !this.trainLayer) {
        return;
      }
      const clockOffsetS = response.serverTime - Date.now() / 1000;
      this.trainLayer.update(response.trains, shapes, clockOffsetS);
      this.updateSummary();
    });
  }

  private loadTrains(): void {
    this.trainsApi
      .trains()
      .then((response) => {
        this.trainsFailed.set(false);
        this.trains.set(response);
        this.loadShapes(response);
      })
      .catch(() => this.trainsFailed.set(true));
  }

  private loadShapes(response: TrainsResponse): void {
    const known = this.shapes();
    const missing = [...new Set(response.trains.map((train) => train.shapeId))].filter(
      (id) => !known.has(id),
    );
    for (const shapeId of missing) {
      this.trainsApi
        .shape(shapeId)
        .then((shape) => this.shapes.update((shapes) => new Map(shapes).set(shapeId, shape)))
        .catch(() => {
          // That shape's trains stay hidden. The next poll retries.
        });
    }
  }

  private updateSummary(): void {
    if (this.trainLayer && this.trains()) {
      this.visibleTrains.set(this.trainLayer.visibleCount());
    }
  }
}
