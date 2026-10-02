# Workflow: New Service

## 1. Required Inputs
- Architecture Decision Record (ADR) justifying the new service.
- API Contract / OpenAPI Specification.
- Service level objectives (SLOs).

## 2. Required Context
- System topology (`architecture.yaml`).
- DevOps standards (`standards/devops.md`).
- Security constraints (`config/constraints.yaml`).

## 3. Required Reviews
- Architecture Review (`reviews/architecture.md`)
- Security Review (`reviews/security.md`)
- Production Readiness Review (`reviews/production-readiness.md`)

## 4. Expected Outputs
- New repository or monorepo package initialized.
- CI/CD pipeline configured.
- Boilerplate API and Healthcheck endpoints.

## 5. Exit Criteria
- Service deploys successfully to a staging environment.
- Service successfully registers with service discovery / API gateway.

## 6. Execution Steps
1. **ADR Validation**: Verify the ADR is approved in `memory/adr/`.
2. **Directory Scaffolding**: Create the root directory and copy the `microservice-scaffold` template.
3. **Configuration Injection**: Setup environment variables, `constraints.yaml`, and `stack.yaml` for the new service.
4. **Boilerplate Generation**: Implement the `cmd/server/main` entry point.
5. **Health Endpoints**: Implement `/health/live` and `/health/ready`.
6. **CI/CD Setup**: Generate the GitHub Actions (or equivalent) pipeline for building and testing.
7. **Dockerization**: Create the multi-stage `Dockerfile`.
8. **Verification**: Build the docker image locally and run tests.
9. **Commit**: Push initial commit to the main branch of the new service.
