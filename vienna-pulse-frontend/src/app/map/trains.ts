/** Response of `GET /api/trains`. All times are Unix epoch seconds. */
export interface TrainsResponse {
  /** When the server built the response, used to correct the browser clock. */
  serverTime: number;
  from: number;
  to: number;
  feedVersion: number;
  trains: Train[];
}

export interface Train {
  /** `<service date>/<trip_id>`, stable for one run. */
  id: string;
  serviceDate: string;
  line: string;
  directionId: 0 | 1;
  headsign: string | null;
  shapeId: string;
  /** The stops around the requested window, in trip order. */
  stops: TrainStop[];
}

export interface TrainStop {
  stopId: string;
  name: string;
  arrival: number;
  departure: number;
  /** Distance along the trip's shape in meters. */
  distM: number;
}

/** A trip shape from `GET /api/shapes/{id}`, reduced to what the animation needs. */
export interface TripShape {
  coordinates: [number, number][];
  /** `distancesM[i]` is the distance of `coordinates[i]` from the start of the shape. */
  distancesM: number[];
}
