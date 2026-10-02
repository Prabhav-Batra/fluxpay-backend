# Plan 1 — Backend Foundation, Identity, Merchants, API Keys

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A running Spring Boot API where a merchant can sign up, log in to a dashboard session, manage its profile, and issue/roll/revoke test and live API keys that authenticate the public API — with tenant isolation, a standard error envelope, rate limiting on auth routes, and ProjectOS architecture guards.

**Architecture:** Single Spring Boot 3.5 app (modular monolith), package-by-feature under `com.fluxpay` (`common`, `identity`, `merchants`, `apikeys`, plus app-level `config`). Each feature module is layered `api → service → domain → persistence`. Dashboard auth is a server session stored in Postgres (Spring Session JDBC); API auth is `Authorization: Bearer sk_{test|live}_…`. Both produce a principal implementing `TenantPrincipal`, from which controllers receive a `TenantContext(merchantId, mode)`.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security 6, Spring Data JPA (Hibernate 6), Spring Session JDBC, Flyway, PostgreSQL 17, Bucket4j + Caffeine, JUnit 5, Testcontainers, ArchUnit, Spotless (palantir-java-format), Gradle 8.14 (Kotlin DSL).

**Spec:** `docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md` (this plan covers §3.1 foundation, §5 `users`/`merchants`/`api_keys`, §8 auth/tenant/rate-limit/secrets, §9 errors). Plans 2–5 cover the rest.

## Global Constraints

