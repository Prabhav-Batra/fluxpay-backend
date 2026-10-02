# Workflow: API Change

## 1. Required Inputs
- The proposed change to the Request/Response payload.
- Versioning strategy (Is this a breaking change?).

## 2. Required Context
- Existing OpenAPI/Swagger definitions.
- Downstream consumer requirements.
- `standards/api.md`

## 3. Required Reviews
- API Review (`reviews/api.md`)
- Code Review (`reviews/code.md`)

## 4. Expected Outputs
- Updated endpoint logic.
- Updated API documentation / schema files.

## 5. Exit Criteria
- API backwards compatibility is maintained OR a new API version is properly provisioned.
- Contract tests pass.

## 6. Execution Steps
1. **Impact Analysis**: Query the `Impact_Analyzer` to identify downstream clients that will be affected by this change.
2. **Schema Definition**: Update the OpenAPI/Swagger YAML or Protobuf definition *before* touching the implementation code.
3. **Controller Implementation**: Implement the new route or modify the existing one. Validate all new incoming payloads.
4. **Mock/Test Generation**: Generate mock responses and update contract tests (e.g., Pact) to verify the new payload.
5. **Review Gate**: Submit for API Review to ensure the error envelopes and status codes conform to `standards/api.md`.
6. **Consumer Notification**: If the change is deprecating a field, trigger the consumer notification protocol (e.g., Slack alert or deprecation header).
