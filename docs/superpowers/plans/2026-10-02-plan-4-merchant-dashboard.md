# Plan 4 — Merchant Dashboard and Admin Console (fluxpay-frontend)

> **For agentic workers:** execute with superpowers:executing-plans (native, one fresh reviewer at the end). Ideas only here; code lives in the source files.

**Goal:** A merchant can sign up, log in, switch test/live mode, manage products, payment links, API keys and webhook endpoints, inspect sales, events, balance and analytics, and edit branding. The platform admin can manage merchants and record payouts. The hosted checkout pages are out of scope (Plan 5).

**Architecture:** Next.js 15 App Router client-rendered console. The browser calls the same-origin path `/api/v1/*`, and `next.config` rewrites it to `BACKEND_URL`, so `SESSION` and `XSRF-TOKEN` are first-party cookies. TanStack Query holds all server state, with the mode in every query key. Features live in `src/features/<feature>/{components,api,hooks,schemas}`. Routes in `src/app` only compose feature components.

**Tech stack:** Next.js 15, React 19, TypeScript 5 (strict), Tailwind 4, shadcn/ui, TanStack Query 5 and Table 8, react-hook-form 7 with zod 4, Recharts 3, Vitest with Testing Library (jsdom), pnpm, ESLint.

**Spec:** `docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md` §3.2, §8 (dashboard auth), §9 (errors), §10 (dashboard and admin).

## Global constraints
- Only the libraries in `.engineering/config/stack.yaml`, plus dev-only test tooling (Vitest, Testing Library, jsdom) with MIT or Apache licences.
- Features never import other features. Shared code goes in `src/lib` or `src/components`. One component per file, under 250 lines. No `any`.
- 2-space indent, single quotes, 100-column lines, TSDoc on exported functions and hooks.
- Money is integer paise end to end. Rupee input is converted to paise with string arithmetic, never floats. Display uses `en-IN` INR formatting.
- The JSON is snake_case. TypeScript types mirror it exactly, so there is no case-conversion layer.
- Every POST, PATCH and DELETE sends `X-XSRF-TOKEN` (the value of the `XSRF-TOKEN` cookie). Every `/dashboard` call sends `FluxPay-Mode`.
- Layouts work at 320 px, 768 px and 1200 px or wider. Interactive elements are real buttons and links with labels.
- Commit to `main` after each task (Conventional Commits with the Co-Authored-By trailer), then push.

## Review focus
1. **Expired session mid-use:** any 401 from the API clears the cached user and sends the user to `/login?next=<path>`, with no error toast loop. (Task 2 client test, Task 3 guard test.)
2. **CSRF token missing or rotated** (first visit, or after login or logout): a mutation that gets `403 CSRF_TOKEN_INVALID` fetches `/auth/csrf` once and retries once. (Task 2.)
3. **Rupee input edge cases:** "499", "499.5", "499.50", "1,299", "0.999", "-5", "" and amounts outside ₹1 to ₹5,00,000 must give exact paise or a field error, never float drift. (Task 2 money tests, Task 5 product form test.)
4. **Show-once secrets:** API key and webhook secrets appear only in the creation or roll dialog, and are dropped from the cache when it closes. (Task 7.)
5. **Mode switch:** switching test and live refetches every list and never shows test data under the live label. The query key always includes the mode. (Task 4 test.)

---

## Task 1 — Scaffold
- Create a Next.js 15 app in `fluxpay-frontend` with TypeScript strict, the App Router, `src/` and Tailwind 4. Initialise shadcn/ui with the neutral base and add the needed primitives: button, input, label, card, table, dialog, dropdown-menu, select, badge, tabs, sonner, switch, textarea, skeleton, separator, sheet, form, alert and tooltip.
- In `next.config.ts`, rewrite `/api/:path*` to `${BACKEND_URL}/api/:path*` (default `http://localhost:8080`). `.env.example` lists `BACKEND_URL`.
- Vitest config uses jsdom, the `@/` alias and a setup file with jest-dom matchers. Scripts: `dev`, `build`, `lint`, `typecheck`, `test`.
- `src/app/providers.tsx` provides the QueryClient (no retries on 4xx) and the Toaster.
- GitHub Actions workflow `ci.yml`: install with pnpm, then lint, typecheck, test and build.
- **Test:** a smoke test that the providers render their children.

