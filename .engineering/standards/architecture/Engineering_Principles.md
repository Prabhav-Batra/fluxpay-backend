# Engineering Principles

## Purpose
The foundational philosophy driving all technical decisions in ProjectOS. When specific rules do not cover a scenario, fall back to these principles.

## Core Principles

1. **No God Files**
   - *Rationale*: Massive files become merge-conflict magnets, are impossible to unit test, and destroy cognitive load. 
   - *Action*: Aggressively split files into focused modules.

2. **Optimize for Reading, Not Writing**
   - *Rationale*: Code is read 10x more than it is written.
   - *Action*: Favor verbose, descriptive variable names over terse abbreviations. Avoid overly clever one-liners if a 3-line approach is more obvious.

3. **Explicit over Implicit**
   - *Rationale*: "Magic" frameworks that hide behavior save 5 minutes of setup but cost 5 days of debugging.
   - *Action*: Prefer explicit dependency injection, clear return types, and visible configuration over implicit state and reflection-heavy magic.

4. **The Principle of Least Astonishment**
   - *Rationale*: A component should behave exactly as its name implies.
   - *Action*: A function named `getUser()` should not mutate the database.

5. **Design for Disposability**
   - *Rationale*: Software rots. You will eventually need to rewrite components.
   - *Action*: Isolate features behind interfaces so they can be ripped out and replaced without touching the rest of the system.

6. **Automate the Grunt Work**
   - *Rationale*: Human willpower is finite. Relying on humans to remember formatting rules or deployment steps is a guaranteed failure.
   - *Action*: If a process takes more than 5 minutes and is repeated, automate it via CI/CD, linters, or ProjectOS workflows.

7. **Security by Default**
   - *Rationale*: Security patched on at the end always fails.
   - *Action*: Assume all input is malicious. Default all APIs to deny-all. Never store secrets in code.
