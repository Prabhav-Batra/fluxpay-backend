# Spring Boot Plugin

## Registration
- ID: `projectos.plugins.springboot`
- Version: `1.0.0`
- Compatibility: `Java 17+`, `Spring Boot 3.x`

## Capabilities
- **Standards**: Injects Spring Architecture and Java Style Guide.
- **Templates**: Provides `@RestController`, `@Service`, `@Repository` scaffolds.
- **Workflows**: Hooks into `maven build` or `gradle build`.
- **Knowledge Packs**: Injects the `spring-boot` tech pack from the registry.

## Core Rules
1. **Constructor Injection**: Always use constructor injection for dependencies. Never use `@Autowired` on fields. This makes classes easier to unit test without Spring context.
2. **Configuration Colocation**: Use `@ConfigurationProperties` to bind properties to strongly typed beans instead of scattering `@Value` annotations everywhere.
3. **Transaction Boundaries**: Apply `@Transactional` at the Service layer, not the Repository layer, to ensure multiple repository calls form a single atomic unit of work.
4. **Actuator**: Always include `spring-boot-starter-actuator` for health checks, metrics, and observability. Secure the actuator endpoints appropriately.

## Common Anti-Patterns
- ❌ Putting business logic in `@RestController` classes.
- ❌ Returning JPA `@Entity` classes directly from controllers. Always map to a Data Transfer Object (DTO) to avoid leaking database schema and lazy loading exceptions.
- ❌ Catching standard exceptions globally without returning proper `ProblemDetail` or RFC-7807 compliant error responses.

## Dependencies
- Requires: Java SDK 17+, Maven/Gradle.
- Links to Pack: `registry/packs/spring-boot/manifest.yaml`
