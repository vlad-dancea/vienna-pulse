-- GTFS timetable tables. Every row belongs to one gtfs_feed_version, so a new
-- feed is imported next to the active one and activated in one transaction.
-- Integer keys (trip_key, stop_key) are assigned by the importer per version.
-- The original GTFS ids are kept as unique columns.
-- Times are seconds after the GTFS service day origin (noon minus 12 hours,
-- local time), so trips after midnight have values above 86400.

CREATE TABLE agency (
    feed_version_id bigint NOT NULL
        REFERENCES gtfs_feed_version(id) ON DELETE CASCADE,
    agency_id text NOT NULL,
    name text NOT NULL,
    timezone text NOT NULL,
    PRIMARY KEY (feed_version_id, agency_id)
);

CREATE TABLE route (
    feed_version_id bigint NOT NULL,
    route_id text NOT NULL,
    agency_id text NOT NULL,
    short_name text NOT NULL,
    long_name text,
    route_type smallint NOT NULL CHECK (route_type >= 0),
    color text CHECK (color ~ '^[0-9A-Fa-f]{6}$'),
    text_color text CHECK (text_color ~ '^[0-9A-Fa-f]{6}$'),
    PRIMARY KEY (feed_version_id, route_id),
    FOREIGN KEY (feed_version_id, agency_id)
        REFERENCES agency(feed_version_id, agency_id) ON DELETE CASCADE
);

-- Supports agency deletion and line matching within a mapped agency
CREATE INDEX route_by_name
    ON route (feed_version_id, agency_id, short_name);

CREATE TABLE stop (
    feed_version_id bigint NOT NULL
        REFERENCES gtfs_feed_version(id) ON DELETE CASCADE,
    stop_key integer NOT NULL CHECK (stop_key > 0),
    stop_id text NOT NULL,
    station_ref text NOT NULL,
    name text NOT NULL,
    lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon double precision NOT NULL CHECK (lon BETWEEN -180 AND 180),
    PRIMARY KEY (feed_version_id, stop_key),
    UNIQUE (feed_version_id, stop_id)
);

-- Station references preserve namespaces such as at:49:282 and at:43:3134
CREATE INDEX stop_by_station
    ON stop (feed_version_id, station_ref);

CREATE TABLE shape (
    feed_version_id bigint NOT NULL
        REFERENCES gtfs_feed_version(id) ON DELETE CASCADE,
    shape_id text NOT NULL,
    lats double precision[] NOT NULL,
    lons double precision[] NOT NULL,
    dists_m double precision[] NOT NULL,
    length_m double precision NOT NULL
        CHECK (length_m >= 0 AND length_m < 'Infinity'::double precision),
    PRIMARY KEY (feed_version_id, shape_id),
    CHECK (
        cardinality(lats) >= 2
        AND cardinality(lats) = cardinality(lons)
        AND cardinality(lats) = cardinality(dists_m)
    ),
    CHECK (
        array_ndims(lats) = 1
        AND array_ndims(lons) = 1
        AND array_ndims(dists_m) = 1
        AND array_lower(lats, 1) = 1
        AND array_lower(lons, 1) = 1
        AND array_lower(dists_m, 1) = 1
    ),
    CHECK (
        array_position(lats, NULL::double precision) IS NULL
        AND array_position(lons, NULL::double precision) IS NULL
        AND array_position(dists_m, NULL::double precision) IS NULL
    ),
    CHECK (length_m = dists_m[cardinality(dists_m)])
);

-- Importer validates finite elements and coordinate ranges
-- Arrays share shape point sequence order and distances never decrease

CREATE TABLE service (
    feed_version_id bigint NOT NULL
        REFERENCES gtfs_feed_version(id) ON DELETE CASCADE,
    service_id text NOT NULL,
    PRIMARY KEY (feed_version_id, service_id)
);

-- A service may exist without a weekly calendar row
-- Weekday bits run from Monday on the left to Sunday on the right
CREATE TABLE service_calendar (
    feed_version_id bigint NOT NULL,
    service_id text NOT NULL,
    weekdays bit(7) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL CHECK (end_date >= start_date),
    PRIMARY KEY (feed_version_id, service_id),
    FOREIGN KEY (feed_version_id, service_id)
        REFERENCES service(feed_version_id, service_id) ON DELETE CASCADE
);

-- Contains final operating dates after applying additions and removals
CREATE TABLE service_date (
    feed_version_id bigint NOT NULL,
    service_id text NOT NULL,
    service_date date NOT NULL,
    PRIMARY KEY (feed_version_id, service_date, service_id),
    FOREIGN KEY (feed_version_id, service_id)
        REFERENCES service(feed_version_id, service_id) ON DELETE CASCADE
);

CREATE INDEX service_date_by_service
    ON service_date (feed_version_id, service_id);

CREATE TABLE trip (
    feed_version_id bigint NOT NULL,
    trip_key integer NOT NULL CHECK (trip_key > 0),
    trip_id text NOT NULL,
    route_id text NOT NULL,
    service_id text NOT NULL,
    shape_id text NOT NULL,
    direction_id smallint NOT NULL CHECK (direction_id IN (0, 1)),
    headsign text,
    first_departure_s integer NOT NULL CHECK (first_departure_s >= 0),
    last_arrival_s integer NOT NULL
        CHECK (last_arrival_s >= first_departure_s),
    PRIMARY KEY (feed_version_id, trip_key),
    UNIQUE (feed_version_id, trip_id),
    FOREIGN KEY (feed_version_id, route_id)
        REFERENCES route(feed_version_id, route_id) ON DELETE CASCADE,
    FOREIGN KEY (feed_version_id, service_id)
        REFERENCES service(feed_version_id, service_id) ON DELETE CASCADE,
    FOREIGN KEY (feed_version_id, shape_id)
        REFERENCES shape(feed_version_id, shape_id) ON DELETE CASCADE
);

-- Supports active service lookup and the service foreign key
CREATE INDEX trip_by_service
    ON trip (feed_version_id, service_id, first_departure_s);

CREATE INDEX trip_by_route
    ON trip (feed_version_id, route_id);

CREATE INDEX trip_by_shape
    ON trip (feed_version_id, shape_id);

-- No foreign keys on this bulk table
-- Importer validates version existence and version scoped trip and stop keys
-- Importer validates increasing sequences and nondecreasing times and distances
CREATE TABLE stop_time (
    feed_version_id bigint NOT NULL,
    trip_key integer NOT NULL CHECK (trip_key > 0),
    stop_sequence integer NOT NULL CHECK (stop_sequence >= 0),
    stop_key integer NOT NULL CHECK (stop_key > 0),
    arrival_s integer NOT NULL CHECK (arrival_s >= 0),
    departure_s integer NOT NULL CHECK (departure_s >= arrival_s),
    dist_m double precision NOT NULL
        CHECK (dist_m >= 0 AND dist_m < 'Infinity'::double precision),
    pickup_type smallint NOT NULL DEFAULT 0
        CHECK (pickup_type BETWEEN 0 AND 3),
    drop_off_type smallint NOT NULL DEFAULT 0
        CHECK (drop_off_type BETWEEN 0 AND 3),
    PRIMARY KEY (feed_version_id, trip_key, stop_sequence)
);

-- Supports planned departure windows at a mapped platform
CREATE INDEX stop_time_by_stop
    ON stop_time (feed_version_id, stop_key, departure_s);

-- Retention must delete stop_time rows before deleting their feed version
-- Execute both deletions in one transaction under the updater lock
-- Do not delete individual trips or stops from an imported version
-- Run ANALYZE after each successful bulk import
