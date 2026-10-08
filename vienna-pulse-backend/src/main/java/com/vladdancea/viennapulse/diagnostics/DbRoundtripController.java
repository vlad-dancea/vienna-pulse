package com.vladdancea.viennapulse.diagnostics;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TEMPORARY: proves the browser to API to database path with one insert and
 * one delete. Uses a temporary table, so nothing stays in the database.
 * Remove once the first real endpoint exists.
 */
@RestController
class DbRoundtripController {

	private final JdbcClient jdbc;

	DbRoundtripController(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@PostMapping("/api/diagnostics/db-roundtrip")
	@Transactional
	DbRoundtrip roundtrip() {
		long start = System.nanoTime();
		jdbc.sql("CREATE TEMPORARY TABLE connection_check (id int GENERATED ALWAYS AS IDENTITY, note text) ON COMMIT DROP")
			.update();
		int inserted = jdbc.sql("INSERT INTO connection_check (note) VALUES ('hello from the frontend')").update();
		int deleted = jdbc.sql("DELETE FROM connection_check").update();
		String database = jdbc.sql("SELECT 'PostgreSQL ' || current_setting('server_version')").query(String.class).single();
		return new DbRoundtrip(inserted, deleted, database, (System.nanoTime() - start) / 1_000_000.0);
	}

	record DbRoundtrip(int inserted, int deleted, String database, double durationMs) {
	}

}
