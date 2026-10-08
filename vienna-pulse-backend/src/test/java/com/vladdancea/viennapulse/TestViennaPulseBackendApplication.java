package com.vladdancea.viennapulse;

import org.springframework.boot.SpringApplication;

/**
 * Local development entry point: runs the app against a throwaway database
 * container. Start it with {@code ./gradlew bootTestRun}.
 */
public class TestViennaPulseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(ViennaPulseBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
