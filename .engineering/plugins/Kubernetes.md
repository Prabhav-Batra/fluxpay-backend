# Kubernetes Plugin

## Registration
- ID: `projectos.plugins.kubernetes`
- Version: `1.0.0`
- Compatibility: `Kubernetes 1.25+`

## Capabilities
- **Standards**: Injects Kubernetes specific rules for manifests, deployment strategies, and resource management.
- **Workflows**: Hooks into `infrastructure-change` and CI/CD deployment pipelines.

## Core Rules
1. **Resource Limits**: Every Pod must specify both `requests` and `limits` for CPU and Memory. A pod without limits can bring down an entire node.
2. **Probes**: Always define `livenessProbe` (should the pod be restarted?) and `readinessProbe` (should the pod receive traffic?) for deployments.
3. **Immutability**: Never use the `:latest` tag for container images. Always pin to a specific, immutable SHA or semantic version tag.
4. **Least Privilege**: Pods should run with the minimum necessary ServiceAccount permissions. Use Role-Based Access Control (RBAC).
5. **ConfigMaps & Secrets**: Externalize configuration into ConfigMaps and sensitive data into Secrets. Never hardcode them in the Deployment YAML.

## Common Anti-Patterns
- ❌ Running pods as root. Enforce `securityContext: runAsNonRoot: true`.
- ❌ Naked Pods: Never deploy a `Pod` directly. Always use a controller like `Deployment`, `DaemonSet`, or `StatefulSet` to ensure the pod is recreated if it fails.
- ❌ Leaving PodDisruptionBudgets (PDBs) unconfigured for highly available services, allowing node drains to take the service offline.

## Dependencies
- Requires: `kubectl` CLI.
