package com.vladdancea.viennapulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts the same database image as production (deploy/compose.yaml) and
 * wires the DataSource to it. Used by the tests and by {@code ./gradlew bootTestRun}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// Keep in sync with the db image in deploy/compose.yaml.
	static final DockerImageName DB_IMAGE = DockerImageName.parse("timescale/timescaledb:2.30.2-pg18")
		.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DB_IMAGE);
	}

}
