package com.vladdancea.viennapulse;

import static org.assertj.core.api.Assertions.assertThat;

import com.vladdancea.viennapulse.gtfs.GtfsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ViennaPulseBackendApplicationTests {

	@Autowired
	private GtfsProperties gtfs;

	@Test
	void contextLoads() {
	}

	@Test
	void resolvesTheGtfsWorkDir() {
		assertThat(gtfs.workDir()).isAbsolute();
		assertThat(gtfs.workDir().toString()).doesNotContain("${").endsWith("vienna-pulse/gtfs");
	}

}
