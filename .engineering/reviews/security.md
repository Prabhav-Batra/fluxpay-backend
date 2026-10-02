# Review Checklist: Security

> **Reviewer Instructions**: Validate all code against OWASP Top 10 and `standards/security.md`.

## 🔴 Blockers (Must Fix)
- [ ] **Authentication**: Are endpoints properly authenticating the caller (JWT, OAuth)?
- [ ] **Authorization (BOLA/IDOR)**: Does the code verify that the authenticated user *owns* or has rights to the specific resource requested?
- [ ] **Injection (SQLi/XSS)**: Are all DB queries parameterized? Is all user input sanitized before rendering or querying?
- [ ] **Secrets Leakage**: Are any API keys, passwords, or tokens hardcoded or logged?
- [ ] **Dependency Vulnerabilities**: Did the CI/CD pipeline flag any High/Critical CVEs in new packages?
- [ ] **Cryptographic Standards**: Are industry-standard algorithms (AES-256, Argon2, SHA-256) used instead of custom or deprecated ones (MD5)?

## 🟡 Warnings (Should Fix)
- [ ] **Least Privilege**: Does the service account or IAM role have more permissions than it strictly needs?
- [ ] **Rate Limiting / DoS**: Is this endpoint susceptible to brute force or DoS attacks?
- [ ] **Security Headers**: Are CSP, HSTS, and X-Frame-Options headers configured on web responses?
- [ ] **Verbose Errors**: Does the API leak stack traces or internal DB schema details in error responses?

## 🟢 Advisories (Nice to Have)
- [ ] **Audit Logging**: Are sensitive actions (e.g., password change, permission grant) writing to an immutable audit log?
- [ ] **Session Invalidation**: Does changing a password correctly invalidate all existing active sessions?
