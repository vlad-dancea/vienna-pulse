package com.vladdancea.viennapulse.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the SPA (pulse.vladdancea.com, or the dev server) call the API from the browser. */
@Configuration(proxyBeanMethods = false)
class CorsConfig implements WebMvcConfigurer {

	private final List<String> allowedOrigins;

	CorsConfig(@Value("${pulse.cors.allowed-origins}") List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(allowedOrigins.toArray(String[]::new))
			.allowedMethods("GET", "POST")
			.maxAge(3600);
	}

}
