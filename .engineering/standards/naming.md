# Global Standard: Naming & Conventions

## General Rules
1. **Clarity over brevity**: Names must reveal intent. `calculateMonthlyInterest()` is better than `calcInt()`. Avoid abbreviations unless they are universally understood in the domain (e.g., `id`, `url`).
2. **Pronounceable names**: Use pronounceable names to facilitate technical discussions. `generationTimestamp` instead of `genTmStmp`.
3. **Searchable names**: Avoid single-letter names or generic names (`data`, `info`, `manager`, `processor`) unless the context is extremely narrow (e.g., `i` in a 3-line loop).
4. **Ubiquitous language**: Use vocabulary from the business domain. If the business calls it a "Guest", do not call it a "User" in the code.

## Casing
5. **camelCase**: Use for local variables, function names, and instance methods.
6. **PascalCase (UpperCamelCase)**: Use for class names, interfaces, types, enums, and React components.
7. **SCREAMING_SNAKE_CASE**: Use for global constants and environment variables.
8. **snake_case**: Use for database tables, columns, and often file names (depending on the language ecosystem like Python or Rust).
9. **kebab-case**: Use for URLs, CSS classes, and HTML IDs.

## Functions & Methods
10. **Verb phrases**: Function names should be verbs or verb phrases that describe the action being performed (e.g., `fetchUserData()`, `isEmailValid()`).
11. **Boolean variables**: Prefix boolean variables with `is`, `has`, `can`, or `should` (e.g., `isActive`, `hasPermission`).
12. **Symmetry**: Use symmetrical antonyms (e.g., `start`/`stop`, `open`/`close`, `add`/`remove`, `get`/`set`).

## Classes & Interfaces
13. **Noun phrases**: Class names should be nouns or noun phrases representing an entity or concept (e.g., `UserRepository`, `PaymentGateway`).
14. **Interfaces**: In languages like TypeScript, prefer simply naming the interface as the concept (`User`) rather than prefixing with 'I' (`IUser`), unless it's a specific convention in the language ecosystem (like C#).

## Files & Directories
15. **Match the primary export**: The file name should match the primary class or component it exports. If exporting a class `UserProfile`, the file should be `UserProfile.ts` or `user_profile.py` depending on language conventions.
16. **Feature-based grouping**: Name directories by feature or domain concept (`/auth`, `/billing`), not by technical role (`/controllers`, `/models`), unless following a strict MVC framework convention.
