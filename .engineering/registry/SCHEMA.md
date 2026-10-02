# Technology Pack Schema (SCHEMA)

## Overview
A "Tech Pack" is a self-contained module inside `.engineering/registry/packs/` that encapsulates all the knowledge, standards, and rules for a specific technology.

## Directory Structure
A complete Tech Pack should look like this:
```
[tech-name]/
├── manifest.yaml          # REQUIRED: Identity and dependencies
├── standards.md           # Coding standards and formatting rules
├── best-practices.md      # Recommended patterns
├── pitfalls.md            # Common mistakes and anti-patterns
├── workflows/             # Tech-specific SOPs (e.g., how to run migrations)
├── templates/             # Boilerplate code and file structures
└── checklists/            # Code review and security verification checklists
```

## Manifest Specification (`manifest.yaml`)
The manifest is the core of the Tech Pack. It must adhere to the following schema:
```yaml
api_version: v1
kind: TechPack
metadata:
  name: "spring-boot"
  version: "3.x"
  category: "framework" # Options: language, framework, database, tool
  
spec:
  dependencies:
    # Resolves recursively to other Tech Packs
    - "java"
    - "maven"
    
  compatibility:
    requires:
      - tech: "java"
        version: ">= 17"
    conflicts:
      - tech: "jakarta-ee"
        
  assets:
    # Explicit pointers to the context files. If omitted, they are ignored.
    standards: "standards.md"
    best_practices: "best-practices.md"
    pitfalls: "pitfalls.md"
```
