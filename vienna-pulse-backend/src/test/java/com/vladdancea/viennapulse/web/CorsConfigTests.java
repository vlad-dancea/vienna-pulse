package com.vladdancea.viennapulse.web;

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
class CorsConfigTests {

	@Autowired
	private MockMvcTester mvc;

	@Test
	void allowsTheFrontendOrigin() {
		assertThat(mvc.options()
			.uri("/api/anything")
			.header("Origin", "https://pulse.vladdancea.com")
			.header("Access-Control-Request-Method", "GET"))
			.hasStatusOk()
			.hasHeader("Access-Control-Allow-Origin", "https://pulse.vladdancea.com");
	}

	@Test
	void rejectsOtherOrigins() {
		assertThat(mvc.options()
			.uri("/api/anything")
			.header("Origin", "https://evil.example")
			.header("Access-Control-Request-Method", "GET"))
			.hasStatus(403);
	}

}
