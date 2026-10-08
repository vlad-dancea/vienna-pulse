import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LinesApi } from './lines-api';

describe('LinesApi', () => {
  it('loads the lines from the API', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const api = TestBed.inject(LinesApi);
    const http = TestBed.inject(HttpTestingController);
    TestBed.tick();

    http.expectOne('https://api.pulse.vladdancea.com/api/lines').flush({
      type: 'FeatureCollection',
      serviceDate: '2026-10-08',
      feedVersion: 1,
      features: [],
    });
    await TestBed.inject(ApplicationRef).whenStable();

    expect(api.lines.value()?.serviceDate).toBe('2026-10-08');
    http.verify();
  });
});
