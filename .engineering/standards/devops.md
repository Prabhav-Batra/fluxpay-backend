# Global Standard: DevOps & CI/CD

## CI/CD Pipelines
1. **Infrastructure as Code (IaC)**: All infrastructure (servers, databases, networks, IAM roles) must be defined in code (Terraform, CloudFormation, Pulumi). Manual changes via cloud consoles are strictly prohibited.
2. **Immutable Infrastructure**: Servers and containers are disposable. Never SSH into a production server to apply updates. Deploy a new image instead.
3. **Continuous Integration (CI)**: Every push to a branch must trigger a CI pipeline that runs linting, unit tests, security scans, and build verification.
4. **Build once, deploy anywhere**: The CI pipeline must produce a single, immutable artifact (e.g., a Docker image) that is promoted through environments (dev -> staging -> prod) without modification. Environment-specific behavior must be injected via configuration.
5. **No human deployments**: All deployments to staging and production must be triggered automatically by the CI/CD system after a merge, or via an automated release process. Humans do not run deployment scripts locally.

## Environments
6. **Parity**: Development, staging, and production environments must be as identical as possible to minimize "works on my machine" issues.
7. **Ephemeral environments**: Branches should ideally spin up ephemeral preview environments for review, which are destroyed upon merge.

## Containers
8. **Minimal base images**: Use Alpine, Distroless, or minimal Scratch images to reduce attack surface and deployment time.
9. **Run as non-root**: Docker containers must specify a non-root `USER`. Never run application processes as root inside the container.
10. **Resource limits**: Every container deployment must specify both CPU and memory `requests` and `limits`.

## Secrets Management
11. **Secret injection**: CI/CD pipelines must inject secrets directly from a secure vault (AWS Secrets Manager, HashiCorp Vault, GitHub Secrets). Secrets must never appear in CI logs.
12. **Principle of Least Privilege for CI**: The CI/CD system's IAM role must only have permissions to deploy resources explicitly required by the project.

## Resilience & Rollbacks
13. **Zero-downtime deployments**: Deployments must use rolling updates, blue/green, or canary strategies. The application must never drop traffic during a deployment.
14. **Automated rollbacks**: If health checks or key metrics fail during or immediately after a deployment, the pipeline must automatically rollback to the previous stable version.
15. **Database migrations**: Migrations run before the new code is deployed. They must be backward-compatible so that the old version of the app continues to function during the rollout.

## Observability
16. **Centralized logging**: All containers must output logs to `stdout`/`stderr`. The infrastructure must forward these to a centralized logging system.
17. **Monitoring coverage**: Every deployment must automatically provision basic dashboards (CPU, Memory, Request Rate, Error Rate, Latency).
