# ProjectOS Configuration Layer

## Overview
The Configuration Layer resides in `.engineering/config/`. It acts as the central control panel for ProjectOS. By modifying these YAML files, engineering teams can shape exactly how the AI Assistant behaves without changing any underlying code. 

The configuration system is strictly modular, versioned (`api_version: v1`), and highly backward-compatible.

## Schema Specifications

1. **[project.yaml](./project.yaml)**: Governs project identity. It dictates the AI's communication tone based on engineering profile and its risk tolerance based on maturity level.
2. **[stack.yaml](./stack.yaml)**: The whitelist of allowed technologies. The AI uses this to strictly scope code generation. Supports multi-language and multi-framework setups.
3. **[architecture.yaml](./architecture.yaml)**: Defines system design patterns and infrastructure targets. Guides the AI on where to place logic and what Cloud SDKs to use.
4. **[preferences.yaml](./preferences.yaml)**: Controls cosmetic and workflow rules, directly affecting how the AI formats code and names branches/commits.
5. **[constraints.yaml](./constraints.yaml)**: The cognitive firewall. Explicitly defines security constraints, anti-patterns, and disallowed dependencies the AI must never use.
6. **[environments.yaml](./environments.yaml)**: Provides context on deployment stages, ensuring the AI never applies development mocks to production configurations.
