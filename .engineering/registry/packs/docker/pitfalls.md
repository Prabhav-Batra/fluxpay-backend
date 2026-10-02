# Docker Pitfalls

1. ❌ **Running as Root**: Leaving the default `root` user in the Dockerfile. If the container is compromised, the attacker has root access in the container namespaces.
2. ❌ **Bloated Images**: Copying the entire repository (`COPY . .`) without a `.dockerignore` file, accidentally including gigabytes of test data or local binaries.
3. ❌ **Secrets in Images**: Passing API keys or passwords via `ENV` or `ARG` during the build process. These are permanently visible in `docker history`. Use build secrets or mount them at runtime.
4. ❌ **Zombie Processes**: Running complex applications (like Java or Node) as PID 1 without an init system (like `tini`). They will fail to reap zombie processes, eventually exhausting system resources.
5. ❌ **Data Loss**: Storing database files or uploaded media directly in the container filesystem instead of a mounted volume. The data will be deleted when the container is replaced.
6. ❌ **Apt-Get Anti-Patterns**: Running `apt-get update` and `apt-get install` on separate `RUN` lines. Docker will cache the update layer, leading to outdated package installations later. Combine them: `RUN apt-get update && apt-get install -y`.
7. ❌ **Failing to Clean Up**: Leaving apt caches or downloaded tarballs in the image. Always run `rm -rf /var/lib/apt/lists/*` at the end of an install layer.
8. ❌ **IP Binding**: Binding the application server to `localhost` (127.0.0.1) inside the container. It will be unreachable from the outside. Always bind to `0.0.0.0`.
