# Workflow: Infrastructure Change

## 1. Required Inputs
- Target topology change (e.g., Add Redis, upgrade Postgres instance).
- Cost estimation for the new resources.

## 2. Required Context
- Existing `infrastructure` segment of `config/architecture.yaml`.
- `standards/devops.md`.

## 3. Required Reviews
- Architecture Review (`reviews/architecture.md`)
- Production Readiness Review (`reviews/production-readiness.md`)

## 4. Expected Outputs
- Terraform, Pulumi, or Kubernetes manifest updates.
- Updated `architecture.yaml` and `repository-index.yaml` reflecting the new infrastructure.

## 5. Exit Criteria
- Infrastructure deploys successfully to the staging environment without manual intervention.

## 6. Execution Steps
1. **IaC Update**: Modify the Infrastructure-as-Code definitions (Terraform/Helm) to reflect the new state.
2. **Cost & Security Check**: Run a static analysis tool (e.g., `tfsec` or `checkov`) and a cost estimator (e.g., Infracost) on the proposed plan.
3. **Plan Generation**: Run `terraform plan` or equivalent to preview the exact creation/destruction sequence. Ensure no data-bearing resources are accidentally destroyed.
4. **Architecture Sync**: Update `.engineering/config/architecture.yaml` to officially register the new infrastructure component in ProjectOS.
5. **Staging Rollout**: Apply the changes to the staging environment. Run integration smoke tests.
6. **Production Rollout**: Merge to main, triggering the automated production apply via CD.
