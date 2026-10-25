plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "lk.bookhive"
version = "0.0.1-SNAPSHOT"
description = "BookHive public library API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val mapstructVersion = "1.6.3"
val springdocVersion = "3.1.1"
val archunitVersion = "1.5.1"
val bucket4jVersion = "8.21.0"
val awsSdkVersion = "2.46.7"
val zxingVersion = "3.5.4"
val openpdfVersion = "3.0.5"
// 1.37.0 is not yet compatible with Spotless 8.10.4
val googleJavaFormatVersion = "1.36.1"

// Adds build-info.properties so /actuator/info shows the app name and version
springBoot {
    buildInfo()
}

dependencies {
    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWT signing (RS256) and validation of Bearer tokens, via Nimbus JOSE
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Sending email over SMTP (Mailpit locally, AWS SES in production)
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Redis (Lettuce client) + Bucket4j: rate limits shared by every backend instance
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.bucket4j:bucket4j_jdk17-core:$bucket4jVersion")
    implementation("com.bucket4j:bucket4j_jdk17-lettuce:$bucket4jVersion")
    implementation("org.flywaydb:flyway-database-postgresql")
    // File storage over the S3 API: SeaweedFS locally, AWS S3 in production
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:s3") {
        // We only use the blocking client; the async Netty client is not needed
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    // Library card: Code128 barcode + QR code images, and the printable card PDF
    implementation("com.google.zxing:core:$zxingVersion")
    implementation("com.github.librepdf:openpdf:$openpdfVersion")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // Real PostgreSQL in Docker for tests (versions managed by Spring Boot)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// MapStruct mappers become Spring beans, so they can be injected with a constructor
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Amapstruct.defaultComponentModel=spring")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Formatting: ./gradlew spotlessApply fixes it, ./gradlew check fails if it's wrong
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(googleJavaFormatVersion)
    }
}
