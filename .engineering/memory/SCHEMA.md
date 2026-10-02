# Project Memory Schema

## Purpose
The AI Assistant has no long-term memory between chat sessions. ProjectOS solves this by persisting critical knowledge directly to disk in structured formats. This directory is the "brain" of the specific project.

## Directory Structure

### 1. `adr/` (Architecture Decision Records)
Stores immutable records of why technical decisions were made.
- **Format**: Markdown, based on the Nygard template.
- **Naming**: `ADR-001-use-postgres-for-events.md`.
- **When to update**: Anytime a decision is made that impacts the architecture, database, or tech stack.

### 2. `tech-debt/` (Technical Debt Registry)
Tracks shortcuts taken, known bugs, and refactoring needs.
- **Format**: YAML list in `tech-debt/registry.yaml`.
- **Schema**:
  ```yaml
  - id: TD-001
    date: "YYYY-MM-DD"
    description: "Hardcoded the tax rate to 20% to hit deadline."
    impact: "High - will break if tax laws change."
    location: "src/billing/tax_calculator.ts"
    status: "open" # open, resolved
  ```
- **When to update**: Anytime the AI or human knowingly takes a shortcut to save time.

### 3. `domain/` (Ubiquitous Language)
Maintains the business glossary so the AI uses the correct terminology.
- **Format**: YAML dictionary in `domain/ubiquitous-language.yaml`.
- **Schema**:
  ```yaml
  terms:
    "Guest": "An unregistered user browsing the catalog."
    "Cart": "A temporary collection of items pending checkout."
  ```
- **When to update**: When a new business concept is introduced during a feature request.

### 4. `features.yaml` (Completed Features)
A high-level index of what the system actually does, preventing the AI from rebuilding existing functionality.
- **Schema**:
  ```yaml
  - name: "User Registration"
    description: "Allows Guests to become Users via email/password."
    modules: ["src/auth"]
  ```

## AI Access Rules
- The Output Engine **writes** to these files at the end of a successful execution.
- The Context Resolver **reads** from these files to inject historical context into new tasks.
