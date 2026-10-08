package com.vladdancea.viennapulse.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import com.vladdancea.viennapulse.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class DbRoundtripControllerTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void insertsAndDeletesOneRow() {
		var result = mvc.post().uri("/api/diagnostics/db-roundtrip").exchange();
		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.inserted").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.deleted").isEqualTo(1);
	}

	@Test
	void allowsTheFrontendOrigin() {
		assertThat(mvc.options()
			.uri("/api/diagnostics/db-roundtrip")
			.header("Origin", "https://pulse.vladdancea.com")
			.header("Access-Control-Request-Method", "POST"))
			.hasStatusOk()
			.hasHeader("Access-Control-Allow-Origin", "https://pulse.vladdancea.com");
	}

	@Test
	void rejectsOtherOrigins() {
		assertThat(mvc.options()
			.uri("/api/diagnostics/db-roundtrip")
			.header("Origin", "https://evil.example")
			.header("Access-Control-Request-Method", "POST"))
			.hasStatus(403);
	}

}
