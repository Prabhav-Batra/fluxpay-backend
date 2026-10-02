# Global Standard: Security

## Authentication
1. **JWT by default**: Use short-lived access tokens (15 min) with long-lived refresh tokens (7 days). Never store JWTs in localStorage; use httpOnly secure cookies.
2. **OAuth2 flows**: Use Authorization Code + PKCE for SPAs and mobile apps. Never use Implicit Grant.
3. **Session management**: Invalidate server-side sessions on password change, logout, and suspicious activity.
4. **Multi-factor authentication**: Require MFA for admin endpoints, financial transactions, and account recovery.

## Authorization
5. **RBAC minimum**: Every endpoint must declare its required role. Default to deny-all.
6. **Row-level security**: Users must only access their own data. Enforce at the query layer, not just the controller.
7. **Principle of Least Privilege**: Services and users must only have the minimum permissions necessary. IAM roles should be scoped per-service, not shared.

## Input Handling
8. **Sanitization**: All user inputs must be sanitized to prevent XSS. All database queries must use parameterized statements to prevent SQL Injection.
9. **Validation at the boundary**: Validate type, length, format, and range at the API entry point. Reject early with descriptive errors.
10. **File uploads**: Validate MIME type server-side (not just extension). Enforce size limits. Store in isolated buckets, never alongside application code.
11. **Rate limiting**: Apply rate limits on all public endpoints. Use sliding window counters. Return `429 Too Many Requests` with `Retry-After` header.

## Secrets
12. **Never commit secrets**: Secrets must be injected at runtime via environment variables, vault, or secret manager. No exceptions.
13. **Rotation policy**: All signing keys, API keys, and database credentials must be rotatable without downtime. Document the rotation procedure per-secret.
14. **Encryption at rest**: Sensitive data (PII, financial data) must be encrypted at rest using AES-256 or equivalent.
15. **Encryption in transit**: All service-to-service communication must use TLS 1.2+. Internal gRPC must use mTLS.

## Logging & Audit
16. **Never log PII**: Mask or redact email addresses, phone numbers, passwords, tokens, and financial data in all log outputs.
17. **Audit trail**: All state-mutating operations on sensitive resources must produce an immutable audit log entry with actor, action, resource, timestamp, and IP.
18. **Correlation IDs**: Every request must carry a correlation ID propagated across all downstream services for forensic tracing.

## Headers & Transport
19. **Security headers**: Set `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`.
20. **CORS**: Whitelist specific origins. Never use `Access-Control-Allow-Origin: *` in production.

## Dependency Security
21. **Vulnerability scanning**: Run automated dependency vulnerability scans (Snyk, Trivy, or equivalent) on every CI pipeline.
22. **License compliance**: Only use dependencies with licenses listed in `constraints.yaml.technology.allowed_licenses`.
