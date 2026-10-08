import {
  afterNextRender,
  Component,
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

/** Served from our own origin (angular.json assets), MapLibre workers must be same-origin. */
setWorkerUrl('/maplibre/maplibre-gl-worker.mjs');

const VIENNA: [number, number] = [16.372, 48.208];

/** Full-screen map of Vienna with the U-Bahn lines. Lazy-loaded, MapLibre is about 1 MB. */
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
    @if (status(); as message) {
      <p
        class="bg-background/90 text-foreground absolute top-4 left-1/2 -translate-x-1/2 rounded-md px-3 py-1.5 text-sm shadow"
        role="status"
      >
        {{ message }}
      </p>
    }
  `,
})
export class PulseMap {
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('container');
  private readonly linesApi = inject(LinesApi);
  private readonly map = signal<MapLibreMap | null>(null);
  private readonly basemapFailed = signal(false);

  protected readonly status = () => {
    if (this.basemapFailed()) {
      return 'The basemap could not be loaded.';
    }
    if (this.linesApi.lines.error()) {
      return 'The U-Bahn lines are not available right now.';
    }
    if (this.linesApi.lines.isLoading()) {
      return 'Loading the U-Bahn lines…';
    }
    return null;
  };

  constructor() {
    const destroyRef = inject(DestroyRef);

    afterNextRender(() => {
      let map: MapLibreMap | null = null;
      let destroyed = false;
      destroyRef.onDestroy(() => {
        destroyed = true;
        map?.remove();
      });
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
          loaded.on('load', () => this.map.set(loaded));
        })
        .catch(() => this.basemapFailed.set(true));
    });

    // Draw the lines once both the map and the data are ready.
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
      LINE_LAYERS.forEach((layer) => map.addLayer(layer));
      const bounds = boundsOf(data);
      if (bounds) {
        map.fitBounds(bounds, { padding: 48, duration: 0 });
      }
    });
  }
}
