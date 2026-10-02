# Workflow: Incident Response

## 1. Required Inputs
- Active alert, pager notification, or critical bug report.
- Impact radius (e.g., Data loss, API downtime).

## 2. Required Context
- Runbooks and observability dashboards (Datadog, Grafana, etc.).
- Infrastructure state.

## 3. Required Reviews
- Peer Review (Pair programming is mandatory during P0 incidents).

## 4. Expected Outputs
- Immediate mitigation (e.g., rollback, hotfix, scale up).
- Communication log to stakeholders.

## 5. Exit Criteria
- System metrics return to healthy baselines.
- An incident post-mortem document is drafted.

## 6. Execution Steps
1. **Triage & Acknowledge**: Immediately halt feature work. Assess the severity (P0/P1) and assign an Incident Commander.
2. **Mitigation First**: Do not try to fix the root cause immediately if a faster mitigation exists. Favor reverting the last deployment or scaling up infrastructure.
3. **Hotfix Implementation**: If a rollback is impossible, pair-program the hotfix. Bypass standard staging pipelines using the emergency deploy route.
4. **Validation**: Monitor the telemetry (logs, CPU, error rates) for 15 minutes after mitigation to ensure stability.
5. **Communication**: Update internal status pages or stakeholder Slack channels with the resolution status.
6. **Post-Mortem**: Within 48 hours, schedule a blameless RCA (Root Cause Analysis). Create a follow-up ticket in `memory/tech-debt/registry.yaml` to address the underlying systemic failure.
