import { httpResource } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { API_BASE_URL } from '../core/api';
import type { LineGeometry } from './line-geometry';

/** Line geometry of today's service day. The browser caches it (ETag, one hour). */
@Service()
export class LinesApi {
  private readonly baseUrl = inject(API_BASE_URL);

  readonly lines = httpResource<LineGeometry>(() => `${this.baseUrl}/api/lines`);
}
