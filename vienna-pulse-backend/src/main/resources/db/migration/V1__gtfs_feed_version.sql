-- One row per GTFS feed the backend has downloaded. Exactly one imported
-- version is active and serves the map. ETag and Last-Modified make the next
-- download conditional, so an unchanged feed costs a 304.
CREATE TABLE gtfs_feed_version (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sha256        char(64)    NOT NULL UNIQUE,
    size_bytes    bigint      NOT NULL CHECK (size_bytes > 0),
    etag          text,
    last_modified timestamptz,
    downloaded_at timestamptz NOT NULL DEFAULT now(),
    imported_at   timestamptz,
    active        boolean     NOT NULL DEFAULT false,
    CONSTRAINT gtfs_feed_version_active_is_imported CHECK (NOT active OR imported_at IS NOT NULL)
);

-- At most one active version.
CREATE UNIQUE INDEX gtfs_feed_version_single_active ON gtfs_feed_version (active) WHERE active;
