import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL } from '../core/api';
import type { TrainsResponse, TripShape } from './trains';

/** Minutes of timetable per request. Polling every minute keeps 9 or more minutes ahead. */
export const TRAINS_WINDOW_MINUTES = 10;

interface ShapeResponse {
  geometry: { coordinates: [number, number][] };
  properties: { shapeId: string; distancesM: number[] };
}

@Service()
export class TrainsApi {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);
  private readonly shapes = new Map<string, Promise<TripShape>>();

  trains(): Promise<TrainsResponse> {
    return firstValueFrom(
      this.http.get<TrainsResponse>(`${this.baseUrl}/api/trains`, {
        params: { minutes: TRAINS_WINDOW_MINUTES },
      }),
    );
  }

  /** Each shape is requested once per page load. The browser caches it for a day. */
  shape(shapeId: string): Promise<TripShape> {
    let shape = this.shapes.get(shapeId);
    if (!shape) {
      shape = firstValueFrom(
        this.http.get<ShapeResponse>(`${this.baseUrl}/api/shapes/${encodeURIComponent(shapeId)}`),
      ).then((response) => ({
        coordinates: response.geometry.coordinates,
        distancesM: response.properties.distancesM,
      }));
      // A failed request may be retried on the next poll.
      shape.catch(() => this.shapes.delete(shapeId));
      this.shapes.set(shapeId, shape);
    }
    return shape;
  }
}
