# Workflow Plugin Template

*Copy this template to `.engineering/workflows/custom/[workflow-name].md` to define a new SOP.*

---

# Workflow: [Workflow Name]

## 1. Required Inputs
- [List the exact data, files, or parameters the AI/Human must provide to start this workflow].
- e.g., Figma URL, Target Architecture.

## 2. Required Context
- [List the specific standards, tech packs, or config files the Context Loader must inject].
- e.g., `standards/security.md`.

## 3. Required Reviews
- [List which checklists from `reviews/` must pass before completion].
- e.g., Code Review (`reviews/code.md`).

## 4. Expected Outputs
- [List the tangible deliverables this workflow produces].
- e.g., Updated Terraform state, new API endpoint.

## 5. Exit Criteria
- [List the binary pass/fail conditions].
- e.g., 100% test coverage achieved.

## 6. Execution Steps
1. **[Step 1 Name]**: [Detailed description of the first action the Execution Engine must take].
2. **[Step 2 Name]**: [Detailed description of the second action].
3. **[Step N Name]**: [Detailed description...].
