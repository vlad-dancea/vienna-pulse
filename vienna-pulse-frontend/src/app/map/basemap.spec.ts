import type { StyleSpecification } from 'maplibre-gl';
import { loadBasemapStyle, POSITRON_STYLE_URL, withoutSymbols } from './basemap';

const STYLE: StyleSpecification = {
  version: 8,
  sources: {},
  layers: [
    { id: 'background', type: 'background' },
    { id: 'water', type: 'fill', source: 'openmaptiles', 'source-layer': 'water' },
    { id: 'label_city', type: 'symbol', source: 'openmaptiles', 'source-layer': 'place' },
    {
      id: 'highway-shield',
      type: 'symbol',
      source: 'openmaptiles',
      'source-layer': 'transportation_name',
    },
  ],
};

describe('basemap', () => {
  it('drops every symbol layer and keeps the rest in order', () => {
    expect(withoutSymbols(STYLE).layers.map((layer) => layer.id)).toEqual(['background', 'water']);
  });

  it('loads the Positron style without symbols', async () => {
    const fetchFn = vi.fn(async () => new Response(JSON.stringify(STYLE)));

    const style = await loadBasemapStyle(fetchFn as unknown as typeof fetch);

    expect(fetchFn).toHaveBeenCalledWith(POSITRON_STYLE_URL);
    expect(style.layers).toHaveLength(2);
  });

  it('fails when the style cannot be loaded', async () => {
    const fetchFn = vi.fn(async () => new Response('down', { status: 503 }));

    await expect(loadBasemapStyle(fetchFn as unknown as typeof fetch)).rejects.toThrow('HTTP 503');
  });
});
