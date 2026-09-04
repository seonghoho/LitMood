plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.litmood"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["testcontainersVersion"] = "1.20.4"

dependencies {
    // ── Web / 가상 스레드 (ADR-003) ────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ── 영속성 (ADR-004) ───────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ── 캐시 / 토큰 저장소 (ADR-005) ───────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // ── 인증 (ADR-009) ─────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // ── 오브젝트 스토리지 — 아바타 presigned 업로드 (F-01-04) ──
    implementation("software.amazon.awssdk:s3:2.29.45")

    // ── API 문서 → OpenAPI 3.1 SSOT (ADR-008) ──────────────
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // ── 관측성 (NFR-07) ────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── 개발 편의 ──────────────────────────────────────────
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ── 테스트: H2 대신 Testcontainers (docs/06-infra.md) ──
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

tasks.withType<Test> {
    // docker-java 기본값은 Docker Engine 29 가 제거한 구버전 API 라 /info 가 400 을 반환한다.
    // Engine 25 이상이 지원하는 최소 버전으로 고정해 Testcontainers 가 데몬을 찾게 한다.
    systemProperty("api.version", "1.44")
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

// OpenAPI 스펙을 리포지토리 루트로 추출 → orval / Postman 이 소비 (ADR-008)
tasks.register("exportOpenApiHint") {
    group = "documentation"
    description = "OpenAPI 스펙 추출 방법을 안내한다"
    doLast {
        println(
            """
            애플리케이션 기동 후 아래 명령으로 스펙을 추출한다:
              curl -s http://localhost:8080/v3/api-docs.yaml -o ../../packages/api-client/openapi.yaml
            """.trimIndent(),
        )
    }
}
