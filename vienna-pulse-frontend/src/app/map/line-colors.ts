/**
 * Line colors for the light basemap. Close to the official Wiener Linien colors, but more
 * saturated so the glow stays visible on light gray.
 */
const NEON: Readonly<Record<string, string>> = {
  U1: '#ff1f4b',
  U2: '#b026ff',
  U3: '#ff7a00',
  U4: '#00c060',
  U5: '#00a7b5',
  U6: '#c28a2c',
};

const FALLBACK = '#52525b';

export function lineColor(line: string, official: string | null = null): string {
  return NEON[line] ?? official ?? FALLBACK;
}
