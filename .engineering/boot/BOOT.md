# ProjectOS Boot Layer (BIOS)

## Overview
The Boot Layer functions as the Basic Input/Output System (BIOS) for ProjectOS. It is the fundamental initialization sequence that every AI Assistant must execute before performing any engineering task, analysis, or code generation within a host repository.

## Philosophy
Just as a physical computer requires a BIOS to initialize hardware before loading the operating system, an AI Assistant requires a cognitive boot sequence to load the necessary constraints, context, and capabilities before engaging with a codebase. 

The Boot Layer guarantees a deterministic starting state. It ensures the Assistant operates strictly within the boundaries of the host project's architecture, standards, and domain logic, completely overriding any default or assumed behaviors.

## Core Directives
1. **Zero Assumption Principle**: The Assistant must assume nothing about the project stack, architecture, or domain until the boot sequence is complete.
2. **Strict Determinism**: The boot process must be executed identically on every new session or context window reset.
3. **Immutability of Rules**: Standards and architectures loaded during boot are read-only directives that cannot be violated during execution.

## Boot Components
- **[STARTUP.md](./STARTUP.md)**: The sequential initialization routine.
- **[LOAD_ORDER.md](./LOAD_ORDER.md)**: The strict dependency graph for context ingestion.
- **[THINKING_MODEL.md](./THINKING_MODEL.md)**: The cognitive framework and execution constraints.
- **[AI_STARTUP.md](./AI_STARTUP.md)**: The primary injection prompt that triggers the boot sequence.