- Java 21 toolchain; Spring Boot `3.5.x`; PostgreSQL `17`; Flyway owns the schema; `spring.jpa.hibernate.ddl-auto: validate`.
- If a pinned dependency/plugin version fails to resolve, use the latest patch of the same minor line and say so in the commit body.
- Package-by-feature under `com.fluxpay.<module>.{api,service,domain,persistence}`; module dependencies exactly as `.engineering/config/architecture.yaml`. `com.fluxpay.config` is app wiring and may depend on any module.
- ProjectOS: controllers ≤ 300 lines, `*Service*` files ≤ 500 lines, constructor injection only, `@ConfigurationProperties` instead of `@Value`, never return `@Entity` from controllers, `@Transactional` only on service implementations.
- Services never deal in HTTP status codes: they throw `FluxpayException(ErrorType, code, message)`; only `common.error` maps `ErrorType` → HTTP status.
- JSON is `snake_case` everywhere (global Jackson setting). Unknown JSON properties → 400.
- Error envelope: `{ "error": { "code", "message", "details": [{ "field", "code", "message" }], "trace_id" } }`.
- Primary keys are UUIDv7; API exposes `<prefix>_<22-char base62>`; malformed or wrong-prefix IDs → 404.
- Every merchant-owned row has `merchant_id` + `mode` (`TEST`/`LIVE` in DB, `test`/`live` in JSON). Tenant + mode come only from the principal (dashboard mode from header `FluxPay-Mode`, default `test`). Cross-tenant access → 404.
- Money is not touched in this plan.
- Secrets/config only from environment. Required: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `FRONTEND_BASE_URL`.
- Tests: `should_<behavior>_when_<condition>` method names, Arrange-Act-Assert. Integration tests extend `AbstractIntegrationTest` (Testcontainers Postgres). **Docker must be running** for integration tests.
- Before every commit: `./gradlew spotlessApply`. Commits: Conventional Commits, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- All work is committed directly on `main` (owner's decision, overrides ProjectOS feature-branch rule); push `main` after each task.

## Review Focus

1. Email entered with different case or surrounding spaces (`" Owner@Jextter.com "`) must map to the same account for signup-duplicate checks and login.
2. Two simultaneous signups with the same email must give one 201 and one 409 `EMAIL_TAKEN` — never a 500 from the unique constraint.
3. A malformed `Authorization` header (`Bearer` with nothing, `Basic …`, a random string, a revoked or rolled key) must give 401 `INVALID_API_KEY`, never 500.
4. A garbage `FluxPay-Mode` header (`prod`, `LIVE `) must give 400 `INVALID_MODE`, not silently default to test.
5. A password that is ≤ 72 characters but > 72 UTF-8 bytes (bcrypt silently truncates) must be rejected with 422 `PASSWORD_TOO_LONG`; a business name with no ASCII letters (e.g. `"जेक्सटर"`) must still get a valid slug.

Each line above has a test in the owning task (Tasks 5, 5, 9, 4, 5/7 respectively).

---

## File Map

```
fluxpay-backend/
├── settings.gradle.kts, build.gradle.kts, gradlew, gradle/wrapper/*
├── Dockerfile, .dockerignore, .env.example, .github/workflows/ci.yml
└── src/
    ├── main/java/com/fluxpay/
    │   ├── FluxpayApplication.java
    │   ├── config/        SecurityConfig, RestAuthenticationEntryPoint, RestAccessDeniedHandler
    │   ├── common/
    │   │   ├── config/    FluxpayProperties, ClockConfig, WebMvcConfig
    │   │   ├── id/        UuidV7, Base62, IdPrefix, PublicId
    │   │   ├── error/     ErrorType, ErrorDetail, ErrorResponse, FluxpayException, HttpStatusMapper,
    │   │   │              ErrorResponseWriter, GlobalExceptionHandler
    │   │   ├── web/       CorrelationIdFilter
    │   │   ├── tenant/    Mode, TenantPrincipal, TenantContext, TenantContextArgumentResolver
    │   │   └── ratelimit/ RateLimitProperties, RateLimiter, RateLimitDecision, RateLimitFilter
    │   ├── identity/
    │   │   ├── domain/      Role, UserAccount
    │   │   ├── persistence/ UserAccountRepository
    │   │   ├── service/     DashboardPrincipal, PasswordPolicy, UserService, UserServiceImpl,
    │   │   │                AdminBootstrapProperties, AdminBootstrapper
    │   │   └── api/         SessionAuthenticator, AuthController, LoginRequest, MeResponse, CsrfResponse
    │   ├── merchants/
    │   │   ├── domain/      Merchant, MerchantStatus
    │   │   ├── persistence/ MerchantRepository
    │   │   ├── service/     MerchantProperties, SlugGenerator, MerchantView, ProfileUpdate,
    │   │   │                MerchantService, MerchantServiceImpl, SignupCommand,
    │   │   │                MerchantOnboardingService, MerchantOnboardingServiceImpl
    │   │   └── api/         SignupController, SignupRequest, MerchantController,
    │   │                    UpdateMerchantRequest, MerchantResponse
    │   └── apikeys/
    │       ├── domain/      ApiKey
    │       ├── persistence/ ApiKeyRepository
    │       ├── service/     ApiKeySecret, ApiKeyPrincipal, ApiKeyView, IssuedApiKey,
    │       │                ApiKeyService, ApiKeyServiceImpl
    │       └── api/         ApiKeyAuthenticationFilter, ApiKeyController, ApiKeyResponse,
    │                        AccountController, AccountResponse
    ├── main/resources/
    │   ├── application.yml, application-local.yml, application-prod.yml
    │   ├── db/migration/ V1__spring_session.sql, V2__merchants_and_users.sql, V3__api_keys.sql
    │   └── db/rollback/  V1__down.sql, V2__down.sql, V3__down.sql
    └── test/java/com/fluxpay/
        ├── support/  TestcontainersConfiguration, DatabaseCleaner, AbstractIntegrationTest, TestMerchants
        ├── HealthEndpointsIntegrationTest
        ├── architecture/ ModuleBoundariesTest, ProjectOsConstraintsTest
        ├── common/   id/UuidV7Test, id/Base62Test, id/PublicIdTest, error/GlobalExceptionHandlerTest,
        │             web/CorrelationIdFilterTest, tenant/ModeTest, tenant/TenantContextArgumentResolverTest,
        │             ratelimit/RateLimiterTest, ratelimit/RateLimitIntegrationTest
        ├── identity/ PasswordPolicyTest, UserServiceIntegrationTest, AuthFlowIntegrationTest
        ├── merchants/ SlugGeneratorTest, SignupIntegrationTest, MerchantProfileIntegrationTest
        └── apikeys/  ApiKeySecretTest, ApiKeyManagementIntegrationTest,
                      ApiKeyAuthenticationIntegrationTest, TenantIsolationIntegrationTest
    test/resources/application-test.yml
```

---

### Task 1: Project skeleton, health endpoints, Flyway, test harness, CI, Docker

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `.env.example`, `Dockerfile`, `.dockerignore`, `.github/workflows/ci.yml`
- Restore: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (from tag `legacy-v0`)
- Create: `src/main/java/com/fluxpay/FluxpayApplication.java`, `src/main/java/com/fluxpay/common/config/FluxpayProperties.java`, `src/main/java/com/fluxpay/common/config/ClockConfig.java`
- Create: `src/main/resources/application.yml`, `application-local.yml`, `application-prod.yml`, `db/migration/V1__spring_session.sql`, `db/rollback/V1__down.sql`
- Create: `src/test/resources/application-test.yml`, `src/test/java/com/fluxpay/support/{TestcontainersConfiguration,DatabaseCleaner,AbstractIntegrationTest}.java`
- Test: `src/test/java/com/fluxpay/HealthEndpointsIntegrationTest.java`

**Interfaces:**
- Produces: `FluxpayProperties(String frontendBaseUrl)` bean; `Clock` bean (UTC); `AbstractIntegrationTest` with protected `MockMvc mockMvc`, `ObjectMapper objectMapper`, and automatic table truncation after each test.

- [ ] **Step 0: Restore the Gradle wrapper**

```bash
cd fluxpay-backend
git checkout legacy-v0 -- gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties
grep distributionUrl gradle/wrapper/gradle-wrapper.properties   # expect gradle-8.14.5-bin.zip
```

- [ ] **Step 1: Write Gradle build files**

`settings.gradle.kts`:
```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "fluxpay-backend"
```

`build.gradle.kts`:
```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "com.fluxpay"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.session:spring-session-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        palantirJavaFormat("2.50.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
```

- [ ] **Step 2: Write application class, properties and clock**

`src/main/java/com/fluxpay/FluxpayApplication.java`:
```java
package com.fluxpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FluxpayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxpayApplication.class, args);
    }
}
```

`src/main/java/com/fluxpay/common/config/FluxpayProperties.java`:
```java
package com.fluxpay.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Platform-wide settings. The app refuses to start when a required value is missing. */
@Validated
@ConfigurationProperties("fluxpay")
public record FluxpayProperties(@NotBlank String frontendBaseUrl) {}
```

`src/main/java/com/fluxpay/common/config/ClockConfig.java`:
```java
package com.fluxpay.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 3: Write configuration files and first migration**

`src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: fluxpay-backend
  threads:
    virtual:
      enabled: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    enabled: true
  session:
    jdbc:
      initialize-schema: never
  jackson:
    property-naming-strategy: SNAKE_CASE
    deserialization:
      fail-on-unknown-properties: true
    serialization:
      write-dates-as-timestamps: false

server:
  forward-headers-strategy: framework
  servlet:
    session:
      timeout: 12h
      cookie:
        http-only: true
        secure: true
        same-site: lax

management:
  endpoints:
    web:
      base-path: /
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
      show-details: never
      group:
        live:
          include: livenessState
        ready:
          include: readinessState,db

fluxpay:
  frontend-base-url: ${FRONTEND_BASE_URL}
```

`src/main/resources/application-local.yml`:
```yaml
server:
  servlet:
    session:
      cookie:
        secure: false
```

`src/main/resources/application-prod.yml`:
```yaml
logging:
  structured:
    format:
      console: logstash
```

`src/main/resources/db/migration/V1__spring_session.sql` (official Spring Session PostgreSQL schema):
```sql
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
```

`src/main/resources/db/rollback/V1__down.sql`:
```sql
-- Manual rollback for V1 (Flyway community edition does not run undo scripts).
DROP TABLE IF EXISTS spring_session_attributes;
DROP TABLE IF EXISTS spring_session;
DELETE FROM flyway_schema_history WHERE version = '1';
```

`src/test/resources/application-test.yml`:
```yaml
server:
  servlet:
    session:
      cookie:
        secure: false

fluxpay:
  frontend-base-url: http://localhost:3000
```

- [ ] **Step 4: Write the integration-test harness**

`src/test/java/com/fluxpay/support/TestcontainersConfiguration.java`:
```java
package com.fluxpay.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    @Bean
    DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate) {
        return new DatabaseCleaner(jdbcTemplate);
    }
}
```

`src/test/java/com/fluxpay/support/DatabaseCleaner.java`:
```java
package com.fluxpay.support;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Truncates every application table so each integration test starts from an empty database. */
public class DatabaseCleaner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void truncateAll() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        if (!tables.isEmpty()) {
            jdbcTemplate.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
    }
}
```

`src/test/java/com/fluxpay/support/AbstractIntegrationTest.java`:
```java
package com.fluxpay.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanDatabase() {
        databaseCleaner.truncateAll();
    }
}
```

- [ ] **Step 5: Write the failing health test**

`src/test/java/com/fluxpay/HealthEndpointsIntegrationTest.java`:
```java
package com.fluxpay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class HealthEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_report_up_when_liveness_probe_is_called() throws Exception {
        mockMvc.perform(get("/health/live")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void should_report_up_when_readiness_probe_is_called_with_database_available() throws Exception {
        mockMvc.perform(get("/health/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 6: Run it**

Run: `./gradlew test --tests 'com.fluxpay.HealthEndpointsIntegrationTest'`
Expected: FAIL with 401 — Spring Security's default config protects `/health/**`. (If it fails earlier with "Could not find a valid Docker environment", start Docker Desktop and rerun.)

- [ ] **Step 7: Make health public with a minimal security config**

`src/main/java/com/fluxpay/config/SecurityConfig.java` (replaced wholesale in Task 6):
```java
package com.fluxpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/health/**", "/error")
                .permitAll()
                .anyRequest()
                .denyAll());
        return http.build();
    }
}
```

- [ ] **Step 8: Run it again**

Run: `./gradlew test --tests 'com.fluxpay.HealthEndpointsIntegrationTest'`
Expected: PASS (2 tests).

- [ ] **Step 9: Add Docker, env example and CI**

`Dockerfile`:
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

`.dockerignore`:
```
.gradle
build
.git
.env
.engineering
docs
```

`.env.example`:
```bash
# PostgreSQL (Neon). Use a fresh database for v1.
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fluxpay
SPRING_DATASOURCE_USERNAME=fluxpay
SPRING_DATASOURCE_PASSWORD=change-me

# Public URL of fluxpay-frontend (used for CORS and checkout URLs)
FRONTEND_BASE_URL=http://localhost:3000

# Spring profile: local for development, prod on Render
SPRING_PROFILES_ACTIVE=local
```

`.github/workflows/ci.yml`:
```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew spotlessCheck build
```

- [ ] **Step 10: Format, verify the full build, commit**

```bash
./gradlew spotlessApply build
git add -A
git commit -m "chore(foundation): spring boot skeleton with health probes, flyway and testcontainers

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Public IDs (UUIDv7, Base62, prefixed IDs)

**Files:**
- Create: `src/main/java/com/fluxpay/common/id/{UuidV7,Base62,IdPrefix,PublicId}.java`
- Test: `src/test/java/com/fluxpay/common/id/{UuidV7Test,Base62Test,PublicIdTest}.java`

**Interfaces:**
- Produces:
  - `UuidV7.generate(): UUID`; package-private `UuidV7.generate(long epochMillis, Random random): UUID`
  - `Base62.encodeUuid(UUID): String` (always 22 chars), `Base62.decodeUuid(String): Optional<UUID>`, `Base62.random(int length, SecureRandom random): String`
  - `enum IdPrefix { MERCHANT("acct"), USER("user"), API_KEY("key"), PRODUCT("prod"), PAYMENT_LINK("plink"), CHECKOUT_SESSION("cs"), PAYMENT("pay"), SALE("sale"), EVENT("evt"), WEBHOOK_ENDPOINT("we"), PAYOUT("po") }` with `value()`
  - `PublicId.of(IdPrefix, UUID): String`, `PublicId.parse(IdPrefix, String): Optional<UUID>`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/common/id/UuidV7Test.java`:
```java
package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void should_set_version_7_and_rfc_variant_when_generated() {
        UUID id = UuidV7.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void should_embed_epoch_millis_in_top_48_bits_when_generated_with_fixed_time() {
        long millis = 1_759_363_200_000L;

        UUID id = UuidV7.generate(millis, new Random(1));

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(millis);
    }

    @Test
    void should_sort_by_creation_time_when_generated_at_increasing_millis() {
        UUID earlier = UuidV7.generate(1_000L, new Random(1));
        UUID later = UuidV7.generate(2_000L, new Random(1));

        assertThat(Long.compareUnsigned(earlier.getMostSignificantBits(), later.getMostSignificantBits()))
                .isNegative();
    }
}
```

`src/test/java/com/fluxpay/common/id/Base62Test.java`:
```java
package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Base62Test {

    @Test
    void should_round_trip_when_encoding_and_decoding_a_uuid() {
        UUID id = UUID.randomUUID();

        assertThat(Base62.decodeUuid(Base62.encodeUuid(id))).contains(id);
    }

    @Test
    void should_produce_22_chars_when_encoding_the_zero_uuid() {
        assertThat(Base62.encodeUuid(new UUID(0, 0))).isEqualTo("0".repeat(22));
    }

    @Test
    void should_round_trip_when_encoding_the_max_uuid() {
        UUID max = new UUID(-1L, -1L);

        assertThat(Base62.decodeUuid(Base62.encodeUuid(max))).contains(max);
    }

    @Test
    void should_return_empty_when_decoding_wrong_length_or_bad_chars_or_overflow() {
        assertThat(Base62.decodeUuid("abc")).isEmpty();
        assertThat(Base62.decodeUuid("!".repeat(22))).isEmpty();
        assertThat(Base62.decodeUuid("z".repeat(22))).isEmpty();
        assertThat(Base62.decodeUuid(null)).isEmpty();
    }

    @Test
    void should_return_only_alphabet_chars_when_generating_random_string() {
        String value = Base62.random(40, new SecureRandom());

        assertThat(value).hasSize(40).matches("[0-9A-Za-z]+");
    }
}
```

`src/test/java/com/fluxpay/common/id/PublicIdTest.java`:
```java
package com.fluxpay.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicIdTest {

    @Test
    void should_prefix_and_round_trip_when_formatting_an_id() {
        UUID id = UuidV7.generate();

        String publicId = PublicId.of(IdPrefix.PRODUCT, id);

        assertThat(publicId).startsWith("prod_").hasSize("prod_".length() + 22);
        assertThat(PublicId.parse(IdPrefix.PRODUCT, publicId)).contains(id);
    }

    @Test
    void should_return_empty_when_prefix_does_not_match() {
        String saleId = PublicId.of(IdPrefix.SALE, UuidV7.generate());

        assertThat(PublicId.parse(IdPrefix.PRODUCT, saleId)).isEmpty();
    }

    @Test
    void should_return_empty_when_input_is_null_or_garbage() {
        assertThat(PublicId.parse(IdPrefix.PRODUCT, null)).isEmpty();
        assertThat(PublicId.parse(IdPrefix.PRODUCT, "prod_")).isEmpty();
        assertThat(PublicId.parse(IdPrefix.PRODUCT, "prod_not-an-id")).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.id.*'`
Expected: FAIL — compilation errors, classes don't exist.

- [ ] **Step 3: Implement**

`src/main/java/com/fluxpay/common/id/UuidV7.java`:
```java
package com.fluxpay.common.id;

import java.security.SecureRandom;
import java.util.Random;
import java.util.UUID;

/** RFC 9562 version 7 UUIDs: 48-bit Unix millis followed by 74 random bits. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID generate() {
        return generate(System.currentTimeMillis(), RANDOM);
    }

    static UUID generate(long epochMillis, Random random) {
        byte[] r = new byte[10];
        random.nextBytes(r);
        long msb = ((epochMillis & 0xFFFF_FFFF_FFFFL) << 16) | 0x7000L | ((r[0] & 0x0FL) << 8) | (r[1] & 0xFFL);
        long lsb = 0x8000_0000_0000_0000L | ((r[2] & 0x3FL) << 56);
        for (int i = 3; i < 10; i++) {
            lsb |= (r[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
```

`src/main/java/com/fluxpay/common/id/Base62.java`:
```java
package com.fluxpay.common.id;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

public final class Base62 {

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    static final int UUID_LENGTH = 22;
    private static final BigInteger BASE = BigInteger.valueOf(62);
    private static final BigInteger UUID_LIMIT = BigInteger.ONE.shiftLeft(128);

    private Base62() {}

    public static String encodeUuid(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        BigInteger value = new BigInteger(1, buffer.array());
        StringBuilder out = new StringBuilder();
        while (value.signum() > 0) {
            BigInteger[] divRem = value.divideAndRemainder(BASE);
            out.append(ALPHABET.charAt(divRem[1].intValue()));
            value = divRem[0];
        }
        while (out.length() < UUID_LENGTH) {
            out.append('0');
        }
        return out.reverse().toString();
    }

    public static Optional<UUID> decodeUuid(String encoded) {
        if (encoded == null || encoded.length() != UUID_LENGTH) {
            return Optional.empty();
        }
        BigInteger value = BigInteger.ZERO;
        for (char c : encoded.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) {
                return Optional.empty();
            }
            value = value.multiply(BASE).add(BigInteger.valueOf(digit));
        }
        if (value.compareTo(UUID_LIMIT) >= 0) {
            return Optional.empty();
        }
        byte[] raw = value.toByteArray();
        byte[] bytes = new byte[16];
        int copy = Math.min(raw.length, 16);
        System.arraycopy(raw, raw.length - copy, bytes, 16 - copy, copy);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    public static String random(int length, SecureRandom random) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }
}
```

`src/main/java/com/fluxpay/common/id/IdPrefix.java`:
```java
package com.fluxpay.common.id;

public enum IdPrefix {
    MERCHANT("acct"),
    USER("user"),
    API_KEY("key"),
    PRODUCT("prod"),
    PAYMENT_LINK("plink"),
    CHECKOUT_SESSION("cs"),
    PAYMENT("pay"),
    SALE("sale"),
    EVENT("evt"),
    WEBHOOK_ENDPOINT("we"),
    PAYOUT("po");

    private final String value;

    IdPrefix(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
```

`src/main/java/com/fluxpay/common/id/PublicId.java`:
```java
package com.fluxpay.common.id;

import java.util.Optional;
import java.util.UUID;

/** Formats internal UUIDs as typed public IDs such as {@code prod_0F3k...}. */
public final class PublicId {

    private PublicId() {}

    public static String of(IdPrefix prefix, UUID id) {
        return prefix.value() + "_" + Base62.encodeUuid(id);
    }

    public static Optional<UUID> parse(IdPrefix prefix, String publicId) {
        String expected = prefix.value() + "_";
        if (publicId == null || !publicId.startsWith(expected)) {
            return Optional.empty();
        }
        return Base62.decodeUuid(publicId.substring(expected.length()));
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.common.id.*'`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): uuidv7 ids with prefixed base62 public ids

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Error envelope and correlation IDs

**Files:**
- Create: `src/main/java/com/fluxpay/common/error/{ErrorType,ErrorDetail,ErrorResponse,FluxpayException,HttpStatusMapper,ErrorResponseWriter,GlobalExceptionHandler}.java`
- Create: `src/main/java/com/fluxpay/common/web/CorrelationIdFilter.java`
- Test: `src/test/java/com/fluxpay/common/error/GlobalExceptionHandlerTest.java`, `src/test/java/com/fluxpay/common/web/CorrelationIdFilterTest.java`

**Interfaces:**
- Produces:
  - `enum ErrorType { BAD_REQUEST, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT, VALIDATION, RATE_LIMITED, GATEWAY_ERROR, INTERNAL }`
  - `record ErrorDetail(String field, String code, String message)`
  - `record ErrorResponse(Body error)` with nested `record Body(String code, String message, List<ErrorDetail> details, String traceId)`
  - `FluxpayException(ErrorType type, String code, String message[, List<ErrorDetail> details])` with getters `type()`, `code()`, `details()` and factories `badRequest`, `unauthenticated`, `forbidden`, `notFound`, `conflict`, `validation(field, code, message)`, `rateLimited`
  - `HttpStatusMapper.toStatus(ErrorType): HttpStatus`
  - `ErrorResponseWriter.write(HttpServletResponse, HttpStatus, String code, String message)` and `write(HttpServletResponse, FluxpayException)` (Spring bean)
  - `CorrelationIdFilter.HEADER = "X-Request-Id"`, `CorrelationIdFilter.MDC_KEY = "correlationId"`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/common/error/GlobalExceptionHandlerTest.java`:
```java
package com.fluxpay.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    record CreateThing(@NotBlank String displayName) {}

    @RestController
    static class ThrowingController {
        @GetMapping("/not-found")
        void notFound() {
            throw FluxpayException.notFound("THING_NOT_FOUND", "No such thing");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw FluxpayException.conflict("THING_TAKEN", "Taken");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }

        @PostMapping("/things")
        void create(@Valid @RequestBody CreateThing body) {}
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void should_return_404_envelope_when_service_throws_not_found() throws Exception {
        mockMvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("THING_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("No such thing"))
                .andExpect(jsonPath("$.error.details").isArray());
    }

    @Test
    void should_return_409_when_service_throws_conflict() throws Exception {
        mockMvc.perform(get("/conflict")).andExpect(status().isConflict());
    }

    @Test
    void should_return_422_with_snake_case_field_details_when_body_is_invalid() throws Exception {
        mockMvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"display_name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("display_name"));
    }

    @Test
    void should_return_400_when_body_is_malformed_json() throws Exception {
        mockMvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void should_hide_internals_and_return_500_when_unexpected_exception_is_thrown() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected error occurred"));
    }
}
```

`src/test/java/com/fluxpay/common/web/CorrelationIdFilterTest.java`:
```java
package com.fluxpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void should_propagate_incoming_id_when_header_is_valid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seen.get()).isEqualTo("req-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("req-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void should_generate_new_id_when_header_is_missing_or_unsafe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad value\nwith newline");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.error.*' --tests 'com.fluxpay.common.web.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 3: Implement the error types**

`src/main/java/com/fluxpay/common/error/ErrorType.java`:
```java
package com.fluxpay.common.error;

/** Transport-neutral error categories. Only {@link HttpStatusMapper} knows their HTTP status. */
public enum ErrorType {
    BAD_REQUEST,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    RATE_LIMITED,
    GATEWAY_ERROR,
    INTERNAL
}
```

`src/main/java/com/fluxpay/common/error/ErrorDetail.java`:
```java
package com.fluxpay.common.error;

public record ErrorDetail(String field, String code, String message) {}
```

`src/main/java/com/fluxpay/common/error/ErrorResponse.java`:
```java
package com.fluxpay.common.error;

import java.util.List;

public record ErrorResponse(Body error) {

    public record Body(String code, String message, List<ErrorDetail> details, String traceId) {}

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details, String traceId) {
        return new ErrorResponse(new Body(code, message, details, traceId));
    }
}
```

`src/main/java/com/fluxpay/common/error/FluxpayException.java`:
```java
package com.fluxpay.common.error;

import java.util.List;

/** The only exception services throw for expected failures. */
public class FluxpayException extends RuntimeException {

    private final ErrorType type;
    private final String code;
    private final List<ErrorDetail> details;

    public FluxpayException(ErrorType type, String code, String message) {
        this(type, code, message, List.of());
    }

    public FluxpayException(ErrorType type, String code, String message, List<ErrorDetail> details) {
        super(message);
        this.type = type;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public static FluxpayException badRequest(String code, String message) {
        return new FluxpayException(ErrorType.BAD_REQUEST, code, message);
    }

    public static FluxpayException unauthenticated(String code, String message) {
        return new FluxpayException(ErrorType.UNAUTHENTICATED, code, message);
    }

    public static FluxpayException forbidden(String code, String message) {
        return new FluxpayException(ErrorType.FORBIDDEN, code, message);
    }

    public static FluxpayException notFound(String code, String message) {
        return new FluxpayException(ErrorType.NOT_FOUND, code, message);
    }

    public static FluxpayException conflict(String code, String message) {
        return new FluxpayException(ErrorType.CONFLICT, code, message);
    }

    public static FluxpayException validation(String field, String code, String message) {
        return new FluxpayException(
                ErrorType.VALIDATION, "VALIDATION_FAILED", message, List.of(new ErrorDetail(field, code, message)));
    }

    public static FluxpayException rateLimited(String message) {
        return new FluxpayException(ErrorType.RATE_LIMITED, "RATE_LIMITED", message);
    }

    public ErrorType type() {
        return type;
    }

    public String code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
```

`src/main/java/com/fluxpay/common/error/HttpStatusMapper.java`:
```java
package com.fluxpay.common.error;

import org.springframework.http.HttpStatus;

public final class HttpStatusMapper {

    private HttpStatusMapper() {}

    public static HttpStatus toStatus(ErrorType type) {
        return switch (type) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case VALIDATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case GATEWAY_ERROR -> HttpStatus.BAD_GATEWAY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

`src/main/java/com/fluxpay/common/error/ErrorResponseWriter.java`:
```java
package com.fluxpay.common.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Writes the standard error envelope from servlet filters, where controller advice does not apply. */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, FluxpayException exception) throws IOException {
        write(
                response,
                HttpStatusMapper.toStatus(exception.type()),
                exception.code(),
                exception.getMessage(),
                exception.details());
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        write(response, status, code, message, List.of());
    }

    private void write(
            HttpServletResponse response, HttpStatus status, String code, String message, List<ErrorDetail> details)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(code, message, details, MDC.get(CorrelationIdFilter.MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
```

`src/main/java/com/fluxpay/common/error/GlobalExceptionHandler.java`:
```java
package com.fluxpay.common.error;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fluxpay.common.web.CorrelationIdFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE =
            new PropertyNamingStrategies.SnakeCaseStrategy();

    @ExceptionHandler(FluxpayException.class)
    public ResponseEntity<ErrorResponse> handleFluxpay(FluxpayException ex) {
        return respond(HttpStatusMapper.toStatus(ex.type()), ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorDetail(SNAKE.translate(error.getField()), "INVALID", error.getDefaultMessage()))
                .toList();
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is not valid JSON", List.of());
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status, String code, String message, List<ErrorDetail> details) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(code, message, details, MDC.get(CorrelationIdFilter.MDC_KEY)));
    }
}
```

- [ ] **Step 4: Implement the correlation filter**

`src/main/java/com/fluxpay/common/web/CorrelationIdFilter.java`:
```java
package com.fluxpay.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assigns every request a correlation ID, exposed in logs (MDC), responses and error bodies. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.common.error.*' --tests 'com.fluxpay.common.web.*'`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): standard error envelope and request correlation ids

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Tenant primitives (Mode, TenantContext, argument resolver)

**Files:**
- Create: `src/main/java/com/fluxpay/common/tenant/{Mode,TenantPrincipal,TenantContext,TenantContextArgumentResolver}.java`, `src/main/java/com/fluxpay/common/config/WebMvcConfig.java`
- Test: `src/test/java/com/fluxpay/common/tenant/{ModeTest,TenantContextArgumentResolverTest}.java`

**Interfaces:**
- Produces:
  - `enum Mode { TEST, LIVE }` with `@JsonValue value()` → `"test"`/`"live"`, `static Optional<Mode> parse(String)` (exact lowercase match only)
  - `interface TenantPrincipal { UUID merchantId(); Optional<Mode> fixedMode(); }` — `merchantId()` may be null (platform admin)
  - `record TenantContext(UUID merchantId, Mode mode)`
  - `TenantContextArgumentResolver.MODE_HEADER = "FluxPay-Mode"`; any controller parameter of type `TenantContext` is resolved from the current `Authentication`'s principal. No principal / non-tenant principal → `FluxpayException.unauthenticated("UNAUTHENTICATED", …)`; principal with null merchant → `forbidden("MERCHANT_REQUIRED", …)`; bad header → `badRequest("INVALID_MODE", …)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/common/tenant/ModeTest.java`:
```java
package com.fluxpay.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModeTest {

    @Test
    void should_parse_lowercase_values() {
        assertThat(Mode.parse("test")).contains(Mode.TEST);
        assertThat(Mode.parse("live")).contains(Mode.LIVE);
    }

    @Test
    void should_reject_unknown_padded_or_uppercase_values() {
        assertThat(Mode.parse("prod")).isEmpty();
        assertThat(Mode.parse("LIVE")).isEmpty();
        assertThat(Mode.parse("live ")).isEmpty();
        assertThat(Mode.parse(null)).isEmpty();
    }
}
```

`src/test/java/com/fluxpay/common/tenant/TenantContextArgumentResolverTest.java`:
```java
package com.fluxpay.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.ErrorType;
import com.fluxpay.common.error.FluxpayException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class TenantContextArgumentResolverTest {

    record FakePrincipal(UUID merchantId, Optional<Mode> fixedMode) implements TenantPrincipal {}

    private final TenantContextArgumentResolver resolver = new TenantContextArgumentResolver();
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(TenantPrincipal principal) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private TenantContext resolve() throws Exception {
        return (TenantContext) resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
    }

    @Test
    void should_default_to_test_mode_when_dashboard_principal_sends_no_header() throws Exception {
        UUID merchantId = UUID.randomUUID();
        authenticate(new FakePrincipal(merchantId, Optional.empty()));

        assertThat(resolve()).isEqualTo(new TenantContext(merchantId, Mode.TEST));
    }

    @Test
    void should_use_header_mode_when_dashboard_principal_sends_live() throws Exception {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.empty()));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "live");

        assertThat(resolve().mode()).isEqualTo(Mode.LIVE);
    }

    @Test
    void should_ignore_header_when_principal_has_fixed_mode() throws Exception {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.of(Mode.TEST)));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "live");

        assertThat(resolve().mode()).isEqualTo(Mode.TEST);
    }

    @Test
    void should_reject_with_bad_request_when_mode_header_is_garbage() {
        authenticate(new FakePrincipal(UUID.randomUUID(), Optional.empty()));
        request.addHeader(TenantContextArgumentResolver.MODE_HEADER, "prod");

        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_MODE");
    }

    @Test
    void should_reject_with_forbidden_when_principal_has_no_merchant() {
        authenticate(new FakePrincipal(null, Optional.empty()));

        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).type())
                .isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    void should_reject_with_unauthenticated_when_no_principal() {
        assertThatThrownBy(this::resolve)
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).type())
                .isEqualTo(ErrorType.UNAUTHENTICATED);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.tenant.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 3: Implement**

`src/main/java/com/fluxpay/common/tenant/Mode.java`:
```java
package com.fluxpay.common.tenant;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;

public enum Mode {
    TEST("test"),
    LIVE("live");

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Optional<Mode> parse(String raw) {
        return Arrays.stream(values()).filter(mode -> mode.value.equals(raw)).findFirst();
    }
}
```

`src/main/java/com/fluxpay/common/tenant/TenantPrincipal.java`:
```java
package com.fluxpay.common.tenant;

import java.util.Optional;
import java.util.UUID;

/** Implemented by every authenticated principal that can act for a merchant. */
public interface TenantPrincipal {

    /** The merchant this principal acts for, or {@code null} for platform staff. */
    UUID merchantId();

    /** Present when the credential itself is bound to a mode (API keys); empty for dashboard sessions. */
    Optional<Mode> fixedMode();
}
```

`src/main/java/com/fluxpay/common/tenant/TenantContext.java`:
```java
package com.fluxpay.common.tenant;

import java.util.UUID;

/** The merchant and mode every tenant-scoped operation runs under. Never built from request input. */
public record TenantContext(UUID merchantId, Mode mode) {}
```

`src/main/java/com/fluxpay/common/tenant/TenantContextArgumentResolver.java`:
```java
package com.fluxpay.common.tenant;

import com.fluxpay.common.error.FluxpayException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class TenantContextArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String MODE_HEADER = "FluxPay-Mode";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantContext.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            throw FluxpayException.unauthenticated("UNAUTHENTICATED", "Authentication required");
        }
        if (principal.merchantId() == null) {
            throw FluxpayException.forbidden("MERCHANT_REQUIRED", "This endpoint requires a merchant account");
        }
        Mode mode = principal.fixedMode().orElseGet(() -> modeFromHeader(webRequest.getHeader(MODE_HEADER)));
        return new TenantContext(principal.merchantId(), mode);
    }

    private static Mode modeFromHeader(String header) {
        if (header == null) {
            return Mode.TEST;
        }
        return Mode.parse(header)
                .orElseThrow(() -> FluxpayException.badRequest("INVALID_MODE", MODE_HEADER + " must be test or live"));
    }
}
```

`src/main/java/com/fluxpay/common/config/WebMvcConfig.java`:
```java
package com.fluxpay.common.config;

