# Docker Best Practices

1. **Multi-Stage Builds**: Always use multi-stage builds to compile code in a heavy image and run the artifact in a lightweight image (like `alpine` or `distroless`).
2. **Least Privilege**: Never run the application as `root`. Create a specific user (`adduser -D appuser`) and switch to it using the `USER` directive.
3. **Layer Caching**: Order commands from least-frequently changed to most-frequently changed. Copy dependency files (`package.json`, `pom.xml`) and install them *before* copying the source code.
4. **Specific Tags**: Never use `latest`. Pin base images to specific versions and SHA digests (e.g., `node:18.16.0-alpine@sha256:abc123...`).
5. **Immutability**: Containers must be completely ephemeral and immutable. Do not write persistent data to the container filesystem; use external volumes or databases.
6. **Healthchecks**: Define a `HEALTHCHECK` directive in the Dockerfile so orchestrators know if the application is actually ready to receive traffic, not just if the process is running.
7. **Signal Handling**: Ensure the application properly handles `SIGTERM` and `SIGINT` for graceful shutdowns. Do not wrap the entrypoint in an interactive shell script (`sh -c`) that swallows signals.
8. **Minimal Base Images**: Use Alpine, Distroless, or Scratch images to minimize the attack surface area and image size.
9. **.dockerignore**: Always include a `.dockerignore` file to prevent copying `.git/`, local `node_modules/`, and sensitive environment files into the build context.
10. **Read-Only Filesystem**: Run containers with a read-only root filesystem (`--read-only` in Docker run) to prevent attackers from dropping malware.
