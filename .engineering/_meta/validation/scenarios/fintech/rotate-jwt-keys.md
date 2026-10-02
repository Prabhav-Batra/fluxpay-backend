# Scenario: Rotate JWT Signing Keys

**Domain**: FinTech
**Difficulty**: High Risk (Security)

## Description
The primary JWT signing key used by the `auth-service` has reached its 90-day rotation period. We need to introduce a new signing key, support validating tokens signed by the old key during the transition period, and update the API Gateway to utilize the new JWKS endpoint.

## Expected Workflow
- `workflows/security-patch.md` or `workflows/refactor.md`
- Needs `reviews/security.md` and `reviews/api.md`

## Exit Criteria
- Zero downtime for active users.
- Old tokens remain valid until expiration.
- New tokens are signed with the new key.
