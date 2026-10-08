plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.vladdancea"
version = "0.0.1-SNAPSHOT"
description = "Vienna Pulse backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Only the executable jar is needed. A fixed name keeps the Dockerfile simple.
tasks.jar {
	enabled = false
}

tasks.bootJar {
	archiveFileName = "app.jar"
}

tasks.withType<Test> {
	useJUnitPlatform()
}