## Task 2 — `src/lib` foundation
- **`lib/api/client.ts` — `apiFetch<T>(path, {method, body, mode, query})`:**
  - Prefixes `/api/v1`, uses same-origin credentials, sends JSON and adds `FluxPay-Mode` when a mode is given.
  - For mutations, reads the `XSRF-TOKEN` cookie; if it is missing, it calls `GET /auth/csrf` first.
  - On `403 CSRF_TOKEN_INVALID`, it refreshes the token and retries once.
  - A 204 returns `undefined`. Errors throw `ApiError` (status, code, message, details, trace_id), parsed from the envelope with a fallback for responses that are not JSON.
  - A 401 calls a registered `onUnauthorized` handler.
- **`lib/api/types.ts`:** `ListResponse<T>` {data, cursor, has_more}, `ApiErrorBody` and `Mode`.
- **`lib/money.ts`:** `formatPaise(paise)` gives "₹1,299.00", and `rupeesToPaise(text)` returns paise or null for invalid input (up to 2 decimals, commas allowed, non-negative).
- **`lib/dates.ts`:** `formatDateTime` and `formatDate` in `en-IN`, plus `isoDay(date)` for analytics ranges.
- **`lib/mode/`:** a `ModeProvider` context (test or live, remembered in localStorage, default test) and a `useMode` hook.
- **`lib/forms/applyApiErrors.ts`:** maps a 422 `details[{field,...}]` onto react-hook-form `setError`. A field error with no matching form field becomes a root error.
- **`lib/hooks/useCursorList`:** wraps `useInfiniteQuery` for cursor lists, with a "Load more" button.
- **Tests:**
  - Client: the CSRF header is sent, a missing cookie triggers a token fetch, a CSRF 403 retries once, the error envelope is parsed, a 401 calls the handler, a 204 is handled, and the mode header is sent.
  - The money cases from Review focus 3.
  - `applyApiErrors` mapping.

## Task 3 — Auth feature
- **`features/auth`:**
  - Login and signup forms. The zod schemas are: business name 1–100 characters, a valid email, and a password of at least 10 characters and at most 72 bytes.
  - `useMe` (`GET /auth/me`, with a 401 treated as null), `useLogin`, `useSignup` and `useLogout`.
- **Routes:** `(auth)/login` and `(auth)/signup`. After success, go to `?next` if it is a safe relative path, otherwise `/dashboard`, or `/admin` for `platform_admin`.
- **`src/middleware.ts`:** redirects `/dashboard/*` and `/admin/*` to `/login?next=…` when there is no `SESSION` cookie. This is a cheap first gate; `useMe` is the real check.
- **`AuthGuard` component:** takes a required role, shows a skeleton while loading, redirects on null, and sends the wrong role to its own home.
- **Tests:**
  - Login form validation and the submit payload.
  - A server 401 shows "Invalid email or password".
  - A signup 422 maps to its fields.
  - The guard redirects and checks roles.
  - The `next` sanitiser rejects `//evil.com` and absolute URLs.

## Task 4 — Dashboard shell
- `app/dashboard/layout.tsx` wraps `AuthGuard(merchant_owner)`, `ModeProvider` and `DashboardShell`.
- **`DashboardShell`:**
  - A sidebar on desktop and a sheet on mobile, with Home, Products, Payment links, Sales, Balance, Developers and Settings.
  - A mode switch in the header.
  - A "Test mode" banner while in test mode.
  - A user menu with the email and Log out.
- **`lib/api/queryKeys`:** a convention where keys are `[feature, mode, …params]`.
- **Tests:**
  - The nav renders all items and marks the active one.
  - The mode switch changes the mode and the banner.
  - The hooks under test put the mode in the key, which a key test checks.

## Task 5 — Products and payment links
- **`features/products`:**
  - A table with name, price, status and created date, and a filter for active, archived or all.
  - A create/edit dialog (name, description, https image URL, price in rupees, metadata as key/value rows) and archive/unarchive (PATCH `active`).
