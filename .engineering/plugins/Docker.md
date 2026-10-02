# Docker Plugin

## Registration
- ID: `projectos.plugins.docker`
- Version: `1.0.0`
- Compatibility: `Docker Engine 20+`

## Capabilities
- **Standards**: Injects containerization rules for security, size, and build caching.
- **Workflows**: Hooks into `docker build` and CI/CD pipelines.
- **Knowledge Packs**: Injects the `docker` tech pack from the registry.

## Core Rules
1. **Multi-Stage Builds**: Always use multi-stage builds to separate the build environment from the runtime environment. The final image must contain only the compiled binary and essential runtime dependencies.
2. **Minimal Base Images**: Use `alpine`, `distroless`, or `scratch` for the final stage to reduce attack surface and download size.
3. **Non-Root User**: Always specify a `USER` directive in the Dockerfile with a non-root UID (e.g., `USER 1000:1000`). Never run the container process as `root`.
4. **Layer Caching**: Order commands in the Dockerfile from least frequently changed (e.g., package manager setup, dependency installation) to most frequently changed (e.g., source code copy) to maximize Docker layer caching.

## Common Anti-Patterns
- ❌ Hardcoding secrets or credentials in the Dockerfile using `ENV`. Use `ARG` (if only needed at build time) or mount secrets at runtime.
- ❌ Running multiple processes (e.g., a web server and a database) in a single container. Use `docker-compose` or Kubernetes to orchestrate multiple containers instead.
- ❌ Using the `latest` tag for base images (e.g., `FROM node:latest`). Always pin to a specific version (e.g., `FROM node:18.16.0-alpine`).

## Dependencies
- Requires: Docker CLI.
- Links to Pack: `registry/packs/docker/manifest.yaml`
