# Review Checklist: API

> **Reviewer Instructions**: Validate all API changes against `standards/api.md`. Blockers (🔴) must be resolved before merge. Warnings (🟡) should be resolved but can be deferred with a tech-debt ticket. Advisories (🟢) are optional improvements.

## 🔴 Blockers (Must Fix)
- [ ] **Breaking Changes**: Does this change break backwards compatibility without a major version bump? (e.g., removing a field, changing a type).
- [ ] **Authentication**: Are all new endpoints properly secured behind the standard authentication middleware?
- [ ] **REST Semantics**: Are HTTP verbs used correctly? (No GET for mutations, no POST for simple retrieval).
- [ ] **Status Codes**: Are error conditions returning proper 4xx/5xx codes instead of 200 OK?
- [ ] **Error Envelope**: Does the error response match the standard JSON error envelope (`code`, `message`, `traceId`)?
- [ ] **Idempotency**: Are all new PUT and DELETE operations strictly idempotent?

## 🟡 Warnings (Should Fix)
- [ ] **Pagination**: Do endpoints returning collections support cursor-based pagination?
- [ ] **N+1 Risk**: Does this endpoint trigger N+1 queries when resolving nested resources?
- [ ] **Documentation**: Is the OpenAPI/Swagger specification fully updated with request/response examples?
- [ ] **Validation**: Are request payloads validated at the controller boundary before hitting business logic?

## 🟢 Advisories (Nice to Have)
- [ ] **Partial Responses**: Could this endpoint benefit from sparse fieldsets (`?fields=id,name`)?
- [ ] **Caching**: Should this GET request include `Cache-Control` headers or an ETag?