import com.fluxpay.common.tenant.TenantContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new TenantContextArgumentResolver());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.common.tenant.*'`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): tenant context resolved from authenticated principal and mode header

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Identity — users, password policy, admin bootstrap

**Files:**
- Create: `src/main/resources/db/migration/V2__merchants_and_users.sql`, `src/main/resources/db/rollback/V2__down.sql`
- Create: `src/main/java/com/fluxpay/identity/domain/{Role,UserAccount}.java`, `identity/persistence/UserAccountRepository.java`
- Create: `src/main/java/com/fluxpay/identity/service/{DashboardPrincipal,PasswordPolicy,PasswordEncoderConfig,UserService,UserServiceImpl,AdminBootstrapProperties,AdminBootstrapper}.java`
- Test: `src/test/java/com/fluxpay/identity/{PasswordPolicyTest,UserServiceIntegrationTest}.java`

**Interfaces:**
- Consumes: `FluxpayException`, `UuidV7`, `TenantPrincipal`, `Mode`, `Clock`.
- Produces:
  - `enum Role { MERCHANT_OWNER, PLATFORM_ADMIN }` with `authority()` → `"ROLE_" + name()` and `@JsonValue value()` → lowercase name
  - `record DashboardPrincipal(UUID userId, String email, Role role, UUID merchantId) implements TenantPrincipal, Principal, Serializable` — `getName()` returns `userId.toString()`; `fixedMode()` returns `Optional.empty()`
  - `interface UserService { DashboardPrincipal createMerchantOwner(String email, String rawPassword, UUID merchantId); DashboardPrincipal authenticate(String email, String rawPassword); void ensurePlatformAdmin(String email, String rawPassword); static String normalizeEmail(String) }`
  - Errors: `conflict("EMAIL_TAKEN")`, `unauthenticated("INVALID_CREDENTIALS")`, `validation("password", "PASSWORD_TOO_SHORT"|"PASSWORD_TOO_LONG")`
  - `void reserveEmail(String email)` on `UserService`: takes a transaction-scoped Postgres advisory lock on the normalized email, then throws `EMAIL_TAKEN` if it exists. Callers must be inside a transaction. Serializes concurrent signups for the same email.
  - Bean `PasswordEncoder` (BCrypt, strength 12) declared in `identity/service/PasswordEncoderConfig.java`. No other class may declare one.

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V2__merchants_and_users.sql`:
```sql
CREATE TABLE merchants (
    id               UUID PRIMARY KEY,
    business_name    VARCHAR(100) NOT NULL,
    slug             VARCHAR(60)  NOT NULL UNIQUE,
    logo_url         VARCHAR(500),
    brand_color      VARCHAR(7),
    platform_fee_bps INTEGER      NOT NULL CHECK (platform_fee_bps BETWEEN 0 AND 10000),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL CHECK (role IN ('MERCHANT_OWNER', 'PLATFORM_ADMIN')),
    merchant_id   UUID REFERENCES merchants (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_role_merchant CHECK ((role = 'MERCHANT_OWNER') = (merchant_id IS NOT NULL))
);

CREATE INDEX users_merchant_idx ON users (merchant_id);
```

`src/main/resources/db/rollback/V2__down.sql`:
```sql
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS merchants;
DELETE FROM flyway_schema_history WHERE version = '2';
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/fluxpay/identity/PasswordPolicyTest.java`:
```java
package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.service.PasswordPolicy;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void should_accept_password_of_10_to_72_bytes() {
        assertThatCode(() -> PasswordPolicy.validate("correct-horse")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
    }

    @Test
    void should_reject_when_password_is_shorter_than_10_chars() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short"))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).details().get(0).code())
                .isEqualTo("PASSWORD_TOO_SHORT");
    }

    @Test
    void should_reject_when_password_exceeds_72_utf8_bytes_even_if_under_72_chars() {
        String devanagari = "क".repeat(30); // 30 chars, 90 bytes

        assertThatThrownBy(() -> PasswordPolicy.validate(devanagari))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).details().get(0).code())
                .isEqualTo("PASSWORD_TOO_LONG");
    }

    @Test
    void should_reject_when_password_is_null() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null)).isInstanceOf(FluxpayException.class);
    }
}
```

`src/test/java/com/fluxpay/identity/UserServiceIntegrationTest.java`:
```java
package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.domain.Role;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID merchantId;

    @BeforeEach
    void insertMerchant() {
        merchantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO merchants (id, business_name, slug, platform_fee_bps, status, created_at, updated_at)"
                        + " VALUES (?, 'Jextter', ?, 500, 'ACTIVE', ?, ?)",
                merchantId,
                "jextter-" + merchantId,
                java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()));
    }

    @Test
    void should_normalize_email_when_creating_owner_and_authenticating() {
        DashboardPrincipal created = userService.createMerchantOwner(" Owner@Jextter.com ", "correct-horse", merchantId);

        DashboardPrincipal loggedIn = userService.authenticate("owner@jextter.COM", "correct-horse");

        assertThat(created.email()).isEqualTo("owner@jextter.com");
        assertThat(loggedIn.userId()).isEqualTo(created.userId());
        assertThat(loggedIn.role()).isEqualTo(Role.MERCHANT_OWNER);
        assertThat(loggedIn.merchantId()).isEqualTo(merchantId);
    }

    @Test
    void should_throw_email_taken_when_email_differs_only_by_case() {
        userService.createMerchantOwner("owner@jextter.com", "correct-horse", merchantId);

        assertThatThrownBy(() -> userService.createMerchantOwner("OWNER@jextter.com", "correct-horse", merchantId))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void should_throw_email_taken_when_reserving_registered_email() {
        userService.createMerchantOwner("owner@jextter.com", "correct-horse", merchantId);

        assertThatThrownBy(() -> userService.reserveEmail(" Owner@Jextter.com"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("EMAIL_TAKEN");
    }

    @Test
    void should_throw_invalid_credentials_when_password_is_wrong_or_user_unknown() {
        userService.createMerchantOwner("owner@jextter.com", "correct-horse", merchantId);

        assertThatThrownBy(() -> userService.authenticate("owner@jextter.com", "wrong-password"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CREDENTIALS");
        assertThatThrownBy(() -> userService.authenticate("nobody@jextter.com", "correct-horse"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void should_create_admin_once_when_ensure_platform_admin_is_called_twice() {
        userService.ensurePlatformAdmin("admin@fluxpay.in", "admin-password-1");
        userService.ensurePlatformAdmin("admin@fluxpay.in", "admin-password-1");

        DashboardPrincipal admin = userService.authenticate("admin@fluxpay.in", "admin-password-1");
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);

        assertThat(admin.role()).isEqualTo(Role.PLATFORM_ADMIN);
        assertThat(admin.merchantId()).isNull();
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.identity.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement domain and repository**

`src/main/java/com/fluxpay/identity/domain/Role.java`:
```java
package com.fluxpay.identity.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Role {
    MERCHANT_OWNER,
    PLATFORM_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/identity/domain/UserAccount.java`:
```java
package com.fluxpay.identity.domain;

import com.fluxpay.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    private UserAccount(String email, String passwordHash, Role role, UUID merchantId, Instant now) {
        this.id = UuidV7.generate();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.merchantId = merchantId;
        this.createdAt = now;
    }

    public static UserAccount merchantOwner(String email, String passwordHash, UUID merchantId, Instant now) {
        return new UserAccount(email, passwordHash, Role.MERCHANT_OWNER, merchantId, now);
    }

    public static UserAccount platformAdmin(String email, String passwordHash, Instant now) {
        return new UserAccount(email, passwordHash, Role.PLATFORM_ADMIN, null, now);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UUID getMerchantId() {
        return merchantId;
    }
}
```

`src/main/java/com/fluxpay/identity/persistence/UserAccountRepository.java`:
```java
package com.fluxpay.identity.persistence;

import com.fluxpay.identity.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Blocks until no other transaction holds the lock for this email; released at commit/rollback. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('email:' || :email))) AS l", nativeQuery = true)
    Integer lockEmail(@Param("email") String email);
}
```

- [ ] **Step 5: Implement the service layer**

`src/main/java/com/fluxpay/identity/service/DashboardPrincipal.java`:
```java
package com.fluxpay.identity.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantPrincipal;
import com.fluxpay.identity.domain.Role;
import java.io.Serializable;
import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/** The signed-in dashboard user, stored in the server session. */
public record DashboardPrincipal(UUID userId, String email, Role role, UUID merchantId)
        implements TenantPrincipal, Principal, Serializable {

    @Override
    public Optional<Mode> fixedMode() {
        return Optional.empty();
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
```

`src/main/java/com/fluxpay/identity/service/PasswordPolicy.java`:
```java
package com.fluxpay.identity.service;

import com.fluxpay.common.error.FluxpayException;
import java.nio.charset.StandardCharsets;

/** bcrypt ignores input beyond 72 bytes, so longer passwords are rejected rather than silently truncated. */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw FluxpayException.validation(
                    "password", "PASSWORD_TOO_SHORT", "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw FluxpayException.validation(
                    "password", "PASSWORD_TOO_LONG", "Password must be at most " + MAX_BYTES + " bytes");
        }
    }
}
```

`src/main/java/com/fluxpay/identity/service/PasswordEncoderConfig.java`:
```java
package com.fluxpay.identity.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

`src/main/java/com/fluxpay/identity/service/UserService.java`:
```java
package com.fluxpay.identity.service;

import java.util.Locale;
import java.util.UUID;

public interface UserService {

    /**
     * Locks the email for the current transaction and throws EMAIL_TAKEN (conflict) if it is registered.
     * Must be called inside a transaction; serializes concurrent signups for one email.
     */
    void reserveEmail(String email);

    /** Creates the owner login for a new merchant. Throws EMAIL_TAKEN (conflict) when the email exists. */
    DashboardPrincipal createMerchantOwner(String email, String rawPassword, UUID merchantId);

    /** Throws INVALID_CREDENTIALS (unauthenticated) for an unknown email or wrong password. */
    DashboardPrincipal authenticate(String email, String rawPassword);

    /** Creates the platform admin if no user with that email exists; otherwise does nothing. */
    void ensurePlatformAdmin(String email, String rawPassword);

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/identity/service/UserServiceImpl.java`:
```java
package com.fluxpay.identity.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.identity.domain.UserAccount;
import com.fluxpay.identity.persistence.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String dummyHash;

    public UserServiceImpl(UserAccountRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-password");
    }

    @Override
    @Transactional
    public void reserveEmail(String email) {
        String normalized = UserService.normalizeEmail(email);
        users.lockEmail(normalized);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
    }

    @Override
    @Transactional
    public DashboardPrincipal createMerchantOwner(String email, String rawPassword, UUID merchantId) {
        String normalized = UserService.normalizeEmail(email);
        PasswordPolicy.validate(rawPassword);
        if (users.existsByEmail(normalized)) {
            throw emailTaken();
        }
        UserAccount account = UserAccount.merchantOwner(
                normalized, passwordEncoder.encode(rawPassword), merchantId, Instant.now(clock));
        try {
            return toPrincipal(users.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardPrincipal authenticate(String email, String rawPassword) {
        Optional<UserAccount> account = users.findByEmail(UserService.normalizeEmail(email));
        String hash = account.map(UserAccount::getPasswordHash).orElse(dummyHash);
        boolean matches = rawPassword != null && passwordEncoder.matches(rawPassword, hash);
        if (account.isEmpty() || !matches) {
            throw FluxpayException.unauthenticated("INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return toPrincipal(account.get());
    }

    @Override
    @Transactional
    public void ensurePlatformAdmin(String email, String rawPassword) {
        String normalized = UserService.normalizeEmail(email);
        if (users.existsByEmail(normalized)) {
            log.info("Platform admin bootstrap skipped: user already exists");
            return;
        }
        PasswordPolicy.validate(rawPassword);
        users.save(UserAccount.platformAdmin(normalized, passwordEncoder.encode(rawPassword), Instant.now(clock)));
        log.info("Platform admin created");
    }

    private static DashboardPrincipal toPrincipal(UserAccount account) {
        return new DashboardPrincipal(
                account.getId(), account.getEmail(), account.getRole(), account.getMerchantId());
    }

    private static FluxpayException emailTaken() {
        return FluxpayException.conflict("EMAIL_TAKEN", "An account with this email already exists");
    }
}
```

`src/main/java/com/fluxpay/identity/service/AdminBootstrapProperties.java`:
```java
package com.fluxpay.identity.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional: when both are set, the platform admin is created on startup if missing. */
@ConfigurationProperties("fluxpay.admin")
public record AdminBootstrapProperties(String bootstrapEmail, String bootstrapPassword) {

    public boolean isConfigured() {
        return bootstrapEmail != null
                && !bootstrapEmail.isBlank()
                && bootstrapPassword != null
                && !bootstrapPassword.isBlank();
    }
}
```

`src/main/java/com/fluxpay/identity/service/AdminBootstrapper.java`:
```java
package com.fluxpay.identity.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapper implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserService userService;

    public AdminBootstrapper(AdminBootstrapProperties properties, UserService userService) {
        this.properties = properties;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isConfigured()) {
            userService.ensurePlatformAdmin(properties.bootstrapEmail(), properties.bootstrapPassword());
        }
    }
}
```

Append to `src/main/resources/application.yml` under the existing `fluxpay:` key:
```yaml
  admin:
    bootstrap-email: ${FLUXPAY_ADMIN_EMAIL:}
    bootstrap-password: ${FLUXPAY_ADMIN_PASSWORD:}
```

Append to `.env.example`:
```bash
# Optional: creates the platform admin login on startup if it does not exist
FLUXPAY_ADMIN_EMAIL=
FLUXPAY_ADMIN_PASSWORD=
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.identity.*'`
Expected: PASS (9 tests). Hibernate `validate` also passes against V2 (the `merchants` table has no entity yet, which is fine).

- [ ] **Step 7: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(identity): user accounts with bcrypt, email normalization and admin bootstrap

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Security configuration and dashboard session auth (login, logout, me, csrf)

**Files:**
- Modify (replace): `src/main/java/com/fluxpay/config/SecurityConfig.java`
- Create: `src/main/java/com/fluxpay/config/{RestAuthenticationEntryPoint,RestAccessDeniedHandler}.java`
- Create: `src/main/java/com/fluxpay/identity/api/{SessionAuthenticator,AuthController,LoginRequest,MeResponse,CsrfResponse}.java`
- Create: `src/test/java/com/fluxpay/support/TestMerchants.java` (session helpers; extended in Tasks 7–8)
- Test: `src/test/java/com/fluxpay/identity/AuthFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `UserService`, `DashboardPrincipal`, `ErrorResponseWriter`, `FluxpayProperties`.
- Produces:
  - Routes: `GET /api/v1/auth/csrf` (public) → `{ "token", "header_name" }`; `POST /api/v1/auth/login` (public, CSRF) → 200 `MeResponse`; `POST /api/v1/auth/logout` → 204; `GET /api/v1/auth/me` → `MeResponse`
  - `record MeResponse(String id, String email, Role role, String merchantId)` with `static MeResponse from(DashboardPrincipal)` (`id` = `user_…`, `merchant_id` = `acct_…` or null)
  - `SessionAuthenticator.signIn(DashboardPrincipal, HttpServletRequest, HttpServletResponse)` and `signOut(HttpServletRequest, HttpServletResponse)`
  - Bean `SecurityContextRepository` (HttpSessionSecurityContextRepository)
  - URL policy (final for Plan 1; Task 9 adds the API-key filter):
    - public: `/health/**`, `/error`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `POST /api/v1/auth/signup`, `/api/v1/public/**`
    - `/api/v1/auth/me`, `/api/v1/auth/logout`: `MERCHANT_OWNER` or `PLATFORM_ADMIN`
    - `/api/v1/dashboard/**`: `MERCHANT_OWNER`
    - `/api/v1/admin/**`: `PLATFORM_ADMIN`
    - `/api/v1/**` (everything else): `API_KEY`
    - anything else: denied
    - CSRF enforced on `/api/v1/auth/**`, `/api/v1/dashboard/**`, `/api/v1/admin/**`; ignored elsewhere
  - Test helper `TestMerchants.csrfPost(String url)` etc. (see code)

- [ ] **Step 1: Write the failing test**

`src/test/java/com/fluxpay/identity/AuthFlowIntegrationTest.java`:
```java
package com.fluxpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.identity.service.UserService;
import com.fluxpay.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGIN_BODY = "{\"email\":\"Owner@Jextter.com\",\"password\":\"correct-horse\"}";

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createOwner() {
        UUID merchantId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO merchants (id, business_name, slug, platform_fee_bps, status, created_at, updated_at)"
                        + " VALUES (?, 'Jextter', 'jextter', 500, 'ACTIVE', ?, ?)",
                merchantId,
                now,
                now);
        userService.createMerchantOwner("owner@jextter.com", "correct-horse", merchantId);
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    void should_return_me_with_prefixed_ids_when_logged_in() throws Exception {
        Cookie session = login();

        mockMvc.perform(get("/api/v1/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@jextter.com"))
                .andExpect(jsonPath("$.role").value("merchant_owner"))
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("user_")))
                .andExpect(jsonPath("$.merchant_id").value(org.hamcrest.Matchers.startsWith("acct_")));
    }

    @Test
    void should_return_401_envelope_when_me_is_called_without_session() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return_401_when_password_is_wrong() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@jextter.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void should_return_403_when_login_is_posted_without_csrf_token() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void should_invalidate_session_when_logged_out() throws Exception {
        Cookie session = login();

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void should_issue_csrf_token_when_requested_anonymously() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.header_name").value("X-XSRF-TOKEN"));
    }

    @Test
    void should_return_401_when_unknown_api_path_is_called_anonymously() throws Exception {
        mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.identity.AuthFlowIntegrationTest'`
Expected: FAIL — compilation errors (no `AuthController` etc.).

- [ ] **Step 3: Implement security wiring**

`src/main/java/com/fluxpay/config/RestAuthenticationEntryPoint.java`:
```java
package com.fluxpay.config;

import com.fluxpay.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required");
    }
}
```

`src/main/java/com/fluxpay/config/RestAccessDeniedHandler.java`:
```java
package com.fluxpay.config;

import com.fluxpay.common.error.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorWriter;

    public RestAccessDeniedHandler(ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        if (ex instanceof CsrfException) {
            errorWriter.write(response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID", "Missing or invalid CSRF token");
            return;
        }
        errorWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
    }
}
```

`src/main/java/com/fluxpay/config/SecurityConfig.java` (replace the Task 1 version):
```java
package com.fluxpay.config;

import com.fluxpay.common.config.FluxpayProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    static final List<String> SESSION_PREFIXES = List.of("/api/v1/auth/", "/api/v1/dashboard/", "/api/v1/admin/");

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        RequestMatcher notSessionRoute = request -> SESSION_PREFIXES.stream()
                .noneMatch(prefix -> request.getRequestURI().startsWith(prefix));

        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(notSessionRoute))
                .cors(Customizer.withDefaults())
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/health/**", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/signup")
                        .permitAll()
                        .requestMatchers("/api/v1/public/**")
                        .permitAll()
                        .requestMatchers("/api/v1/auth/**")
                        .hasAnyRole("MERCHANT_OWNER", "PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/dashboard/**")
                        .hasRole("MERCHANT_OWNER")
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("PLATFORM_ADMIN")
                        .requestMatchers("/api/v1/**")
                        .hasRole("API_KEY")
                        .anyRequest()
                        .denyAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(FluxpayProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.frontendBaseUrl()));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "FluxPay-Mode", "X-Request-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
```

- [ ] **Step 4: Implement the auth API**

`src/main/java/com/fluxpay/identity/api/SessionAuthenticator.java`:
```java
package com.fluxpay.identity.api;

import com.fluxpay.identity.service.DashboardPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Starts and ends dashboard sessions. Rotates the session id on sign-in to prevent fixation. */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void signIn(DashboardPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler()
                .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    }
}
```

`src/main/java/com/fluxpay/identity/api/LoginRequest.java`:
```java
package com.fluxpay.identity.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
```

`src/main/java/com/fluxpay/identity/api/MeResponse.java`:
```java
package com.fluxpay.identity.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.identity.domain.Role;
import com.fluxpay.identity.service.DashboardPrincipal;

public record MeResponse(String id, String email, Role role, String merchantId) {

    public static MeResponse from(DashboardPrincipal principal) {
        String merchantId =
                principal.merchantId() == null ? null : PublicId.of(IdPrefix.MERCHANT, principal.merchantId());
        return new MeResponse(
                PublicId.of(IdPrefix.USER, principal.userId()), principal.email(), principal.role(), merchantId);
    }
}
```

`src/main/java/com/fluxpay/identity/api/CsrfResponse.java`:
```java
package com.fluxpay.identity.api;

public record CsrfResponse(String token, String headerName) {}
```

`src/main/java/com/fluxpay/identity/api/AuthController.java`:
```java
package com.fluxpay.identity.api;

import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(UserService userService, SessionAuthenticator sessionAuthenticator) {
        this.userService = userService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        DashboardPrincipal principal = userService.authenticate(body.email(), body.password());
        sessionAuthenticator.signIn(principal, request, response);
        return MeResponse.from(principal);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticator.signOut(request, response);
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal DashboardPrincipal principal) {
        return MeResponse.from(principal);
    }
}
```

`src/test/java/com/fluxpay/support/TestMerchants.java` (initial version — Task 7 adds `signUp`, Task 8 adds `createApiKey`):
```java
package com.fluxpay.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Request builders and fixtures shared by integration tests. */
public final class TestMerchants {

    private TestMerchants() {}

    public static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return MockMvcRequestBuilders.post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.identity.*' --tests 'com.fluxpay.HealthEndpointsIntegrationTest'`
Expected: PASS. If `should_invalidate_session_when_logged_out` fails because Spring Session keeps the cookie valid, confirm `SecurityContextLogoutHandler` has `invalidateHttpSession=true` (the default).

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(identity): session login, logout, me and csrf with json error responses

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Merchants — self-serve signup and profile

**Files:**
- Create: `src/main/java/com/fluxpay/merchants/domain/{Merchant,MerchantStatus}.java`, `merchants/persistence/MerchantRepository.java`
- Create: `src/main/java/com/fluxpay/merchants/service/{MerchantProperties,SlugGenerator,MerchantView,ProfileUpdate,MerchantService,MerchantServiceImpl,SignupCommand,MerchantOnboardingService,MerchantOnboardingServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/merchants/api/{SignupController,SignupRequest,MerchantController,UpdateMerchantRequest,MerchantResponse}.java`
- Modify: `src/main/resources/application.yml`, `.env.example`, `src/test/java/com/fluxpay/support/TestMerchants.java`
- Test: `src/test/java/com/fluxpay/merchants/{SlugGeneratorTest,SignupIntegrationTest,MerchantProfileIntegrationTest}.java`

**Interfaces:**
- Consumes: `UserService.createMerchantOwner`, `SessionAuthenticator.signIn`, `MeResponse.from`, `TenantContext`.
- Produces:
  - `POST /api/v1/auth/signup` `{business_name, email, password}` → 201 `MeResponse`, session cookie set
  - `GET /api/v1/dashboard/merchant`, `PATCH /api/v1/dashboard/merchant` `{business_name?, logo_url?, brand_color?}` (`""` clears logo/colour) → `MerchantResponse`
  - `record MerchantView(UUID id, String businessName, String slug, String logoUrl, String brandColor, int platformFeeBps, MerchantStatus status, Instant createdAt)`
  - `interface MerchantService { MerchantView get(UUID merchantId); MerchantView updateProfile(UUID merchantId, ProfileUpdate update); boolean isActive(UUID merchantId); }` — unknown id → `notFound("MERCHANT_NOT_FOUND")`
  - `enum MerchantStatus { ACTIVE, SUSPENDED }` with lowercase `@JsonValue`
  - `TestMerchants.signUp(MockMvc, String businessName, String email): SignedIn` where `record SignedIn(Cookie session, String merchantId, String userId)`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/merchants/SlugGeneratorTest.java`:
```java
package com.fluxpay.merchants;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.merchants.service.SlugGenerator;
import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    @Test
    void should_lowercase_and_hyphenate_when_name_has_spaces_and_symbols() {
        assertThat(SlugGenerator.baseSlug("Jextter  Esports & Co.")).isEqualTo("jextter-esports-co");
    }

    @Test
    void should_strip_accents_when_name_has_diacritics() {
        assertThat(SlugGenerator.baseSlug("Café Ñandú")).isEqualTo("cafe-nandu");
    }

    @Test
    void should_fall_back_to_merchant_when_name_has_no_ascii_letters() {
        assertThat(SlugGenerator.baseSlug("जेक्सटर")).isEqualTo("merchant");
    }

    @Test
    void should_truncate_to_40_chars_without_trailing_hyphen() {
        String slug = SlugGenerator.baseSlug("a".repeat(39) + " bcd");

        assertThat(slug).hasSizeLessThanOrEqualTo(40).doesNotEndWith("-");
    }
}
```

`src/test/java/com/fluxpay/merchants/SignupIntegrationTest.java`:
```java
package com.fluxpay.merchants;

import static com.fluxpay.support.TestMerchants.jsonPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class SignupIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY =
            "{\"business_name\":\"Jextter\",\"email\":\"owner@jextter.com\",\"password\":\"correct-horse\"}";

    @Test
    void should_create_merchant_owner_and_session_when_signing_up() throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("merchant_owner"))
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/v1/dashboard/merchant").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.slug").value("jextter"))
                .andExpect(jsonPath("$.platform_fee_bps").value(500))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void should_give_unique_slug_when_two_merchants_share_a_name() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY)).andExpect(status().isCreated());
        MvcResult second = mockMvc.perform(jsonPost(
                        "/api/v1/auth/signup",
                        "{\"business_name\":\"Jextter\",\"email\":\"other@jextter.com\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/v1/dashboard/merchant")
                        .cookie(second.getResponse().getCookie("SESSION")))
                .andExpect(jsonPath("$.slug").value(org.hamcrest.Matchers.matchesPattern("jextter-[a-z0-9]{6}")));
    }

    @Test
    void should_return_409_when_email_is_taken_in_different_case() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY)).andExpect(status().isCreated());

        mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY.replace("owner@jextter.com", " OWNER@Jextter.com ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void should_return_one_201_and_one_409_when_same_email_signs_up_concurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Integer> signup = () -> mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY))
                .andReturn()
                .getResponse()
                .getStatus();
        List<Future<Integer>> futures = pool.invokeAll(List.of(signup, signup));
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void should_return_422_when_password_is_too_long_in_bytes() throws Exception {
        String body = BODY.replace("correct-horse", "क".repeat(30));

        mockMvc.perform(jsonPost("/api/v1/auth/signup", body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void should_return_422_when_email_is_invalid_or_business_name_blank() throws Exception {
        mockMvc.perform(jsonPost(
                        "/api/v1/auth/signup",
                        "{\"business_name\":\"\",\"email\":\"not-an-email\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details.length()").value(2));
    }

    @Test
    void should_create_valid_slug_when_business_name_is_non_latin() throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", BODY.replace("Jextter", "जेक्सटर")))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(get("/api/v1/dashboard/merchant")
                        .cookie(result.getResponse().getCookie("SESSION")))
                .andExpect(jsonPath("$.slug").value("merchant"))
                .andExpect(jsonPath("$.business_name").value("जेक्सटर"));
    }
}
```

`src/test/java/com/fluxpay/merchants/MerchantProfileIntegrationTest.java`:
```java
package com.fluxpay.merchants;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class MerchantProfileIntegrationTest extends AbstractIntegrationTest {

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private org.springframework.test.web.servlet.ResultActions patchProfile(String body) throws Exception {
        return mockMvc.perform(patch("/api/v1/dashboard/merchant")
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_update_only_given_fields_when_patching_profile() throws Exception {
        patchProfile("{\"logo_url\":\"https://cdn.jextter.com/logo.png\",\"brand_color\":\"#FF3366\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.logo_url").value("https://cdn.jextter.com/logo.png"))
                .andExpect(jsonPath("$.brand_color").value("#FF3366"));
    }

    @Test
    void should_clear_logo_when_empty_string_is_sent() throws Exception {
        patchProfile("{\"logo_url\":\"https://cdn.jextter.com/logo.png\"}").andExpect(status().isOk());

        patchProfile("{\"logo_url\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.logo_url").isEmpty());
    }

    @Test
    void should_return_422_when_logo_is_not_https_or_colour_is_invalid() throws Exception {
        patchProfile("{\"logo_url\":\"javascript:alert(1)\",\"brand_color\":\"red\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details.length()").value(2));
    }

    @Test
    void should_return_400_when_unknown_field_is_sent() throws Exception {
        patchProfile("{\"platform_fee_bps\":0}").andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.merchants.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 3: Implement domain and persistence**

`src/main/java/com/fluxpay/merchants/domain/MerchantStatus.java`:
```java
package com.fluxpay.merchants.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum MerchantStatus {
    ACTIVE,
    SUSPENDED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/merchants/domain/Merchant.java`:
```java
package com.fluxpay.merchants.domain;

import com.fluxpay.common.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {

    @Id
    private UUID id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "brand_color")
    private String brandColor;

    @Column(name = "platform_fee_bps", nullable = false)
    private int platformFeeBps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Merchant() {}

    public Merchant(String businessName, String slug, int platformFeeBps, Instant now) {
        this.id = UuidV7.generate();
        this.businessName = businessName;
        this.slug = slug;
        this.platformFeeBps = platformFeeBps;
        this.status = MerchantStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String businessName, Instant now) {
        this.businessName = businessName;
        this.updatedAt = now;
    }

    public void changeBranding(String logoUrl, String brandColor, Instant now) {
        this.logoUrl = logoUrl;
        this.brandColor = brandColor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getSlug() {
        return slug;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getBrandColor() {
        return brandColor;
    }

    public int getPlatformFeeBps() {
        return platformFeeBps;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/merchants/persistence/MerchantRepository.java`:
```java
package com.fluxpay.merchants.persistence;

import com.fluxpay.merchants.domain.Merchant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantRepository extends JpaRepository<Merchant, UUID> {

    boolean existsBySlug(String slug);

    /** Serializes slug selection for one base slug within the current transaction. */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('slug:' || :slug))) AS l", nativeQuery = true)
    Integer lockSlug(@Param("slug") String slug);
}
```

- [ ] **Step 4: Implement the service layer**

`src/main/java/com/fluxpay/merchants/service/MerchantProperties.java`:
```java
package com.fluxpay.merchants.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.merchants")
public record MerchantProperties(@Min(0) @Max(10000) int defaultPlatformFeeBps) {}
```

Append to `application.yml` under `fluxpay:`:
```yaml
  merchants:
    default-platform-fee-bps: ${DEFAULT_PLATFORM_FEE_BPS:500}
```

Append to `.env.example`:
```bash
# Platform fee for new merchants in basis points (500 = 5%). Changeable per merchant by the admin.
DEFAULT_PLATFORM_FEE_BPS=500
```

`src/main/java/com/fluxpay/merchants/service/SlugGenerator.java`:
```java
package com.fluxpay.merchants.service;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugGenerator {

    static final int MAX_LENGTH = 40;
    static final String FALLBACK = "merchant";

    private SlugGenerator() {}

    public static String baseSlug(String businessName) {
        String ascii = Normalizer.normalize(businessName, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? FALLBACK : slug;
    }
}
```

`src/main/java/com/fluxpay/merchants/service/MerchantView.java`:
```java
package com.fluxpay.merchants.service;

import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.domain.MerchantStatus;
import java.time.Instant;
import java.util.UUID;

public record MerchantView(
        UUID id,
        String businessName,
        String slug,
        String logoUrl,
        String brandColor,
        int platformFeeBps,
        MerchantStatus status,
        Instant createdAt) {

    static MerchantView from(Merchant merchant) {
        return new MerchantView(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getSlug(),
                merchant.getLogoUrl(),
                merchant.getBrandColor(),
                merchant.getPlatformFeeBps(),
                merchant.getStatus(),
                merchant.getCreatedAt());
    }
}
```

`src/main/java/com/fluxpay/merchants/service/ProfileUpdate.java`:
```java
package com.fluxpay.merchants.service;

/** Null means "leave unchanged"; an empty string clears logo or colour. */
public record ProfileUpdate(String businessName, String logoUrl, String brandColor) {}
```

`src/main/java/com/fluxpay/merchants/service/MerchantService.java`:
```java
package com.fluxpay.merchants.service;

import java.util.UUID;

public interface MerchantService {

    MerchantView get(UUID merchantId);

    MerchantView updateProfile(UUID merchantId, ProfileUpdate update);

    boolean isActive(UUID merchantId);
}
```

`src/main/java/com/fluxpay/merchants/service/MerchantServiceImpl.java`:
```java
package com.fluxpay.merchants.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.persistence.MerchantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchants;
    private final Clock clock;

    public MerchantServiceImpl(MerchantRepository merchants, Clock clock) {
        this.merchants = merchants;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantView get(UUID merchantId) {
        return MerchantView.from(find(merchantId));
    }

    @Override
    @Transactional
    public MerchantView updateProfile(UUID merchantId, ProfileUpdate update) {
        Merchant merchant = find(merchantId);
        Instant now = Instant.now(clock);
        if (update.businessName() != null) {
            merchant.rename(update.businessName().trim(), now);
        }
        if (update.logoUrl() != null || update.brandColor() != null) {
            merchant.changeBranding(
                    resolve(update.logoUrl(), merchant.getLogoUrl()),
                    resolve(update.brandColor(), merchant.getBrandColor()),
                    now);
        }
        return MerchantView.from(merchant);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID merchantId) {
        return merchants.findById(merchantId)
                .map(merchant -> merchant.getStatus() == MerchantStatus.ACTIVE)
                .orElse(false);
    }

    private Merchant find(UUID merchantId) {
        return merchants
                .findById(merchantId)
                .orElseThrow(() -> FluxpayException.notFound("MERCHANT_NOT_FOUND", "Merchant not found"));
    }

    private static String resolve(String requested, String current) {
        if (requested == null) {
            return current;
        }
        return requested.isEmpty() ? null : requested;
    }
}
```

`src/main/java/com/fluxpay/merchants/service/SignupCommand.java`:
```java
package com.fluxpay.merchants.service;

public record SignupCommand(String businessName, String email, String password) {}
```

`src/main/java/com/fluxpay/merchants/service/MerchantOnboardingService.java`:
```java
package com.fluxpay.merchants.service;

import com.fluxpay.identity.service.DashboardPrincipal;

public interface MerchantOnboardingService {

    /** Creates a merchant and its owner login atomically. */
    DashboardPrincipal signUp(SignupCommand command);
}
```

`src/main/java/com/fluxpay/merchants/service/MerchantOnboardingServiceImpl.java`:
```java
package com.fluxpay.merchants.service;

import com.fluxpay.common.id.Base62;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.identity.service.UserService;
import com.fluxpay.merchants.domain.Merchant;
import com.fluxpay.merchants.persistence.MerchantRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantOnboardingServiceImpl implements MerchantOnboardingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MerchantRepository merchants;
    private final UserService userService;
    private final MerchantProperties properties;
    private final Clock clock;

    public MerchantOnboardingServiceImpl(
            MerchantRepository merchants, UserService userService, MerchantProperties properties, Clock clock) {
        this.merchants = merchants;
        this.userService = userService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public DashboardPrincipal signUp(SignupCommand command) {
        userService.reserveEmail(command.email());
        String name = command.businessName().trim();
        Merchant merchant =
                new Merchant(name, uniqueSlug(name), properties.defaultPlatformFeeBps(), Instant.now(clock));
        merchants.saveAndFlush(merchant);
        return userService.createMerchantOwner(command.email(), command.password(), merchant.getId());
    }

    private String uniqueSlug(String businessName) {
        String base = SlugGenerator.baseSlug(businessName);
        merchants.lockSlug(base);
        String candidate = base;
        while (merchants.existsBySlug(candidate)) {
            candidate = base + "-" + Base62.random(6, RANDOM).toLowerCase(Locale.ROOT);
        }
        return candidate;
    }
}
```

- [ ] **Step 5: Implement the API**

`src/main/java/com/fluxpay/merchants/api/SignupRequest.java`:
```java
package com.fluxpay.merchants.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Password rules are enforced by PasswordPolicy so byte length is checked, not just characters. */
public record SignupRequest(
        @NotBlank @Size(max = 100) String businessName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull String password) {}
```

`src/main/java/com/fluxpay/merchants/api/SignupController.java`:
```java
package com.fluxpay.merchants.api;

import com.fluxpay.identity.api.MeResponse;
import com.fluxpay.identity.api.SessionAuthenticator;
import com.fluxpay.identity.service.DashboardPrincipal;
import com.fluxpay.merchants.service.MerchantOnboardingService;
import com.fluxpay.merchants.service.SignupCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignupController {

    private final MerchantOnboardingService onboarding;
    private final SessionAuthenticator sessionAuthenticator;

    public SignupController(MerchantOnboardingService onboarding, SessionAuthenticator sessionAuthenticator) {
        this.onboarding = onboarding;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @PostMapping("/api/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse signUp(
            @Valid @RequestBody SignupRequest body, HttpServletRequest request, HttpServletResponse response) {
        DashboardPrincipal principal =
                onboarding.signUp(new SignupCommand(body.businessName(), body.email(), body.password()));
        sessionAuthenticator.signIn(principal, request, response);
        return MeResponse.from(principal);
    }
}
```

`src/main/java/com/fluxpay/merchants/api/UpdateMerchantRequest.java`:
```java
package com.fluxpay.merchants.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateMerchantRequest(
        @Size(min = 1, max = 100) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String businessName,
        @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https URL") String logoUrl,
        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "must be a hex colour like #FF3366") String brandColor) {}
```

`src/main/java/com/fluxpay/merchants/api/MerchantResponse.java`:
```java
package com.fluxpay.merchants.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.merchants.domain.MerchantStatus;
import com.fluxpay.merchants.service.MerchantView;
import java.time.Instant;

public record MerchantResponse(
        String id,
        String businessName,
        String slug,
        String logoUrl,
        String brandColor,
        int platformFeeBps,
        MerchantStatus status,
        Instant createdAt) {

    public static MerchantResponse from(MerchantView view) {
        return new MerchantResponse(
                PublicId.of(IdPrefix.MERCHANT, view.id()),
                view.businessName(),
                view.slug(),
                view.logoUrl(),
                view.brandColor(),
                view.platformFeeBps(),
                view.status(),
                view.createdAt());
    }
}
```

`src/main/java/com/fluxpay/merchants/api/MerchantController.java`:
```java
package com.fluxpay.merchants.api;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.ProfileUpdate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/merchant")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public MerchantResponse get(TenantContext tenant) {
        return MerchantResponse.from(merchantService.get(tenant.merchantId()));
    }

    @PatchMapping
    public MerchantResponse update(TenantContext tenant, @Valid @RequestBody UpdateMerchantRequest body) {
        ProfileUpdate update = new ProfileUpdate(body.businessName(), body.logoUrl(), body.brandColor());
        return MerchantResponse.from(merchantService.updateProfile(tenant.merchantId(), update));
    }
}
```

- [ ] **Step 6: Extend the test helper**

Replace `src/test/java/com/fluxpay/support/TestMerchants.java` with:
```java
package com.fluxpay.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Request builders and fixtures shared by integration tests. */
public final class TestMerchants {

    public static final String PASSWORD = "correct-horse";

    public record SignedIn(Cookie session, String merchantId, String userId) {}

    private TestMerchants() {}

    public static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return MockMvcRequestBuilders.post(url)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    public static SignedIn signUp(MockMvc mockMvc, String businessName, String email) throws Exception {
        String body = "{\"business_name\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                .formatted(businessName, email, PASSWORD);
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/signup", body))
                .andExpect(status().isCreated())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        return new SignedIn(
                result.getResponse().getCookie("SESSION"),
                JsonPath.read(json, "$.merchant_id"),
                JsonPath.read(json, "$.id"));
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.merchants.*' --tests 'com.fluxpay.identity.*'`
Expected: PASS. The concurrent-signup test relies on `reserveEmail`'s advisory lock: the second request waits for the first to commit, then sees the email and returns 409. Without the lock both requests would race on the `jextter` slug and one would fail with a 500.

- [ ] **Step 8: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(merchants): self-serve signup with owner session and profile branding

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: API keys — issue, list, roll, revoke

**Files:**
- Create: `src/main/resources/db/migration/V3__api_keys.sql`, `src/main/resources/db/rollback/V3__down.sql`
- Create: `src/main/java/com/fluxpay/apikeys/domain/ApiKey.java`, `apikeys/persistence/ApiKeyRepository.java`
- Create: `src/main/java/com/fluxpay/apikeys/service/{ApiKeySecret,ApiKeyPrincipal,ApiKeyView,IssuedApiKey,ApiKeyService,ApiKeyServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/apikeys/api/{ApiKeyController,ApiKeyResponse}.java`
- Modify: `src/test/java/com/fluxpay/support/TestMerchants.java`
- Test: `src/test/java/com/fluxpay/apikeys/{ApiKeySecretTest,ApiKeyManagementIntegrationTest}.java`

**Interfaces:**
- Consumes: `TenantContext`, `Mode`, `Base62`, `PublicId`, `MerchantService.isActive`.
- Produces:
  - Key format: `sk_test_` / `sk_live_` + 44 base62 chars; first 12 of those are the stored `lookup_id`; SHA-256 hex of the full key is stored.
  - `ApiKeySecret.generate(Mode, SecureRandom)`, `ApiKeySecret.parse(String): Optional<ApiKeySecret>`, methods `value()`, `mode()`, `lookupId()`, `sha256Hex()`, `displayPrefix()` (`sk_test_<lookupId>`), `matches(String storedHashHex): boolean` (constant time)
  - `record ApiKeyPrincipal(UUID keyId, UUID merchantId, Mode mode) implements TenantPrincipal` (`fixedMode()` = `Optional.of(mode)`)
  - `record ApiKeyView(UUID id, String displayPrefix, Mode mode, Instant createdAt, Instant lastUsedAt, Instant revokedAt)`
  - `record IssuedApiKey(ApiKeyView key, String secret)`
  - `interface ApiKeyService { IssuedApiKey create(TenantContext); List<ApiKeyView> list(TenantContext); IssuedApiKey roll(TenantContext, UUID keyId); void revoke(TenantContext, UUID keyId); Optional<ApiKeyPrincipal> authenticate(String rawKey); }`
    - `roll`/`revoke` on another tenant's or other mode's key → `notFound("API_KEY_NOT_FOUND")`; `roll` of a revoked key → `conflict("API_KEY_REVOKED")`; `revoke` is idempotent
    - `authenticate`: empty when unparseable/unknown/hash mismatch/revoked/mode mismatch; throws `forbidden("MERCHANT_SUSPENDED")` when the merchant is not active; updates `last_used_at` at most once per minute
  - Routes (dashboard session, mode from `FluxPay-Mode`): `GET /api/v1/dashboard/api_keys`, `POST /api/v1/dashboard/api_keys` → 201 with `secret`, `POST /api/v1/dashboard/api_keys/{id}/roll` → 201 with `secret`, `DELETE /api/v1/dashboard/api_keys/{id}` → 204
  - `TestMerchants.createApiKey(MockMvc, SignedIn, Mode): String` (returns the secret)

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V3__api_keys.sql`:
```sql
CREATE TABLE api_keys (
    id           UUID PRIMARY KEY,
    merchant_id  UUID        NOT NULL REFERENCES merchants (id),
    mode         VARCHAR(4)  NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    lookup_id    VARCHAR(16) NOT NULL UNIQUE,
    secret_hash  VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX api_keys_merchant_mode_idx ON api_keys (merchant_id, mode, created_at DESC);
```

`src/main/resources/db/rollback/V3__down.sql`:
```sql
DROP TABLE IF EXISTS api_keys;
DELETE FROM flyway_schema_history WHERE version = '3';
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/fluxpay/apikeys/ApiKeySecretTest.java`:
```java
package com.fluxpay.apikeys;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.apikeys.service.ApiKeySecret;
import com.fluxpay.common.tenant.Mode;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class ApiKeySecretTest {

    private final SecureRandom random = new SecureRandom();

    @Test
    void should_format_key_with_mode_prefix_and_44_chars_when_generated() {
        ApiKeySecret secret = ApiKeySecret.generate(Mode.LIVE, random);

        assertThat(secret.value()).matches("sk_live_[0-9A-Za-z]{44}");
        assertThat(secret.lookupId()).hasSize(12);
        assertThat(secret.displayPrefix()).isEqualTo("sk_live_" + secret.lookupId());
    }

    @Test
    void should_round_trip_and_match_hash_when_parsed() {
        ApiKeySecret generated = ApiKeySecret.generate(Mode.TEST, random);

        ApiKeySecret parsed = ApiKeySecret.parse(generated.value()).orElseThrow();

        assertThat(parsed.mode()).isEqualTo(Mode.TEST);
        assertThat(parsed.lookupId()).isEqualTo(generated.lookupId());
        assertThat(parsed.matches(generated.sha256Hex())).isTrue();
    }

    @Test
    void should_not_match_when_hash_belongs_to_another_key() {
        ApiKeySecret one = ApiKeySecret.generate(Mode.TEST, random);
        ApiKeySecret two = ApiKeySecret.generate(Mode.TEST, random);

        assertThat(one.matches(two.sha256Hex())).isFalse();
    }

    @Test
    void should_return_empty_when_parsing_malformed_keys() {
        assertThat(ApiKeySecret.parse(null)).isEmpty();
        assertThat(ApiKeySecret.parse("")).isEmpty();
        assertThat(ApiKeySecret.parse("sk_prod_" + "a".repeat(44))).isEmpty();
        assertThat(ApiKeySecret.parse("sk_test_" + "a".repeat(43))).isEmpty();
        assertThat(ApiKeySecret.parse("sk_test_" + "!".repeat(44))).isEmpty();
        assertThat(ApiKeySecret.parse("pk_test_" + "a".repeat(44))).isEmpty();
    }
}
```

`src/test/java/com/fluxpay/apikeys/ApiKeyManagementIntegrationTest.java`:
```java
package com.fluxpay.apikeys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class ApiKeyManagementIntegrationTest extends AbstractIntegrationTest {

    private static final String KEYS = "/api/v1/dashboard/api_keys";

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private MvcResult create(String mode) throws Exception {
        return mockMvc.perform(post(KEYS).with(csrf()).cookie(owner.session()).header("FluxPay-Mode", mode))
                .andExpect(status().isCreated())
                .andReturn();
    }

    @Test
    void should_return_secret_once_and_hide_it_in_list_when_key_is_created() throws Exception {
        MvcResult created = create("test");
        String json = created.getResponse().getContentAsString();

        assertThat((String) JsonPath.read(json, "$.secret")).startsWith("sk_test_");
        assertThat((String) JsonPath.read(json, "$.id")).startsWith("key_");
        mockMvc.perform(get(KEYS).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                .andExpect(jsonPath("$.data[0].display_prefix").value(org.hamcrest.Matchers.startsWith("sk_test_")));
    }

    @Test
    void should_list_only_keys_of_requested_mode() throws Exception {
        create("test");
        create("live");

        mockMvc.perform(get(KEYS).cookie(owner.session()).header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].mode").value("live"));
    }

    @Test
    void should_revoke_old_key_and_issue_new_one_when_rolled() throws Exception {
        String id = JsonPath.read(create("test").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post(KEYS + "/" + id + "/roll").with(csrf()).cookie(owner.session()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value(org.hamcrest.Matchers.startsWith("sk_test_")));

        mockMvc.perform(get(KEYS).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == '" + id + "')].revoked_at").isNotEmpty());
    }

    @Test
    void should_be_idempotent_when_revoking_twice_and_conflict_when_rolling_revoked() throws Exception {
        String id = JsonPath.read(create("test").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete(KEYS + "/" + id).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(KEYS + "/" + id).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(KEYS + "/" + id + "/roll").with(csrf()).cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("API_KEY_REVOKED"));
    }

    @Test
    void should_return_404_when_key_id_is_malformed_or_belongs_to_other_mode() throws Exception {
        String liveId = JsonPath.read(create("live").getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete(KEYS + "/not-a-key").with(csrf()).cookie(owner.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(KEYS + "/" + liveId).with(csrf()).cookie(owner.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_400_when_mode_header_is_invalid() throws Exception {
        mockMvc.perform(get(KEYS).cookie(owner.session()).header("FluxPay-Mode", "prod"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_MODE"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.apikeys.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement domain and persistence**

`src/main/java/com/fluxpay/apikeys/domain/ApiKey.java`:
```java
package com.fluxpay.apikeys.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    private static final Duration LAST_USED_GRANULARITY = Duration.ofMinutes(1);

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "lookup_id", nullable = false)
    private String lookupId;

    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {}

    public ApiKey(UUID merchantId, Mode mode, String lookupId, String secretHash, Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.lookupId = lookupId;
        this.secretHash = secretHash;
        this.createdAt = now;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void markUsed(Instant now) {
        if (lastUsedAt == null || lastUsedAt.plus(LAST_USED_GRANULARITY).isBefore(now)) {
            lastUsedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public String getLookupId() {
        return lookupId;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
```

`src/main/java/com/fluxpay/apikeys/persistence/ApiKeyRepository.java`:
```java
package com.fluxpay.apikeys.persistence;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped finders only: every lookup a controller can trigger includes merchant and mode. */
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByMerchantIdAndModeOrderByCreatedAtDesc(UUID merchantId, Mode mode);

    Optional<ApiKey> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    Optional<ApiKey> findByLookupId(String lookupId);
}
```

- [ ] **Step 5: Implement the service layer**

`src/main/java/com/fluxpay/apikeys/service/ApiKeySecret.java`:
```java
package com.fluxpay.apikeys.service;

import com.fluxpay.common.id.Base62;
import com.fluxpay.common.tenant.Mode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A plaintext API key. Only its SHA-256 hash and lookup id are ever stored. */
public final class ApiKeySecret {

    static final int RANDOM_LENGTH = 44;
    static final int LOOKUP_LENGTH = 12;
    private static final Pattern FORMAT = Pattern.compile("^sk_(test|live)_([0-9A-Za-z]{" + RANDOM_LENGTH + "})$");

    private final String value;
    private final Mode mode;
    private final String lookupId;

    private ApiKeySecret(String value, Mode mode, String lookupId) {
        this.value = value;
        this.mode = mode;
        this.lookupId = lookupId;
    }

    public static ApiKeySecret generate(Mode mode, SecureRandom random) {
        String body = Base62.random(RANDOM_LENGTH, random);
        return new ApiKeySecret(prefix(mode) + body, mode, body.substring(0, LOOKUP_LENGTH));
    }

    public static Optional<ApiKeySecret> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(raw);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Mode mode = Mode.parse(matcher.group(1)).orElseThrow();
        return Optional.of(new ApiKeySecret(raw, mode, matcher.group(2).substring(0, LOOKUP_LENGTH)));
    }

    public String value() {
        return value;
    }

    public Mode mode() {
        return mode;
    }

    public String lookupId() {
        return lookupId;
    }

    public String displayPrefix() {
        return prefix(mode) + lookupId;
    }

    public String sha256Hex() {
        return HexFormat.of().formatHex(sha256(value));
    }

    public boolean matches(String storedHashHex) {
        return MessageDigest.isEqual(
                sha256Hex().getBytes(StandardCharsets.US_ASCII), storedHashHex.getBytes(StandardCharsets.US_ASCII));
    }

    static String prefix(Mode mode) {
        return "sk_" + mode.value() + "_";
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```

`src/main/java/com/fluxpay/apikeys/service/ApiKeyPrincipal.java`:
```java
package com.fluxpay.apikeys.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantPrincipal;
import java.util.Optional;
import java.util.UUID;

public record ApiKeyPrincipal(UUID keyId, UUID merchantId, Mode mode) implements TenantPrincipal {

    @Override
    public Optional<Mode> fixedMode() {
        return Optional.of(mode);
    }
}
```

`src/main/java/com/fluxpay/apikeys/service/ApiKeyView.java`:
```java
package com.fluxpay.apikeys.service;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

public record ApiKeyView(
        UUID id, String displayPrefix, Mode mode, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {

    static ApiKeyView from(ApiKey key) {
        return new ApiKeyView(
                key.getId(),
                ApiKeySecret.prefix(key.getMode()) + key.getLookupId(),
                key.getMode(),
                key.getCreatedAt(),
                key.getLastUsedAt(),
                key.getRevokedAt());
    }
}
```

`src/main/java/com/fluxpay/apikeys/service/IssuedApiKey.java`:
```java
package com.fluxpay.apikeys.service;

/** A newly created key. {@code secret} is shown to the merchant once and never stored. */
public record IssuedApiKey(ApiKeyView key, String secret) {}
```

`src/main/java/com/fluxpay/apikeys/service/ApiKeyService.java`:
```java
package com.fluxpay.apikeys.service;

import com.fluxpay.common.tenant.TenantContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyService {

    IssuedApiKey create(TenantContext tenant);

    List<ApiKeyView> list(TenantContext tenant);

    /** Revokes the key and issues a replacement. API_KEY_REVOKED (conflict) if already revoked. */
    IssuedApiKey roll(TenantContext tenant, UUID keyId);

    /** Idempotent. API_KEY_NOT_FOUND (not found) when the key is not in this tenant and mode. */
    void revoke(TenantContext tenant, UUID keyId);

    /** Empty for any invalid, unknown or revoked key. MERCHANT_SUSPENDED (forbidden) for suspended merchants. */
    Optional<ApiKeyPrincipal> authenticate(String rawKey);
}
```

`src/main/java/com/fluxpay/apikeys/service/ApiKeyServiceImpl.java`:
```java
package com.fluxpay.apikeys.service;

import com.fluxpay.apikeys.domain.ApiKey;
import com.fluxpay.apikeys.persistence.ApiKeyRepository;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeys;
    private final MerchantService merchantService;
    private final Clock clock;

    public ApiKeyServiceImpl(ApiKeyRepository apiKeys, MerchantService merchantService, Clock clock) {
        this.apiKeys = apiKeys;
        this.merchantService = merchantService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedApiKey create(TenantContext tenant) {
        ApiKeySecret secret = ApiKeySecret.generate(tenant.mode(), RANDOM);
        ApiKey key = apiKeys.save(new ApiKey(
                tenant.merchantId(), tenant.mode(), secret.lookupId(), secret.sha256Hex(), Instant.now(clock)));
        return new IssuedApiKey(ApiKeyView.from(key), secret.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyView> list(TenantContext tenant) {
        return apiKeys.findByMerchantIdAndModeOrderByCreatedAtDesc(tenant.merchantId(), tenant.mode()).stream()
                .map(ApiKeyView::from)
                .toList();
    }

    @Override
    @Transactional
    public IssuedApiKey roll(TenantContext tenant, UUID keyId) {
        ApiKey existing = find(tenant, keyId);
        if (existing.isRevoked()) {
            throw FluxpayException.conflict("API_KEY_REVOKED", "A revoked key cannot be rolled");
        }
        existing.revoke(Instant.now(clock));
        return create(tenant);
    }

    @Override
    @Transactional
    public void revoke(TenantContext tenant, UUID keyId) {
        find(tenant, keyId).revoke(Instant.now(clock));
    }

    @Override
    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String rawKey) {
        Optional<ApiKeySecret> parsed = ApiKeySecret.parse(rawKey);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        ApiKeySecret secret = parsed.get();
        Optional<ApiKey> stored = apiKeys.findByLookupId(secret.lookupId())
                .filter(key -> secret.matches(key.getSecretHash()))
                .filter(key -> !key.isRevoked())
                .filter(key -> key.getMode() == secret.mode());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        ApiKey key = stored.get();
        if (!merchantService.isActive(key.getMerchantId())) {
            throw FluxpayException.forbidden("MERCHANT_SUSPENDED", "This merchant account is suspended");
        }
        key.markUsed(Instant.now(clock));
        return Optional.of(new ApiKeyPrincipal(key.getId(), key.getMerchantId(), key.getMode()));
    }

    private ApiKey find(TenantContext tenant, UUID keyId) {
        return apiKeys.findByIdAndMerchantIdAndMode(keyId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("API_KEY_NOT_FOUND", "API key not found"));
    }
}
```

- [ ] **Step 6: Implement the dashboard API**

`src/main/java/com/fluxpay/apikeys/api/ApiKeyResponse.java`:
```java
package com.fluxpay.apikeys.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fluxpay.apikeys.service.ApiKeyView;
import com.fluxpay.apikeys.service.IssuedApiKey;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record ApiKeyResponse(
        String id,
        String displayPrefix,
        Mode mode,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String secret) {

    public static ApiKeyResponse from(ApiKeyView view) {
        return new ApiKeyResponse(
                PublicId.of(IdPrefix.API_KEY, view.id()),
                view.displayPrefix(),
                view.mode(),
                view.createdAt(),
                view.lastUsedAt(),
                view.revokedAt(),
                null);
    }

    public static ApiKeyResponse from(IssuedApiKey issued) {
        ApiKeyResponse base = from(issued.key());
        return new ApiKeyResponse(
                base.id(),
                base.displayPrefix(),
                base.mode(),
                base.createdAt(),
                base.lastUsedAt(),
                base.revokedAt(),
                issued.secret());
    }
}
```

`src/main/java/com/fluxpay/apikeys/api/ApiKeyController.java`:
```java
package com.fluxpay.apikeys.api;

import com.fluxpay.apikeys.service.ApiKeyService;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/api_keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public Map<String, List<ApiKeyResponse>> list(TenantContext tenant) {
        return Map.of(
                "data",
                apiKeyService.list(tenant).stream().map(ApiKeyResponse::from).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(TenantContext tenant) {
        return ApiKeyResponse.from(apiKeyService.create(tenant));
    }

    @PostMapping("/{id}/roll")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse roll(TenantContext tenant, @PathVariable String id) {
        return ApiKeyResponse.from(apiKeyService.roll(tenant, parseId(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(TenantContext tenant, @PathVariable String id) {
        apiKeyService.revoke(tenant, parseId(id));
    }

    private static UUID parseId(String id) {
        return PublicId.parse(IdPrefix.API_KEY, id)
                .orElseThrow(() -> FluxpayException.notFound("API_KEY_NOT_FOUND", "API key not found"));
    }
}
```

- [ ] **Step 7: Add the key fixture to `TestMerchants`**

Add this import and method to `src/test/java/com/fluxpay/support/TestMerchants.java` (`MockMvcRequestBuilders` is already imported):
```java
import com.fluxpay.common.tenant.Mode;

    public static String createApiKey(MockMvc mockMvc, SignedIn merchant, Mode mode) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/dashboard/api_keys")
                        .with(csrf())
                        .cookie(merchant.session())
                        .header("FluxPay-Mode", mode.value()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.secret");
    }
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.apikeys.*'`
Expected: PASS (10 tests).

- [ ] **Step 9: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(apikeys): hashed test and live api keys with roll and revoke

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: API key authentication, account endpoint, tenant isolation suite

**Files:**
- Create: `src/main/java/com/fluxpay/apikeys/api/{ApiKeyAuthenticationFilter,AccountController,AccountResponse}.java`
- Modify: `src/main/java/com/fluxpay/config/SecurityConfig.java` (register the filter)
- Test: `src/test/java/com/fluxpay/apikeys/{ApiKeyAuthenticationIntegrationTest,TenantIsolationIntegrationTest}.java`

**Interfaces:**
- Consumes: `ApiKeyService.authenticate`, `ErrorResponseWriter`, `MerchantService.get`, `SecurityConfig.SESSION_PREFIXES`.
- Produces:
  - `ApiKeyAuthenticationFilter(ApiKeyService, ErrorResponseWriter)` — not a `@Component` (constructed in `SecurityConfig`); skips paths outside `/api/v1/` and under `/api/v1/{auth,dashboard,admin,public,gateway-webhooks}/`; no `Authorization` header → passes through; any invalid header/key → 401 `INVALID_API_KEY`; suspended merchant → 403 `MERCHANT_SUSPENDED`; valid key → authentication with authority `ROLE_API_KEY` and principal `ApiKeyPrincipal`
  - `GET /api/v1/account` (API key) → `{ "merchant_id", "business_name", "mode" }`
  - `TenantIsolationIntegrationTest` — the suite every later plan extends with its endpoints

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/apikeys/ApiKeyAuthenticationIntegrationTest.java`:
```java
package com.fluxpay.apikeys;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ApiKeyAuthenticationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    @Test
    void should_return_account_with_key_mode_when_live_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant_id").value(owner.merchantId()))
                .andExpect(jsonPath("$.business_name").value("Jextter"))
                .andExpect(jsonPath("$.mode").value("live"));
    }

    @Test
    void should_ignore_mode_header_when_authenticated_with_api_key() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/account")
                        .header("Authorization", "Bearer " + key)
                        .header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.mode").value("test"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Bearer",
                "Bearer ",
                "Basic dXNlcjpwYXNz",
                "random-string",
                "Bearer sk_test_tooShort",
                "Bearer sk_test_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            })
    void should_return_401_invalid_api_key_when_header_is_malformed_or_unknown(String header) throws Exception {
        mockMvc.perform(get("/api/v1/account").header("Authorization", header))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void should_return_401_when_no_credentials_are_sent() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return_401_when_revoked_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        jdbcTemplate.update("UPDATE api_keys SET revoked_at = now()");

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_API_KEY"));
    }

    @Test
    void should_return_403_when_merchant_is_suspended() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        jdbcTemplate.update("UPDATE merchants SET status = 'SUSPENDED'");

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MERCHANT_SUSPENDED"));
    }

    @Test
    void should_not_authenticate_dashboard_routes_with_api_key() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/dashboard/merchant").header("Authorization", "Bearer " + key))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void should_not_authenticate_api_routes_with_dashboard_session() throws Exception {
        mockMvc.perform(get("/api/v1/account").cookie(owner.session())).andExpect(status().isForbidden());
    }

    @Test
    void should_record_last_used_time_when_key_is_used() throws Exception {
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + key));

        Integer used = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM api_keys WHERE last_used_at IS NOT NULL", Integer.class);
        org.assertj.core.api.Assertions.assertThat(used).isEqualTo(1);
    }
}
```

`src/test/java/com/fluxpay/apikeys/TenantIsolationIntegrationTest.java`:
```java
package com.fluxpay.apikeys;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Merchant A must never see or change merchant B's data. Every plan adds its endpoints here.
 */
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    private SignedIn merchantA;
    private SignedIn merchantB;
    private String keyIdOfB;

    @BeforeEach
    void setUp() throws Exception {
        merchantA = TestMerchants.signUp(mockMvc, "Alpha", "a@alpha.com");
        merchantB = TestMerchants.signUp(mockMvc, "Beta", "b@beta.com");
        String created = mockMvc.perform(post("/api/v1/dashboard/api_keys")
                        .with(csrf())
                        .cookie(merchantB.session()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        keyIdOfB = JsonPath.read(created, "$.id");
    }

    @Test
    void should_not_list_other_merchants_keys() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/api_keys").cookie(merchantA.session()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_return_404_when_revoking_other_merchants_key() throws Exception {
        mockMvc.perform(delete("/api/v1/dashboard/api_keys/" + keyIdOfB)
                        .with(csrf())
                        .cookie(merchantA.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_404_when_rolling_other_merchants_key() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/api_keys/" + keyIdOfB + "/roll")
                        .with(csrf())
                        .cookie(merchantA.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_own_merchant_profile_only() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/merchant").cookie(merchantA.session()))
                .andExpect(jsonPath("$.id").value(merchantA.merchantId()));
    }

    @Test
    void should_resolve_account_to_key_owner() throws Exception {
        String keyOfA = TestMerchants.createApiKey(mockMvc, merchantA, Mode.TEST);

        mockMvc.perform(get("/api/v1/account").header("Authorization", "Bearer " + keyOfA))
                .andExpect(jsonPath("$.merchant_id").value(merchantA.merchantId()));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.apikeys.ApiKeyAuthenticationIntegrationTest' --tests 'com.fluxpay.apikeys.TenantIsolationIntegrationTest'`
Expected: FAIL — compilation errors (`AccountController` missing).

- [ ] **Step 3: Implement the filter and account endpoint**

`src/main/java/com/fluxpay/apikeys/api/ApiKeyAuthenticationFilter.java`:
```java
package com.fluxpay.apikeys.api;

import com.fluxpay.apikeys.service.ApiKeyPrincipal;
import com.fluxpay.apikeys.service.ApiKeyService;
import com.fluxpay.common.error.ErrorResponseWriter;
import com.fluxpay.common.error.FluxpayException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates merchant API calls sent with {@code Authorization: Bearer sk_...}. */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final List<String> NON_API_KEY_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/dashboard/",
            "/api/v1/admin/",
            "/api/v1/public/",
            "/api/v1/gateway-webhooks/");

    private final ApiKeyService apiKeyService;
    private final ErrorResponseWriter errorWriter;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ErrorResponseWriter errorWriter) {
        this.apiKeyService = apiKeyService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || NON_API_KEY_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!header.startsWith(BEARER)) {
            rejectInvalidKey(response);
            return;
        }
        Optional<ApiKeyPrincipal> principal;
        try {
            principal = apiKeyService.authenticate(header.substring(BEARER.length()).trim());
        } catch (FluxpayException e) {
            errorWriter.write(response, e);
            return;
        }
        if (principal.isEmpty()) {
            rejectInvalidKey(response);
            return;
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal.get(), null, List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private void rejectInvalidKey(HttpServletResponse response) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid API key");
    }
}
```

`src/main/java/com/fluxpay/apikeys/api/AccountResponse.java`:
```java
package com.fluxpay.apikeys.api;

import com.fluxpay.common.tenant.Mode;

public record AccountResponse(String merchantId, String businessName, Mode mode) {}
```

`src/main/java/com/fluxpay/apikeys/api/AccountController.java`:
```java
package com.fluxpay.apikeys.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.MerchantView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets an integration verify which merchant and mode its API key belongs to. */
@RestController
public class AccountController {

    private final MerchantService merchantService;

    public AccountController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/api/v1/account")
    public AccountResponse account(TenantContext tenant) {
        MerchantView merchant = merchantService.get(tenant.merchantId());
        return new AccountResponse(
                PublicId.of(IdPrefix.MERCHANT, merchant.id()), merchant.businessName(), tenant.mode());
    }
}
```

- [ ] **Step 4: Register the filter in `SecurityConfig`**

In `src/main/java/com/fluxpay/config/SecurityConfig.java`:
1. Add imports:
```java
import com.fluxpay.apikeys.api.ApiKeyAuthenticationFilter;
import com.fluxpay.apikeys.service.ApiKeyService;
import com.fluxpay.common.error.ErrorResponseWriter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
```
2. Add two parameters to `securityFilterChain(...)`: `ApiKeyService apiKeyService, ErrorResponseWriter errorWriter`.
3. Before `return http.build();` add:
```java
        http.addFilterAfter(
                new ApiKeyAuthenticationFilter(apiKeyService, errorWriter), SecurityContextHolderFilter.class);
```

- [ ] **Step 5: Run the full suite**

Run: `./gradlew test`
Expected: PASS (all tests so far). `should_not_authenticate_api_routes_with_dashboard_session` returns 403 because the session principal lacks `ROLE_API_KEY`.

- [ ] **Step 6: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(apikeys): bearer api key authentication, account endpoint and tenant isolation suite

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Rate limiting on auth routes

**Files:**
- Create: `src/main/java/com/fluxpay/common/ratelimit/{RateLimitProperties,RateLimitDecision,RateLimiter,RateLimitFilter}.java`
- Modify: `src/main/resources/application.yml`, `src/test/resources/application-test.yml`
- Test: `src/test/java/com/fluxpay/common/ratelimit/{RateLimiterTest,RateLimitIntegrationTest}.java`

**Interfaces:**
- Consumes: `ErrorResponseWriter`, `FluxpayException.rateLimited`.
- Produces:
  - `record RateLimitProperties(List<Rule> rules)` under `fluxpay.rate-limit`, `record Rule(String name, String method, List<String> paths, int capacity, Duration period)` — `paths` are Spring `PathPattern`s; Plan 2 adds rules for public checkout routes by config only
  - `record RateLimitDecision(boolean allowed, long limit, long remaining, long resetSeconds)`
  - `RateLimiter.tryConsume(String clientKey, Rule rule): RateLimitDecision`
  - `RateLimitFilter` (servlet filter, ordered right after `CorrelationIdFilter`) sets `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` on matching requests; on exhaustion 429 `RATE_LIMITED` + `Retry-After`

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/fluxpay/common/ratelimit/RateLimiterTest.java`:
```java
package com.fluxpay.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final RateLimitProperties.Rule rule =
            new RateLimitProperties.Rule("auth", "POST", List.of("/api/v1/auth/login"), 3, Duration.ofMinutes(1));
    private final RateLimiter limiter = new RateLimiter();

    @Test
    void should_allow_up_to_capacity_then_reject() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume("1.2.3.4", rule).allowed()).isTrue();
        }

        RateLimitDecision rejected = limiter.tryConsume("1.2.3.4", rule);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.resetSeconds()).isPositive();
    }

    @Test
    void should_track_clients_independently() {
        for (int i = 0; i < 3; i++) {
            limiter.tryConsume("1.2.3.4", rule);
        }

        assertThat(limiter.tryConsume("5.6.7.8", rule).allowed()).isTrue();
    }

    @Test
    void should_report_remaining_tokens_when_allowed() {
        RateLimitDecision first = limiter.tryConsume("1.2.3.4", rule);

        assertThat(first.limit()).isEqualTo(3);
        assertThat(first.remaining()).isEqualTo(2);
    }
}
```

`src/test/java/com/fluxpay/common/ratelimit/RateLimitIntegrationTest.java`:
```java
package com.fluxpay.common.ratelimit;

import static com.fluxpay.support.TestMerchants.jsonPost;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

// Spring replaces a whole list from the highest-priority source, so the full rule is restated here.
@TestPropertySource(
        properties = {
            "fluxpay.rate-limit.rules[0].name=auth",
            "fluxpay.rate-limit.rules[0].method=POST",
            "fluxpay.rate-limit.rules[0].paths[0]=/api/v1/auth/login",
            "fluxpay.rate-limit.rules[0].capacity=2",
            "fluxpay.rate-limit.rules[0].period=1m"
        })
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    private static final String BODY = "{\"email\":\"x@y.com\",\"password\":\"whatever-123\"}";

    @Test
    void should_return_429_with_headers_when_login_limit_is_exceeded() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY))
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"));
        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY));

        mockMvc.perform(jsonPost("/api/v1/auth/login", BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.ratelimit.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 3: Implement**

`src/main/java/com/fluxpay/common/ratelimit/RateLimitProperties.java`:
```java
package com.fluxpay.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Per-client-IP limits. In-process (ADR-004, TD-003): limits apply per backend instance. */
@Validated
@ConfigurationProperties("fluxpay.rate-limit")
public record RateLimitProperties(@Valid List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public record Rule(
            @NotBlank String name,
            @NotBlank String method,
            @NotEmpty List<String> paths,
            @Min(1) int capacity,
            @NotNull Duration period) {}
}
```

`src/main/java/com/fluxpay/common/ratelimit/RateLimitDecision.java`:
```java
package com.fluxpay.common.ratelimit;

public record RateLimitDecision(boolean allowed, long limit, long remaining, long resetSeconds) {}
```

`src/main/java/com/fluxpay/common/ratelimit/RateLimiter.java`:
```java
package com.fluxpay.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RateLimiter {

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    public RateLimitDecision tryConsume(String clientKey, RateLimitProperties.Rule rule) {
        Bucket bucket = buckets.get(rule.name() + ":" + clientKey, key -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        long resetSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        return new RateLimitDecision(
                probe.isConsumed(), rule.capacity(), probe.getRemainingTokens(), probe.isConsumed() ? 0 : resetSeconds);
    }

    private static Bucket newBucket(RateLimitProperties.Rule rule) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rule.capacity())
                        .refillGreedy(rule.capacity(), rule.period())
                        .build())
                .build();
    }
}
```

`src/main/java/com/fluxpay/common/ratelimit/RateLimitFilter.java`:
```java
package com.fluxpay.common.ratelimit;

import com.fluxpay.common.error.ErrorResponseWriter;
import com.fluxpay.common.error.FluxpayException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private record CompiledRule(RateLimitProperties.Rule rule, List<PathPattern> patterns) {}

    private final List<CompiledRule> rules;
    private final RateLimiter limiter = new RateLimiter();
    private final ErrorResponseWriter errorWriter;

    public RateLimitFilter(RateLimitProperties properties, ErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
        this.rules = properties.rules().stream()
                .map(rule -> new CompiledRule(
                        rule,
                        rule.paths().stream().map(PathPatternParser.defaultInstance::parse).toList()))
                .toList();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<CompiledRule> match = findRule(request);
        if (match.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitDecision decision = limiter.tryConsume(request.getRemoteAddr(), match.get().rule());
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetSeconds()));
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.resetSeconds()));
            errorWriter.write(response, FluxpayException.rateLimited("Too many requests, retry later"));
            return;
        }
        chain.doFilter(request, response);
    }

    private Optional<CompiledRule> findRule(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return rules.stream()
                .filter(compiled -> compiled.rule().method().equalsIgnoreCase(request.getMethod()))
                .filter(compiled -> compiled.patterns().stream().anyMatch(pattern -> pattern.matches(path)))
                .findFirst();
    }
}
```

Append to `application.yml` under `fluxpay:`:
```yaml
  rate-limit:
    rules:
      - name: auth
        method: POST
        paths: [/api/v1/auth/login, /api/v1/auth/signup]
        capacity: 10
        period: 1m
```

Append to `src/test/resources/application-test.yml` under `fluxpay:` (so other tests never hit the limit):
```yaml
  rate-limit:
    rules:
      - name: auth
        method: POST
        paths: [/api/v1/auth/login, /api/v1/auth/signup]
        capacity: 10000
        period: 1m
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test`
Expected: PASS (full suite).

- [ ] **Step 5: Commit**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): configurable per-ip rate limiting with rate limit headers on auth routes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Architecture guards, ProjectOS memory, branch push

**Files:**
- Test: `src/test/java/com/fluxpay/architecture/{ModuleBoundariesTest,ProjectOsConstraintsTest}.java`
- Modify: `.engineering/memory/features.yaml`, `README.md`

**Interfaces:**
- Consumes: the module map in `.engineering/config/architecture.yaml` (duplicated as a constant — keep both in sync).
- Produces: build fails if any module imports a module it may not depend on, if any `api` package touches `persistence` or `@Entity` types, or if a controller/service file exceeds ProjectOS line limits.

- [ ] **Step 1: Write the guard tests**

`src/test/java/com/fluxpay/architecture/ModuleBoundariesTest.java`:
```java
package com.fluxpay.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import jakarta.persistence.Entity;
import java.util.Map;
import java.util.Set;

/** Mirrors .engineering/config/architecture.yaml. Update both together. */
@AnalyzeClasses(packages = "com.fluxpay", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
            Map.entry("common", Set.of()),
            Map.entry("identity", Set.of("common")),
            Map.entry("merchants", Set.of("common", "identity")),
            Map.entry("apikeys", Set.of("common", "merchants")),
            Map.entry("catalog", Set.of("common", "merchants")),
            Map.entry("payments", Set.of("common")),
            Map.entry("checkout", Set.of("common", "merchants", "catalog", "payments")),
            Map.entry("ledger", Set.of("common", "merchants")),
            Map.entry("events", Set.of("common", "merchants")),
            Map.entry("sales", Set.of("common", "checkout", "payments", "ledger", "events")),
            Map.entry("analytics", Set.of("common", "sales", "ledger")),
            Map.entry("admin", Set.of("common", "merchants", "ledger")));

    @ArchTest
    static void modules_depend_only_on_declared_modules(JavaClasses classes) {
        ALLOWED.forEach((module, allowed) -> {
            String[] forbidden = ALLOWED.keySet().stream()
                    .filter(other -> !other.equals(module) && !allowed.contains(other))
                    .map(other -> "com.fluxpay." + other + "..")
                    .toArray(String[]::new);
            noClasses()
                    .that()
                    .resideInAPackage("com.fluxpay." + module + "..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(forbidden)
                    .allowEmptyShould(true)
                    .because("module boundaries are declared in .engineering/config/architecture.yaml")
                    .check(classes);
        });
    }

    @ArchTest
    static void modules_never_depend_on_app_config(JavaClasses classes) {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.fluxpay.config..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.fluxpay.config..")
                .check(classes);
    }

    @ArchTest
    static void api_layer_never_touches_repositories_or_entities(JavaClasses classes) {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..persistence..")
                .orShould()
                .dependOnClassesThat()
                .areAnnotatedWith(Entity.class)
                .check(classes);
    }
}
```

`src/test/java/com/fluxpay/architecture/ProjectOsConstraintsTest.java`:
```java
package com.fluxpay.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enforces .engineering/config/constraints.yaml line limits. */
class ProjectOsConstraintsTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static List<String> filesOver(String nameFragment, int maxLines) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.getFileName().toString().contains(nameFragment))
                    .filter(path -> lineCount(path) > maxLines)
                    .map(Path::toString)
                    .toList();
        }
    }

    private static long lineCount(Path path) {
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_keep_controllers_within_300_lines() throws IOException {
        assertThat(filesOver("Controller", 300)).isEmpty();
    }

    @Test
    void should_keep_services_within_500_lines() throws IOException {
        assertThat(filesOver("Service", 500)).isEmpty();
    }
}
```

The `ApiKeyAuthenticationFilter` lives in `apikeys.api` and must not import `com.fluxpay.config` — it does not, so these pass. `SecurityConfig` depends on modules, which is allowed for `config`.

- [ ] **Step 2: Run them**

Run: `./gradlew test --tests 'com.fluxpay.architecture.*'`
Expected: PASS. If `api_layer_never_touches_repositories_or_entities` fails, a controller or response imports a repository or entity — move that mapping into the service layer (views like `MerchantView`) rather than weakening the rule.

- [ ] **Step 3: Update ProjectOS memory and README**

Replace `.engineering/memory/features.yaml` with:
```yaml
# Completed Features Registry
# High-level index of built capabilities. Updated as each feature PR merges.

features:
  - name: "Backend foundation"
    description: "Spring Boot skeleton, /health/live and /health/ready, Flyway, error envelope, correlation ids, UUIDv7 prefixed public ids."
    modules: ["src/main/java/com/fluxpay/common"]
  - name: "Merchant signup and dashboard sessions"
    description: "Self-serve signup creates a merchant and owner; session login/logout/me with CSRF; platform admin bootstrap from env."
    modules: ["src/main/java/com/fluxpay/identity", "src/main/java/com/fluxpay/merchants"]
  - name: "Merchant profile"
    description: "Business name, logo URL and brand colour editable from the dashboard."
    modules: ["src/main/java/com/fluxpay/merchants"]
  - name: "API keys"
    description: "Hashed sk_test_/sk_live_ keys with create, list, roll, revoke; Bearer authentication; GET /api/v1/account."
    modules: ["src/main/java/com/fluxpay/apikeys"]
  - name: "Auth rate limiting"
    description: "Configurable per-IP limits with X-RateLimit headers; 429 RATE_LIMITED."
    modules: ["src/main/java/com/fluxpay/common/ratelimit"]
```

Append to `README.md`:
```markdown

## Local development

1. Start Docker Desktop (integration tests use Testcontainers).
2. `cp .env.example .env` and fill in a Postgres connection (or run `docker run -p 5432:5432 -e POSTGRES_USER=fluxpay -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=fluxpay postgres:17-alpine`).
3. `set -a; source .env; set +a; ./gradlew bootRun`
4. `./gradlew spotlessApply build` before every commit.

Health: `GET /health/live`, `GET /health/ready`.
```

- [ ] **Step 4: Full verification**

Run: `./gradlew spotlessCheck build`
Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 5: Commit and push**

```bash
git add -A
git commit -m "test(architecture): enforce module boundaries and projectos line limits

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```
