package com.vladdancea.viennapulse.gtfs;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Access to {@code gtfs_feed_version}. */
@Repository
class GtfsFeedVersionRepository {

	private final JdbcClient jdbc;

	GtfsFeedVersionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	Optional<FeedVersion> findActive() {
		return jdbc.sql("SELECT id, sha256, etag, last_modified FROM gtfs_feed_version WHERE active")
			.query((rs, row) -> {
				Timestamp lastModified = rs.getTimestamp("last_modified");
				return new FeedVersion(rs.getLong("id"), rs.getString("sha256"), new FeedValidators(
						lastModified == null ? null : lastModified.toInstant(), rs.getString("etag")));
			})
			.optional();
	}

	/**
	 * Records a downloaded feed. A feed whose earlier import failed has the same checksum,
	 * so its row is reused with fresh validators.
	 */
	long saveDownloaded(Downloaded feed) {
		return jdbc.sql("""
				INSERT INTO gtfs_feed_version (sha256, size_bytes, etag, last_modified)
				VALUES (:sha256, :size, :etag, :lastModified)
				ON CONFLICT (sha256) DO UPDATE
				SET etag = excluded.etag, last_modified = excluded.last_modified, downloaded_at = now()
				RETURNING id
				""")
			.param("sha256", feed.sha256())
			.param("size", feed.sizeBytes())
			.param("etag", feed.validators().etag())
			.param("lastModified", timestamp(feed.validators()))
			.query(Long.class)
			.single();
	}

	/** Stores new validators for a version, used when the server re-sends identical content. */
	void updateValidators(long id, FeedValidators validators) {
		jdbc.sql("UPDATE gtfs_feed_version SET etag = :etag, last_modified = :lastModified WHERE id = :id")
			.param("etag", validators.etag())
			.param("lastModified", timestamp(validators))
			.param("id", id)
			.update();
	}

	/** Makes {@code id} the only active version. Call inside the import transaction. */
	void activate(long id) {
		jdbc.sql("UPDATE gtfs_feed_version SET active = false WHERE active").update();
		jdbc.sql("UPDATE gtfs_feed_version SET active = true, imported_at = now() WHERE id = :id")
			.param("id", id)
			.update();
	}

	/**
	 * Deletes imported versions except the active one and the newest inactive one (kept as a
	 * fallback). {@code stop_time} has no foreign keys, so its rows are deleted explicitly.
	 *
	 * @return the deleted version ids
	 */
	List<Long> deleteOldVersions() {
		List<Long> old = jdbc.sql("""
				SELECT id FROM gtfs_feed_version
				WHERE NOT active AND imported_at IS NOT NULL
				ORDER BY imported_at DESC, id DESC
				OFFSET 1
				""").query(Long.class).list();
		if (!old.isEmpty()) {
			jdbc.sql("DELETE FROM stop_time WHERE feed_version_id IN (:ids)").param("ids", old).update();
			jdbc.sql("DELETE FROM gtfs_feed_version WHERE id IN (:ids)").param("ids", old).update();
		}
		return old;
	}

	private static Timestamp timestamp(FeedValidators validators) {
		return validators.lastModified() == null ? null : Timestamp.from(validators.lastModified());
	}

	record FeedVersion(long id, String sha256, FeedValidators validators) {
	}

}