- **`features/payment-links`:**
  - A table with the product name (resolved from the products list), the URL with a copy button, and active status.
  - Create (choose an active product, optional success and cancel URLs) and toggle active.
- **Routes:** `dashboard/products` and `dashboard/payment-links`.
- **Tests:**
  - Product form: rupee conversion to paise in the payload, the price bounds error, and the https-only image URL.
  - Edit sends only the changed fields.
  - The payment-link form needs a product.
  - The table renders formatted rows and an empty state.

## Task 6 — Sales, balance and ledger
- **`features/sales`:**
  - A table with filters for status, product and `customer_ref` search, and "Load more".
  - Detail page `dashboard/sales/[id]`: amounts, refunded amount, status, customer ref, metadata, and the payment (gateway payment ID, method, gateway fee, status).
- **`features/balance`:** cards for available, gross, platform fees, gateway fees, refunds and payouts, plus a ledger table with type badges and signed amounts.
- **Tests:**
  - Filters become query params.
  - The detail view renders the payment section.
  - Balance cards format paise.
  - Ledger rows show the type label.

## Task 7 — Developers
- **`features/developers`:**
  - **API keys:** a list (prefix, created, last used, revoked) and create/roll with a show-once secret dialog with copy and an "I've saved it" confirmation. Revoke has a confirmation dialog.
  - **Webhook endpoints:** create (URL) with a show-once `whsec_` secret, enable toggle, edit URL, roll secret, send test and delete. The limit-reached error is shown.
  - **Events:** a list with type and created time. The detail sheet has the JSON payload, deliveries (status, attempts, last code and error, next attempt) and a Resend button.
  - **Quick start:** a curl snippet that creates a checkout session with the current mode's key prefix, and a webhook signature verification note.
- **Routes:** `dashboard/developers` with tabs for API keys, Webhooks, Events and Quick start.
- **Tests:**
  - The secret dialog appears after create and the secret is gone after close.
  - Revoke needs confirmation.
  - Endpoint URL validation errors from the server show on the field.
  - Resend calls the endpoint.

## Task 8 — Settings
- **`features/settings`:** a form for business name, logo URL (https or empty) and brand colour (hex, with a colour swatch preview). It PATCHes the merchant. The slug and platform fee are read-only.
- **Tests:** the hex validation, the empty logo clearing the field, and that only changed fields are sent.

## Task 9 — Home analytics
- **`features/analytics`:**
  - A range picker with Today, 7d, 30d and Custom (from/to dates, at most 366 days).
  - KPI cards for gross, net, sales count, refunds and fees.
  - A Recharts area chart of revenue per day, with a toggle for sales count.
  - A top-products list with names resolved from products.
  - The 5 most recent sales.
- **Route:** `dashboard/page.tsx`.
- **Tests:**
  - Range presets produce the right from/to.
  - An inverted custom range is rejected.
  - KPI cards render the summary.
  - The chart gets the mapped data, including empty days.

## Task 10 — Admin console
- `app/admin/layout.tsx` uses `AuthGuard(platform_admin)` with a minimal shell.
- **`features/admin`:**
  - The merchants table: business name, slug, fee in percent, status, created.
  - The merchant detail page `admin/merchants/[id]`:
    - Test and live balances.
    - An edit form for the fee (bps, 0–10000, shown as a %) and status (active or suspended, with a confirmation dialog).
    - A payouts table and a record-payout form (mode, rupee amount, reference, paid-at date) that is limited to the available balance on the client.
- **Tests:**
  - Fee bounds.
  - The payout amount converts to paise and cannot be above the available balance.
  - Suspend needs confirmation.

## Task 11 — Wrap-up
- Run the app against the local backend (signup → product → link → keys → endpoint), fix what breaks, and update `.engineering/memory/features.yaml` and the README (setup and env).
- Get one fresh reviewer for the whole plan and apply valid findings.
- Known gaps, written up as tech debt in the frontend registry:
  - Sale detail cannot list webhook history because the backend has no filter for events by sale.
  - The dashboard has no refund action because there is no dashboard refund endpoint in v1.
