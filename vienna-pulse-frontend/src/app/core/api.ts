import { InjectionToken } from '@angular/core';

/**
 * Base URL of the Vienna Pulse API. Local development also talks to production,
 * the API allows http://localhost:4200 through CORS.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  factory: () => 'https://api.pulse.vladdancea.com',
});
