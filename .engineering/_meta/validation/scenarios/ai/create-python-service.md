# Scenario: Create a Python AI Service

**Domain**: AI / Machine Learning
**Difficulty**: Architecture Expansion

## Description
We are introducing a new standalone microservice written in Python (FastAPI) that consumes the central message broker to perform sentiment analysis on user reviews.

## Expected Workflow
- `workflows/new-service.md`
- Needs `reviews/architecture.md`, `reviews/production-readiness.md`

## Exit Criteria
- New service repository/module initialized.
- Subscribes to `ReviewSubmittedEvent`.
- Emits `SentimentAnalyzedEvent`.
