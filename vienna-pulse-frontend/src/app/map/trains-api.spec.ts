import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TrainsApi } from './trains-api';

describe('TrainsApi', () => {
  let api: TrainsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TrainsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a ten minute window', async () => {
    const trains = api.trains();
    http
      .expectOne('https://api.pulse.vladdancea.com/api/trains?minutes=10')
      .flush({ serverTime: 1, from: 1, to: 601, feedVersion: 1, trains: [] });

    expect((await trains).to).toBe(601);
  });

  it('loads each shape once and encodes its id', async () => {
    const first = api.shape('21-U2-j26-8.10.R');
    const second = api.shape('21-U2-j26-8.10.R');
    http.expectOne('https://api.pulse.vladdancea.com/api/shapes/21-U2-j26-8.10.R').flush({
      geometry: { coordinates: [[16.1, 48.1]] },
      properties: { shapeId: '21-U2-j26-8.10.R', distancesM: [0] },
    });

    expect(await first).toEqual({ coordinates: [[16.1, 48.1]], distancesM: [0] });
    expect(await second).toBe(await first);
  });

  it('retries a shape after a failed request', async () => {
    const failed = api.shape('s1');
    http
      .expectOne('https://api.pulse.vladdancea.com/api/shapes/s1')
      .flush('down', { status: 503, statusText: 'Down' });
    await expect(failed).rejects.toBeTruthy();

    api.shape('s1');
    http.expectOne('https://api.pulse.vladdancea.com/api/shapes/s1');
  });
});
