# Spring Boot Best Practices

1. **Constructor Injection**: Never use `@Autowired` on fields. Always use constructor injection. This makes the class testable without a Spring context and enforces dependency immutability (using `final`).
2. **Layered Architecture**: Strictly separate `@RestController` (Presentation), `@Service` (Business Logic), and `@Repository` (Data Access). Controllers should not contain business rules.
3. **Profiles for Configuration**: Use `@Profile` and `application-{env}.yml` to manage environment-specific configurations (e.g., local, staging, prod) cleanly.
4. **Validation**: Use Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Size`) on DTOs and Controller parameters to enforce input constraints before the request reaches the Service layer.
5. **Global Exception Handling**: Use `@RestControllerAdvice` and `@ExceptionHandler` to catch exceptions globally and map them to consistent RFC-7807 `ProblemDetail` responses.
6. **Actuator & Observability**: Always include `spring-boot-starter-actuator` for `/health`, `/metrics`, and `/info` endpoints. Integrate with Micrometer for Prometheus/Datadog metrics.
7. **Testing**: Use `@WebMvcTest` for isolated controller tests, `@DataJpaTest` for repository tests, and `@SpringBootTest` strictly for full integration tests. Use Testcontainers for real database tests.
8. **Configuration Properties**: Use `@ConfigurationProperties` to strongly type your application properties rather than scattering `@Value("${property}")` throughout the codebase.
9. **Pagination**: Use `Pageable` and `Page<T>` in Spring Data for endpoints returning collections to enforce limits automatically.
10. **Lombok**: Use `@RequiredArgsConstructor` alongside `final` fields to auto-generate constructor injection boilerplate.
