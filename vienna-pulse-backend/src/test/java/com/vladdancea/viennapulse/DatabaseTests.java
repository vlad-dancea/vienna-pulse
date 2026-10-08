package com.vladdancea.viennapulse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseTests {

	@Autowired
	private JdbcClient jdbc;

	@Test
	void runsOnPostgres18() {
		Integer major = jdbc.sql("SELECT current_setting('server_version_num')::int / 10000").query(Integer.class).single();
		assertThat(major).isEqualTo(18);
	}

	@Test
	void timescaleDbIsAvailable() {
		assertThat(jdbc.sql("SELECT count(*) FROM pg_available_extensions WHERE name = 'timescaledb'")
			.query(Long.class)
			.single()).isEqualTo(1L);
	}

}
