# Review Checklist: Production Readiness

> **Reviewer Instructions**: Validate that the service is ready for the harsh realities of a production environment.

## 🔴 Blockers (Must Fix)
- [ ] **Observability**: Are critical business and technical metrics emitted? Are logs structured and free of PII?
- [ ] **Health Checks**: Does the service implement `/health/live` and `/health/ready` endpoints accurately reflecting dependency status?
- [ ] **Resource Limits**: Are Kubernetes/Docker CPU and Memory limits explicitly configured?
- [ ] **Secrets Management**: Are absolutely no secrets hardcoded? Are they pulled securely at runtime?
- [ ] **Rollback Plan**: If this deployment fails, can we instantly rollback? (e.g., Are database migrations backward compatible?)

## 🟡 Warnings (Should Fix)
- [ ] **Timeouts**: Do all outbound HTTP/gRPC calls have explicit, reasonable timeouts?
- [ ] **Circuit Breakers**: Are downstream dependencies wrapped in circuit breakers to prevent cascading failures?
- [ ] **Rate Limiting**: Are public-facing endpoints protected by rate limiters?
- [ ] **Alerting**: Are alerts configured for increased error rates or latency spikes?

## 🟢 Advisories (Nice to Have)
- [ ] **Load Testing**: Has this specific path been load tested to its expected peak volume?
- [ ] **Chaos Engineering**: Has the service been tested against dependency failures (e.g., Redis goes down)?
