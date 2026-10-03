# Plan 6: E2E Testing and Release

**Goal:** Ensure the entire platform (from merchant signup to payment processing and webhook delivery) functions correctly via an end-to-end integration test. Perform a final verification of the application to prepare for the v1 release.

## Features & Implementation

1. **Playwright E2E Suite (`fluxpay-frontend/e2e`)**:
   - Set up Playwright for E2E testing if not already configured.
   - Implement the core happy-path flow:
     1. Merchant signs up.
     2. Merchant creates a new product.
     3. Merchant creates a checkout session via the API (using their generated API key).
     4. Simulate the consumer visiting the checkout page (`/c/[id]`).
     5. Complete payment using the Razorpay test mode.
     6. Verify the sale appears in the merchant dashboard.
     7. (Optional but recommended) Verify that the webhook was dispatched to a local receiver.

2. **Final Verification & Polish**:
   - Verify all unit and integration tests pass in both backend (`./gradlew test`) and frontend (`pnpm test`).
   - Run linter and build steps to ensure there are no remaining warnings or errors.
   - Review missing gaps (e.g. error handling, environment variables setup, final UI polish).

3. **Release Preparation**:
   - Tag `v1.0.0` or finalize the repository state for deployment.
