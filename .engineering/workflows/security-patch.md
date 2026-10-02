# Workflow: Security Patch

## 1. Required Inputs
- CVE report, vulnerability scan result, or penetration test finding.
- CVSS severity score.

## 2. Required Context
- `standards/security.md`
- Application secrets management strategy.

## 3. Required Reviews
- Security Review (`reviews/security.md`)
- Code Review (`reviews/code.md`)

## 4. Expected Outputs
- Upgraded dependency OR patched application logic.
- Post-mortem documentation (if the vulnerability was exploited).

## 5. Exit Criteria
- The vulnerability scanner passes locally and in CI.
- The patch introduces zero regressions to core functionality.

## 6. Execution Steps
1. **Validation & Reproduction**: Reproduce the vulnerability in a safe, isolated environment. Do not test destructive exploits on staging or production.
2. **Dependency Upgrade**: If the CVE is in a third-party package, bump the version in `package.json`/`pom.xml`. Run `npm audit fix` or equivalent.
3. **Code Patch**: If the vulnerability is in first-party code (e.g., an IDOR bug), implement the fix and add a specific regression test that attempts to exploit it (which should now fail securely).
4. **Secrets Rotation**: If any secrets, keys, or certificates were potentially exposed, immediately rotate them across all environments.
5. **Fast-Track Deployment**: Bypass standard feature-freeze windows. Deploy the patch directly to production via the hotfix pipeline.
6. **Audit & Disclosure**: Audit logs to determine if the vulnerability was actively exploited. Inform the security team and follow Responsible Disclosure protocols if user data was affected.
