# Workflow: Bug Fix

## 1. Required Inputs
- Bug Report (Steps to reproduce, expected vs actual behavior).
- Error logs or stack trace.

## 2. Required Context
- Source files implicated in the stack trace.
- Recent commits touching those files.

## 3. Required Reviews
- Code Review.

## 4. Expected Outputs
- Code changes resolving the bug.
- A regression test (unit or integration) that fails *without* the fix and passes *with* it.

## 5. Exit Criteria
- The bug is no longer reproducible.
- Regression test passes.

## 6. Execution Steps
1. **Reproduction**: If possible, write a failing unit/integration test that reproduces the bug *before* changing any source code.
2. **Root Cause Analysis**: Analyze the stack trace and code to identify the logical flaw.
3. **Fix Implementation**: Modify the source code to resolve the flaw.
4. **Verification**: Run the test from Step 1. It must now pass.
5. **Blast Radius Check**: Run the full test suite to ensure the fix didn't break unrelated features.
6. **Commit**: Use the `fix()` conventional commit prefix and reference the bug ID.
