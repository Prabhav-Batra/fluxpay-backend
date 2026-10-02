# Workflow: Refactor

## 1. Required Inputs
- The specific code block or module to be refactored.
- The primary goal (e.g., performance, readability, decoupling).

## 2. Required Context
- Existing test suite for the target module.
- `standards/backend.md` or `standards/frontend.md` for target patterns.

## 3. Required Reviews
- Code Review (`reviews/code.md`)
- Performance Review (if performance was the goal) (`reviews/performance.md`)

## 4. Expected Outputs
- Cleaner, more maintainable code structure.
- No changes to external API contracts or public interfaces.

## 5. Exit Criteria
- 100% of the existing test suite passes without modification to the tests (proving behavior was preserved).

## 6. Execution Steps
1. **Test Coverage Verification**: Ensure the target code has near 100% test coverage before touching a single line. If it doesn't, *write tests first* (Characterization Tests).
2. **Snapshot Baseline**: Record current performance metrics (latency, memory usage) if the goal is optimization.
3. **Isolate**: Create a new branch. Perform the refactoring in small, atomic commits rather than one massive rewrite.
4. **Verify Equivalence**: Run the test suite. If a test fails, either the refactor broke the code, or the test was tightly coupled to the implementation (fragile test). Fix accordingly.
5. **Performance Diff**: If optimizing, run benchmarks to prove the new code is demonstrably better than the baseline.
6. **Integration Verification**: Check downstream consumers (via `Impact_Analyzer`) to ensure no implicit contracts (like reliance on a specific ordering) were broken.
