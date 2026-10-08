package com.vladdancea.viennapulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ViennaPulseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ViennaPulseBackendApplication.class, args);
	}

}
