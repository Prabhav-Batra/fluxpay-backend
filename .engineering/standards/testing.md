# Global Standard: Testing

## Philosophy
1. **The Test Pyramid**: The test suite must be heavily weighted towards fast, isolated unit tests, followed by integration tests, and very few end-to-end (E2E) tests.
2. **Deterministic tests**: Tests must never rely on external network calls, shared mutable state, or wall-clock time. Use mocks, stubs, and fixed clocks.
3. **Coverage as a signal**: Use code coverage to find untested paths, not as a vanity metric. 80%+ line coverage is a baseline, but 100% coverage with weak assertions is worse than 60% with strong assertions.

## Unit Tests
4. **Arrange-Act-Assert**: Every unit test must follow AAA structure. One action per test. One logical assertion per test.
5. **Test behavior, not implementation**: Test what a function does, not how it does it. Tests should survive refactors.
6. **Naming convention**: `should_[expectedBehavior]_when_[condition]`. Example: `should_throw_InsufficientFunds_when_balance_is_zero`.
7. **No infrastructure in unit tests**: Unit tests must never touch databases, file systems, HTTP clients, or message brokers. Mock all external dependencies.
8. **Edge cases are mandatory**: Every unit test suite must cover: null/empty inputs, boundary values, error paths, and concurrent access (where applicable).

## Integration Tests
9. **Real dependencies**: Integration tests use real databases (via testcontainers or in-memory), real caches, and real message brokers. Never mock the database in an integration test.
10. **Transaction isolation**: Each integration test must run in its own transaction or use database cleanup to prevent test pollution.
11. **API contract verification**: Integration tests must verify HTTP status codes, response schemas, and header contracts — not just response bodies.

## End-to-End Tests
12. **Minimal and critical-path only**: E2E tests should cover only the most critical user flows (e.g., signup → login → purchase → logout). Limit to < 20 E2E tests per service.
13. **Stable selectors**: UI tests must use `data-testid` attributes, never CSS classes or XPath. Tests must survive UI redesigns.

## Test Data
14. **Factories over fixtures**: Use factory functions (Builder pattern) to generate test data, not static JSON fixtures. Factories make intent explicit and reduce coupling.
15. **No production data**: Never copy production data into test environments. Generate synthetic data that covers the same edge cases.

## CI Integration
16. **Tests must pass to merge**: No PR may be merged with failing tests. Flaky tests must be quarantined and fixed within 48 hours.
17. **Performance budget**: The full unit test suite must complete in under 60 seconds. Integration tests under 5 minutes.
