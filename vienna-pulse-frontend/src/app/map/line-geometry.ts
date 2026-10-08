/** Response of `GET /api/lines`: a GeoJSON FeatureCollection, one LineString per line and direction. */
export interface LineGeometry {
  type: 'FeatureCollection';
  serviceDate: string;
  feedVersion: number;
  features: LineFeature[];
}

export interface LineFeature {
  type: 'Feature';
  geometry: { type: 'LineString'; coordinates: [number, number][] };
  properties: LineProperties;
}

export interface LineProperties {
  /** Route short name, e.g. U2. */
  line: string;
  directionId: 0 | 1;
  /** Official color as #RRGGBB, null when the feed has none. */
  color: string | null;
  shapeId: string;
  lengthM: number;
}
