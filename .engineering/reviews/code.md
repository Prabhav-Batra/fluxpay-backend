# Review Checklist: Code Quality

> **Reviewer Instructions**: Validate general code quality, readability, and maintainability.

## 🔴 Blockers (Must Fix)
- [ ] **Test Coverage**: Are there unit tests for the new "happy path" and primary edge cases?
- [ ] **Swallowed Errors**: Are errors caught and silently ignored without logging or re-throwing?
- [ ] **Magic Strings/Numbers**: Are there hardcoded values that should be extracted to constants, enums, or configuration?
- [ ] **Resource Leaks**: Are files, streams, or database connections left unclosed/unmanaged?
- [ ] **Race Conditions**: Is shared mutable state accessed concurrently without proper synchronization?

## 🟡 Warnings (Should Fix)
- [ ] **Naming**: Are variable and function names descriptive and compliant with `standards/naming.md`? (No abbreviations, clear intent).
- [ ] **Complexity**: Is the cyclomatic complexity of any new function too high? (e.g., deeply nested `if/else` or loops).
- [ ] **Duplication (DRY)**: Is there significant copy-pasted logic that should be refactored into a shared utility?
- [ ] **Function Length**: Are any functions excessively long (>50 lines of active logic)?
- [ ] **Comment Accuracy**: Do the comments explain *why* instead of *what*? Are any comments rendered obsolete by this change?

## 🟢 Advisories (Nice to Have)
- [ ] **Functional Paradigms**: Could imperative loops be replaced with more readable functional methods (map/filter/reduce)?
- [ ] **Early Returns**: Can nested `if` statements be refactored using guard clauses (early returns)?
