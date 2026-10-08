import type { StyleSpecification } from 'maplibre-gl';

/** OpenFreeMap Positron: free, no API key, light gray. */
export const POSITRON_STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';

/**
 * Removes every symbol layer (labels, road shields, place markers, icons), so only land,
 * water, parks and streets remain and the U-Bahn lines carry the picture.
 */
export function withoutSymbols(style: StyleSpecification): StyleSpecification {
  return { ...style, layers: style.layers.filter((layer) => layer.type !== 'symbol') };
}

/** Loads the Positron style without its symbol layers. */
export async function loadBasemapStyle(fetchFn: typeof fetch = fetch): Promise<StyleSpecification> {
  const response = await fetchFn(POSITRON_STYLE_URL);
  if (!response.ok) {
    throw new Error(`Basemap style failed with HTTP ${response.status}`);
  }
  return withoutSymbols((await response.json()) as StyleSpecification);
}
