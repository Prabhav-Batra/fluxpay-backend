# Spring Boot Pitfalls

1. ❌ **Field Injection**: Using `@Autowired` on private fields. It makes unit testing impossible without reflection and hides circular dependencies until runtime.
2. ❌ **God Services**: Creating a single `UserService` that handles registration, billing, avatar uploads, and email sending. Split it into focused, smaller services.
3. ❌ **Entity Leakage**: Returning raw JPA `@Entity` classes directly from the `@RestController`. This leaks database structure, exposes sensitive fields, and causes Jackson serialization issues (LazyInitializationException). Always map to DTOs.
4. ❌ **Missing `@Transactional`**: Performing multi-step database writes in a Service method without the `@Transactional` annotation. If step 2 fails, step 1 is not rolled back.
5. ❌ **Fat Controllers**: Writing 200 lines of business validation and mapping logic inside the controller method. The controller should be 3 lines: Receive, Call Service, Return.
6. ❌ **Synchronous External Calls**: Blocking the Tomcat thread pool while waiting for a slow 3rd-party API to respond. Use `WebClient` or `RestTemplate` with timeouts, and consider circuit breakers (Resilience4j).
7. ❌ **N+1 Queries in JPA**: Fetching a list of entities that have a `@OneToMany` lazy relationship, and then calling `.getChildren()` on each one in a loop. Use `@EntityGraph` or `JOIN FETCH`.
8. ❌ **Component Scan Pollution**: Putting non-Spring classes (like domain entities or generic utilities) into packages that are component-scanned, causing Spring to waste startup time trying to proxy them.
