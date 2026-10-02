# RFC-001: Plugin Marketplace

## Status: Proposed
**Author**: Chief Architect
**Date**: 2026-06-30

## Overview
As ProjectOS scales, teams will write redundant plugins for common technologies (e.g., React, Django). We propose a centralized Plugin Marketplace where teams can publish and discover curated Plugin SDK manifests.

## Motivation
To prevent fragmented knowledge bases and standardize how AI assistants write code across the industry.

## Proposed Architecture
- Introduce a new command to the future `projectos` CLI: `projectos plugin install <plugin-name>`.
- The CLI will pull the `manifest.yaml` and standard `.md` files from a central Git repository.

## Drawbacks
- Increases maintenance burden on the core team to curate and verify third-party plugins.
