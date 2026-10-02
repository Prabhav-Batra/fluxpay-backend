# Plan 2 — Catalog, Checkout, Razorpay Payments, Sales, Ledger

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A merchant creates products and payment links; its backend creates a checkout session with an API key (or a customer opens a payment link); the customer pays through Razorpay; FluxPay verifies Razorpay's signed webhook, records exactly one sale with ledger entries (gross, platform fee, gateway fee), writes a `checkout.completed` event to the outbox, and handles refunds, expiry and missed webhooks.

**Architecture:** Adds modules `catalog` (products, payment links), `events` (outbox write side), `ledger` (append-only money entries, balances), `payments` (`PaymentGateway` interface, Razorpay adapter, inbound webhooks, payment records), `checkout` (sessions, hosted-checkout public API), `sales` (capture/refund processing, sales queries, scheduled jobs) and a generic `common.idempotency`. Module dependencies are exactly `.engineering/config/architecture.yaml`; `payments` never depends on `sales`, so it exposes a `GatewayEventHandler` interface that `sales` implements. Webhook delivery to merchants (Plan 3) reads the `events` table written here.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA (Hibernate 6, `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`), `RestClient` for Razorpay, Flyway, PostgreSQL 18, Testcontainers, MockRestServiceServer.

**Spec:** `docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md` — §4 (integration), §5 (products, payment_links, checkout_sessions, payments, sales, ledger_entries, events, idempotency_keys), §6 (payment lifecycle), §8 (gateway webhook security, redirect URLs, rate limits), §9 (errors, idempotency, pagination). Plan 1 is complete; its code is the baseline.

## Global Constraints

- Everything in Plan 1's Global Constraints still applies (package-by-feature layering, `FluxpayException` + `ErrorType`, snake_case JSON, prefixed public IDs with 404 on malformed/wrong prefix, tenant + mode only from the principal, ProjectOS line limits, Spotless before commit, Conventional Commits with the `Co-Authored-By` trailer).
- Commit directly on `main` and push after each task (owner's decision).
- Integration tests run on PostgreSQL **18** (Neon runs 18).
- Money is `long` paise; currency is `INR` only; product `amount` is 100–50,000,000 paise (₹1–₹5,00,000).
- Platform fee = `round_half_up(gross × platform_fee_bps / 10000)`, computed by the ledger from the merchant's `platform_fee_bps`.
- New tenant-owned repositories extend `org.springframework.data.repository.Repository` (not `JpaRepository`) and declare only tenant-scoped finders, except lookups the gateway flow needs by gateway ID.
- Services that must run inside a caller's transaction (`EventPublisher.publish`, `LedgerService.recordSale/recordRefund`, `CheckoutService.lockByGatewayOrderId/markCompleted/expireDue`, `PaymentRecordService.record`) use `@Transactional(propagation = Propagation.MANDATORY)`.
- No outbound HTTP call (Razorpay) is made while a database transaction is open.
- Razorpay credentials are per mode: `RAZORPAY_TEST_KEY_ID`, `RAZORPAY_TEST_KEY_SECRET`, `RAZORPAY_TEST_WEBHOOK_SECRET` (required) and `RAZORPAY_LIVE_*` (optional; live payments fail with `GATEWAY_NOT_CONFIGURED` until set). Webhook URL per mode: `/api/v1/gateway-webhooks/razorpay/{test|live}`.
- List endpoints: `?limit=` (1–100, default 20) and `?starting_after=<public id>`; response `{ "data": [...], "cursor": "<id or null>", "has_more": bool }`, newest first.
- Public (no-auth) routes live under `/api/v1/public/**` and are rate-limited by config.

## Review Focus

1. Razorpay delivers the same `payment.captured` webhook twice (including two deliveries at the same moment) → exactly one sale, one set of ledger entries, one `checkout.completed` event.
2. The customer pays after the session's 30-minute expiry (or after the expiry job marked it expired) → the money was taken, so the sale is still recorded and the session becomes `completed`.
3. The captured amount differs from the session's price snapshot (or the merchant changed the product price after the session was created) → no sale for a mismatch; the snapshot price, not the current product price, is what is charged and recorded.
4. The customer double-clicks "Pay" (two concurrent `POST /pay`) → both responses carry the same Razorpay order ID; the session has exactly one attached order.
5. A merchant retries `POST /checkout_sessions` with the same `Idempotency-Key` but a different body → 409 `IDEMPOTENCY_KEY_REUSED`, never a second session; same key and same body → the original session is returned.

Tests: #1 Task 9, #2 Task 9, #3 Tasks 8 and 9, #4 Task 8, #5 Tasks 7 and 8.

---

## File Map (new unless marked)

```
src/main/java/com/fluxpay/
├── config/SecurityConfig.java (modify), config/SchedulingConfig.java
├── common/
│   ├── id/PublicId.java (modify: parseOrNotFound)
│   ├── pagination/ PageQuery, CursorPage
│   ├── validation/ Metadata
│   ├── web/ RedirectUrlPolicy
│   └── idempotency/ IdempotencyService, IdempotencyCleanupJob
├── catalog/
│   ├── domain/ Product, ProductType, PaymentLink
│   ├── persistence/ ProductRepository, PaymentLinkRepository
│   ├── service/ ProductView, NewProduct, ProductChanges, ProductService(+Impl),
│   │            PaymentLinkView, NewPaymentLink, PaymentLinkChanges, PaymentLinkService(+Impl)
│   └── api/ DashboardProductController, ProductApiController, CreateProductRequest, UpdateProductRequest,
│            ProductResponse, PaymentLinkController, CreatePaymentLinkRequest, UpdatePaymentLinkRequest,
│            PaymentLinkResponse
├── events/
│   ├── domain/ Event, EventType
│   ├── persistence/ EventRepository
│   └── service/ EventView, EventPublisher(+Impl)
├── ledger/
│   ├── domain/ LedgerEntry, LedgerEntryType
│   ├── persistence/ LedgerEntryRepository
│   ├── service/ FeeCalculator, SaleEntries, Balance, LedgerEntryView, LedgerService(+Impl)
│   └── api/ BalanceController, BalanceResponse, LedgerEntryResponse
├── payments/
│   ├── domain/ Payment, PaymentStatus
│   ├── persistence/ PaymentRepository
│   ├── service/ RazorpayProperties, RazorpayHttpConfig, PaymentGateway, GatewayOrder, GatewayPayment,
│   │            GatewayRefund, WebhookSignatures, RazorpayPayloads, RazorpayGateway,
│   │            GatewayWebhookEvent, RazorpayWebhookParser, GatewayEventHandler,
│   │            NewPaymentRecord, RecordedPayment, PaymentRecordService(+Impl)
│   └── api/ RazorpayWebhookController
├── checkout/
│   ├── domain/ CheckoutSession, CheckoutStatus
│   ├── persistence/ CheckoutSessionRepository
│   ├── service/ CheckoutProperties, NewCheckoutSession, CheckoutSessionView, PublicCheckoutView,
│   │            PaymentInstructions, CheckoutService(+Impl)
│   └── api/ CheckoutSessionApiController, CreateCheckoutSessionRequest, CheckoutSessionResponse,
│            PublicCheckoutController, PublicCheckoutResponse, PaymentInstructionsResponse,
│            LinkCheckoutRequest, LinkCheckoutResponse
└── sales/
    ├── domain/ Sale, SaleStatus
    ├── persistence/ SaleRepository, SaleSpecifications
    ├── service/ SalePayloads, SaleCaptureService(+Impl), SaleRefundService(+Impl), GatewayEventRouter,
    │            SaleView, SaleDetailView, SaleFilter, SalesQueryService(+Impl),
    │            JobsProperties, CheckoutExpiryService(+Impl), ReconciliationService(+Impl)
    ├── jobs/ CheckoutExpiryJob, ReconciliationJob
    └── api/ SaleApiController, DashboardSaleController, SaleResponse, SaleDetailResponse
src/main/resources/db/migration/ V4__products.sql … V10__payments_and_sales.sql (+ db/rollback/V4…V10__down.sql)
src/test/java/com/fluxpay/support/ FakePaymentGateway, TestWebhooks (new); TestcontainersConfiguration,
    AbstractIntegrationTest, TestMerchants (modify)
```

---

### Task 1: PostgreSQL 18 in tests, pagination and metadata primitives

**Files:**
- Modify: `src/test/java/com/fluxpay/support/TestcontainersConfiguration.java`, `src/main/java/com/fluxpay/common/id/PublicId.java`
- Create: `src/main/java/com/fluxpay/common/pagination/{PageQuery,CursorPage}.java`, `src/main/java/com/fluxpay/common/validation/Metadata.java`
- Test: `src/test/java/com/fluxpay/common/pagination/{PageQueryTest,CursorPageTest}.java`, `src/test/java/com/fluxpay/common/validation/MetadataTest.java`, `src/test/java/com/fluxpay/common/id/PublicIdTest.java` (modify)

**Interfaces:**
- Produces:
  - `record PageQuery(UUID startingAfter, int limit)`: `static PageQuery of(IdPrefix prefix, String startingAfter, Integer limit)` (400 `INVALID_LIMIT` / `INVALID_CURSOR`), `UUID before()` (startingAfter or the max UUID), `int fetchSize()` (= limit + 1)
  - `record CursorPage<T>(List<T> data, String cursor, boolean hasMore)`: `static <E, T> CursorPage<T> from(List<E> rows, PageQuery query, Function<E, T> mapper, Function<T, String> cursorOf)`, `<R> CursorPage<R> map(Function<T, R>)`
  - `Metadata.validated(Map<String, String>): Map<String, String>` — null → empty; ≤ 20 keys; key `[A-Za-z0-9_.-]{1,40}`; value non-null ≤ 500 chars; violations → 422 with detail field `metadata` and codes `METADATA_TOO_MANY_KEYS` / `METADATA_INVALID_KEY` / `METADATA_INVALID_VALUE`
  - `PublicId.parseOrNotFound(IdPrefix, String publicId, String code, String message): UUID`

- [ ] **Step 1: Switch the test database to PostgreSQL 18**

In `src/test/java/com/fluxpay/support/TestcontainersConfiguration.java` replace `"postgres:17-alpine"` with `"postgres:18-alpine"`. Run `./gradlew test` — expected PASS (the Plan 1 suite on PG 18).

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/fluxpay/common/pagination/PageQueryTest.java`:
```java
package com.fluxpay.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.id.UuidV7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void should_default_to_20_and_max_uuid_when_no_params() {
        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, null, null);

        assertThat(query.limit()).isEqualTo(20);
        assertThat(query.before()).isEqualTo(new UUID(-1L, -1L));
        assertThat(query.fetchSize()).isEqualTo(21);
    }

    @Test
    void should_decode_cursor_when_starting_after_is_a_valid_id() {
        UUID id = UuidV7.generate();

        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, PublicId.of(IdPrefix.PRODUCT, id), 5);

        assertThat(query.before()).isEqualTo(id);
        assertThat(query.limit()).isEqualTo(5);
    }

    @Test
    void should_reject_limit_outside_1_to_100() {
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, null, 0))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_LIMIT");
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, null, 101))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_LIMIT");
    }

    @Test
    void should_reject_cursor_with_wrong_prefix_or_garbage() {
        String saleId = PublicId.of(IdPrefix.SALE, UuidV7.generate());

        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, saleId, null))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CURSOR");
        assertThatThrownBy(() -> PageQuery.of(IdPrefix.PRODUCT, "nope", null))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_CURSOR");
    }
}
```

`src/test/java/com/fluxpay/common/pagination/CursorPageTest.java`:
```java
package com.fluxpay.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.common.id.IdPrefix;
import java.util.List;
import org.junit.jupiter.api.Test;

class CursorPageTest {

    private final PageQuery limitTwo = PageQuery.of(IdPrefix.PRODUCT, null, 2);

    @Test
    void should_trim_extra_row_and_set_cursor_when_more_rows_exist() {
        CursorPage<String> page = CursorPage.from(List.of(1, 2, 3), limitTwo, i -> "item" + i, s -> s);

        assertThat(page.data()).containsExactly("item1", "item2");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.cursor()).isEqualTo("item2");
    }

    @Test
    void should_have_no_cursor_when_last_page() {
        CursorPage<String> page = CursorPage.from(List.of(1), limitTwo, i -> "item" + i, s -> s);

        assertThat(page.hasMore()).isFalse();
        assertThat(page.cursor()).isNull();
    }

    @Test
    void should_keep_cursor_and_flag_when_mapping() {
        CursorPage<String> page = CursorPage.from(List.of(1, 2, 3), limitTwo, i -> "item" + i, s -> s);

        CursorPage<Integer> mapped = page.map(String::length);

        assertThat(mapped.data()).containsExactly(5, 5);
        assertThat(mapped.cursor()).isEqualTo("item2");
        assertThat(mapped.hasMore()).isTrue();
    }
}
```

`src/test/java/com/fluxpay/common/validation/MetadataTest.java`:
```java
package com.fluxpay.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataTest {

    private static String detailCode(Throwable e) {
        return ((FluxpayException) e).details().get(0).code();
    }

    @Test
    void should_return_empty_map_when_null() {
        assertThat(Metadata.validated(null)).isEmpty();
    }

    @Test
    void should_accept_valid_metadata() {
        assertThat(Metadata.validated(Map.of("coins", "500", "pack.tier", "pro"))).containsEntry("coins", "500");
    }

    @Test
    void should_reject_more_than_20_keys() {
        Map<String, String> big = new HashMap<>();
        for (int i = 0; i < 21; i++) {
            big.put("k" + i, "v");
        }

        assertThatThrownBy(() -> Metadata.validated(big))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_TOO_MANY_KEYS"));
    }

    @Test
    void should_reject_bad_keys_and_values() {
        assertThatThrownBy(() -> Metadata.validated(Map.of("has space", "v")))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_KEY"));
        assertThatThrownBy(() -> Metadata.validated(Map.of("k", "x".repeat(501))))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_VALUE"));
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("k", null);
        assertThatThrownBy(() -> Metadata.validated(nullValue))
                .satisfies(e -> assertThat(detailCode(e)).isEqualTo("METADATA_INVALID_VALUE"));
    }
}
```

Append to `src/test/java/com/fluxpay/common/id/PublicIdTest.java` (before the final `}`), adding imports `com.fluxpay.common.error.FluxpayException` and `static org.assertj.core.api.Assertions.assertThatThrownBy`:
```java
    @Test
    void should_throw_not_found_with_given_code_when_parse_or_not_found_fails() {
        assertThatThrownBy(() -> PublicId.parseOrNotFound(IdPrefix.PRODUCT, "bad", "PRODUCT_NOT_FOUND", "Product not found"))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("PRODUCT_NOT_FOUND");
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.pagination.*' --tests 'com.fluxpay.common.validation.*' --tests 'com.fluxpay.common.id.*'`
Expected: FAIL — compilation errors (classes and `parseOrNotFound` missing).

- [ ] **Step 4: Implement**

`src/main/java/com/fluxpay/common/pagination/PageQuery.java`:
```java
package com.fluxpay.common.pagination;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import java.util.UUID;

/** Cursor pagination over UUIDv7 ids (newest first). {@link #before()} feeds {@code id < :before} queries. */
public record PageQuery(UUID startingAfter, int limit) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;
    private static final UUID MAX_UUID = new UUID(-1L, -1L);

    public static PageQuery of(IdPrefix prefix, String startingAfter, Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (resolvedLimit < 1 || resolvedLimit > MAX_LIMIT) {
            throw FluxpayException.badRequest("INVALID_LIMIT", "limit must be between 1 and " + MAX_LIMIT);
        }
        if (startingAfter == null || startingAfter.isEmpty()) {
            return new PageQuery(null, resolvedLimit);
        }
        UUID id = PublicId.parse(prefix, startingAfter)
                .orElseThrow(() -> FluxpayException.badRequest(
                        "INVALID_CURSOR", "starting_after must be a " + prefix.value() + "_ id"));
        return new PageQuery(id, resolvedLimit);
    }

    public UUID before() {
        return startingAfter == null ? MAX_UUID : startingAfter;
    }

    public int fetchSize() {
        return limit + 1;
    }
}
```

`src/main/java/com/fluxpay/common/pagination/CursorPage.java`:
```java
package com.fluxpay.common.pagination;

import java.util.List;
import java.util.function.Function;

public record CursorPage<T>(List<T> data, String cursor, boolean hasMore) {

    /** {@code rows} must have been fetched with {@link PageQuery#fetchSize()} so one extra row signals more. */
    public static <E, T> CursorPage<T> from(
            List<E> rows, PageQuery query, Function<E, T> mapper, Function<T, String> cursorOf) {
        boolean hasMore = rows.size() > query.limit();
        List<T> data = rows.stream().limit(query.limit()).map(mapper).toList();
        String cursor = hasMore ? cursorOf.apply(data.get(data.size() - 1)) : null;
        return new CursorPage<>(data, cursor, hasMore);
    }

    public <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(data.stream().map(mapper).toList(), cursor, hasMore);
    }
}
```

`src/main/java/com/fluxpay/common/validation/Metadata.java`:
```java
package com.fluxpay.common.validation;

import com.fluxpay.common.error.FluxpayException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Free-form string key/value pairs merchants attach to products and checkout sessions. */
public final class Metadata {

    static final int MAX_KEYS = 20;
    static final int MAX_VALUE_LENGTH = 500;
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,40}");

    private Metadata() {}

    public static Map<String, String> validated(Map<String, String> metadata) {
        if (metadata == null) {
            return new LinkedHashMap<>();
        }
        if (metadata.size() > MAX_KEYS) {
            throw FluxpayException.validation(
                    "metadata", "METADATA_TOO_MANY_KEYS", "metadata may have at most " + MAX_KEYS + " keys");
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (entry.getKey() == null || !KEY.matcher(entry.getKey()).matches()) {
                throw FluxpayException.validation(
                        "metadata",
                        "METADATA_INVALID_KEY",
                        "metadata keys must be 1-40 letters, digits, '_', '-' or '.'");
            }
            if (entry.getValue() == null || entry.getValue().length() > MAX_VALUE_LENGTH) {
                throw FluxpayException.validation(
                        "metadata",
                        "METADATA_INVALID_VALUE",
                        "metadata values must be strings of at most " + MAX_VALUE_LENGTH + " characters");
            }
        }
        return new LinkedHashMap<>(metadata);
    }
}
```

Add to `src/main/java/com/fluxpay/common/id/PublicId.java` (import `com.fluxpay.common.error.FluxpayException`):
```java
    public static UUID parseOrNotFound(IdPrefix prefix, String publicId, String code, String message) {
        return parse(prefix, publicId).orElseThrow(() -> FluxpayException.notFound(code, message));
    }
```

- [ ] **Step 5: Run tests to verify they pass, then the full suite**

Run: `./gradlew test --tests 'com.fluxpay.common.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): cursor pagination, metadata validation and postgres 18 test database

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 2: Catalog — products

**Files:**
- Create: `src/main/resources/db/migration/V4__products.sql`, `src/main/resources/db/rollback/V4__down.sql`
- Create: `src/main/java/com/fluxpay/catalog/domain/{Product,ProductType}.java`, `catalog/persistence/ProductRepository.java`
- Create: `src/main/java/com/fluxpay/catalog/service/{ProductView,NewProduct,ProductChanges,ProductService,ProductServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/catalog/api/{DashboardProductController,ProductApiController,CreateProductRequest,UpdateProductRequest,ProductResponse}.java`
- Modify: `src/test/java/com/fluxpay/support/TestMerchants.java`
- Test: `src/test/java/com/fluxpay/catalog/ProductIntegrationTest.java`

**Interfaces:**
- Consumes: `TenantContext`, `PageQuery`, `CursorPage`, `Metadata`, `PublicId.parseOrNotFound`.
- Produces:
  - `record ProductView(UUID id, Mode mode, String name, String description, String imageUrl, long amount, String currency, ProductType type, Map<String, String> metadata, boolean active, Instant createdAt, Instant updatedAt)`
  - `interface ProductService { ProductView create(TenantContext, NewProduct); ProductView get(TenantContext, UUID); CursorPage<ProductView> list(TenantContext, PageQuery, Boolean active); ProductView update(TenantContext, UUID, ProductChanges); }` — unknown/foreign → 404 `PRODUCT_NOT_FOUND`; non-INR → 422 detail `UNSUPPORTED_CURRENCY`
  - Routes: dashboard `GET/POST /api/v1/dashboard/products`, `GET/PATCH /api/v1/dashboard/products/{id}` (archive = `PATCH {"active": false}`); API key `GET /api/v1/products`, `GET /api/v1/products/{id}`
  - `TestMerchants.createProduct(MockMvc, SignedIn, Mode, String name, long amount): String` (returns `prod_…`)

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V4__products.sql`:
```sql
CREATE TABLE products (
    id          UUID PRIMARY KEY,
    merchant_id UUID          NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    image_url   VARCHAR(500),
    amount      BIGINT        NOT NULL CHECK (amount BETWEEN 100 AND 50000000),
    currency    VARCHAR(3)    NOT NULL CHECK (currency = 'INR'),
    type        VARCHAR(20)   NOT NULL CHECK (type = 'ONE_TIME'),
    metadata    JSONB         NOT NULL DEFAULT '{}',
    active      BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX products_merchant_mode_idx ON products (merchant_id, mode, id DESC);
```

`src/main/resources/db/rollback/V4__down.sql`:
```sql
DROP TABLE IF EXISTS products;
DELETE FROM flyway_schema_history WHERE version = '4';
```

- [ ] **Step 2: Add the product fixture to `TestMerchants`**

Add imports `org.springframework.http.MediaType` (already present) and this method to `src/test/java/com/fluxpay/support/TestMerchants.java`:
```java
    public static String createProduct(MockMvc mockMvc, SignedIn merchant, Mode mode, String name, long amount)
            throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/dashboard/products")
                        .with(csrf())
                        .cookie(merchant.session())
                        .header("FluxPay-Mode", mode.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"amount\":%d}".formatted(name, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
```

- [ ] **Step 3: Write the failing test**

`src/test/java/com/fluxpay/catalog/ProductIntegrationTest.java`:
```java
package com.fluxpay.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class ProductIntegrationTest extends AbstractIntegrationTest {

    private static final String PRODUCTS = "/api/v1/dashboard/products";

    private SignedIn owner;

    @BeforeEach
    void signUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
    }

    private ResultActions createRaw(String body) throws Exception {
        return mockMvc.perform(post(PRODUCTS)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchProduct(String id, String body) throws Exception {
        return mockMvc.perform(patch(PRODUCTS + "/" + id)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_create_product_with_defaults() throws Exception {
        createRaw("{\"name\":\"Pro Pack\",\"amount\":4900,\"metadata\":{\"coins\":\"500\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("prod_")))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.type").value("one_time"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.mode").value("test"))
                .andExpect(jsonPath("$.metadata.coins").value("500"));
    }

    @Test
    void should_page_newest_first_with_cursor() throws Exception {
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Starter", 1900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite", 9900);

        String first = mockMvc.perform(get(PRODUCTS + "?limit=2").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("Elite"))
                .andExpect(jsonPath("$.has_more").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = JsonPath.read(first, "$.cursor");

        mockMvc.perform(get(PRODUCTS + "?limit=2&starting_after=" + cursor).cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Starter"))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.cursor").isEmpty());
    }

    @Test
    void should_update_and_archive_and_filter_by_active() throws Exception {
        String id = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite", 9900);

        patchProduct(id, "{\"name\":\"Pro Pack\",\"amount\":5900,\"description\":\"500 coins\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pro Pack"))
                .andExpect(jsonPath("$.amount").value(5900));
        patchProduct(id, "{\"description\":\"\",\"active\":false}")
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get(PRODUCTS + "?active=true").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Elite"));
    }

    @Test
    void should_return_422_for_invalid_amount_currency_metadata_or_image() throws Exception {
        createRaw("{\"name\":\"Cheap\",\"amount\":99}").andExpect(status().isUnprocessableEntity());
        createRaw("{\"name\":\"Dollar\",\"amount\":500,\"currency\":\"USD\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("UNSUPPORTED_CURRENCY"));
        createRaw("{\"name\":\"Bad\",\"amount\":500,\"metadata\":{\"has space\":\"x\"}}")
                .andExpect(status().isUnprocessableEntity());
        createRaw("{\"name\":\"Img\",\"amount\":500,\"image_url\":\"http://x.com/a.png\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_return_400_for_bad_cursor_or_limit() throws Exception {
        mockMvc.perform(get(PRODUCTS + "?starting_after=garbage").cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
        mockMvc.perform(get(PRODUCTS + "?limit=0").cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_LIMIT"));
    }

    @Test
    void should_expose_products_to_api_key_of_same_mode_only() throws Exception {
        String id = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro", 4900);
        String testKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/products/" + id).header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.name").value("Pro"));
        mockMvc.perform(get("/api/v1/products/" + id).header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }
}
```

- [ ] **Step 4: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.catalog.ProductIntegrationTest'`
Expected: FAIL — every test fails (404/401 on unknown routes; no compile errors because tests use HTTP only).

- [ ] **Step 5: Implement domain and persistence**

`src/main/java/com/fluxpay/catalog/domain/ProductType.java`:
```java
package com.fluxpay.catalog.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ProductType {
    ONE_TIME;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/catalog/domain/Product.java`:
```java
package com.fluxpay.catalog.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(
            UUID merchantId,
            Mode mode,
            String name,
            String description,
            String imageUrl,
            long amount,
            String currency,
            Map<String, String> metadata,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.amount = amount;
        this.currency = currency;
        this.type = ProductType.ONE_TIME;
        this.metadata = metadata;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void describe(String description, Instant now) {
        this.description = description;
        this.updatedAt = now;
    }

    public void changeImage(String imageUrl, Instant now) {
        this.imageUrl = imageUrl;
        this.updatedAt = now;
    }

    public void reprice(long amount, Instant now) {
        this.amount = amount;
        this.updatedAt = now;
    }

    public void replaceMetadata(Map<String, String> metadata, Instant now) {
        this.metadata = metadata;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public ProductType getType() {
        return type;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
```

`src/main/java/com/fluxpay/catalog/persistence/ProductRepository.java`:
```java
package com.fluxpay.catalog.persistence;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.common.tenant.Mode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Tenant-scoped finders only. */
public interface ProductRepository extends Repository<Product, UUID> {

    Product save(Product product);

    Optional<Product> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Query("select p from Product p where p.merchantId = :merchantId and p.mode = :mode and p.id < :before"
            + " and p.active in :actives order by p.id desc")
    List<Product> page(
            @Param("merchantId") UUID merchantId,
            @Param("mode") Mode mode,
            @Param("before") UUID before,
            @Param("actives") Collection<Boolean> actives,
            Limit limit);
}
```

- [ ] **Step 6: Implement the service layer**

`src/main/java/com/fluxpay/catalog/service/ProductView.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.catalog.domain.ProductType;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductView(
        UUID id,
        Mode mode,
        String name,
        String description,
        String imageUrl,
        long amount,
        String currency,
        ProductType type,
        Map<String, String> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getMode(),
                product.getName(),
                product.getDescription(),
                product.getImageUrl(),
                product.getAmount(),
                product.getCurrency(),
                product.getType(),
                Map.copyOf(product.getMetadata()),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
```

`src/main/java/com/fluxpay/catalog/service/NewProduct.java`:
```java
package com.fluxpay.catalog.service;

import java.util.Map;

public record NewProduct(
        String name, String description, String imageUrl, long amount, String currency, Map<String, String> metadata) {}
```

`src/main/java/com/fluxpay/catalog/service/ProductChanges.java`:
```java
package com.fluxpay.catalog.service;

import java.util.Map;

/** Null leaves a field unchanged; an empty description or image URL clears it. */
public record ProductChanges(
        String name, String description, String imageUrl, Long amount, Map<String, String> metadata, Boolean active) {}
```

`src/main/java/com/fluxpay/catalog/service/ProductService.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface ProductService {

    ProductView create(TenantContext tenant, NewProduct product);

    /** PRODUCT_NOT_FOUND (not found) unless the product belongs to this tenant and mode. */
    ProductView get(TenantContext tenant, UUID productId);

    /** {@code active} null lists all products. */
    CursorPage<ProductView> list(TenantContext tenant, PageQuery query, Boolean active);

    ProductView update(TenantContext tenant, UUID productId, ProductChanges changes);
}
```

`src/main/java/com/fluxpay/catalog/service/ProductServiceImpl.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.Product;
import com.fluxpay.catalog.persistence.ProductRepository;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.validation.Metadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    static final String SUPPORTED_CURRENCY = "INR";

    private final ProductRepository products;
    private final Clock clock;

    public ProductServiceImpl(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProductView create(TenantContext tenant, NewProduct product) {
        String currency = product.currency() == null ? SUPPORTED_CURRENCY : product.currency();
        if (!SUPPORTED_CURRENCY.equals(currency)) {
            throw FluxpayException.validation("currency", "UNSUPPORTED_CURRENCY", "Only INR is supported");
        }
        Product saved = products.save(new Product(
                tenant.merchantId(),
                tenant.mode(),
                product.name().trim(),
                blankToNull(product.description()),
                blankToNull(product.imageUrl()),
                product.amount(),
                currency,
                Metadata.validated(product.metadata()),
                Instant.now(clock)));
        return ProductView.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductView get(TenantContext tenant, UUID productId) {
        return ProductView.from(find(tenant, productId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ProductView> list(TenantContext tenant, PageQuery query, Boolean active) {
        List<Boolean> actives = active == null ? List.of(true, false) : List.of(active);
        List<Product> rows = products.page(
                tenant.merchantId(), tenant.mode(), query.before(), actives, Limit.of(query.fetchSize()));
        return CursorPage.from(rows, query, ProductView::from, view -> PublicId.of(IdPrefix.PRODUCT, view.id()));
    }

    @Override
    @Transactional
    public ProductView update(TenantContext tenant, UUID productId, ProductChanges changes) {
        Product product = find(tenant, productId);
        Instant now = Instant.now(clock);
        if (changes.name() != null) {
            product.rename(changes.name().trim(), now);
        }
        if (changes.description() != null) {
            product.describe(blankToNull(changes.description()), now);
        }
        if (changes.imageUrl() != null) {
            product.changeImage(blankToNull(changes.imageUrl()), now);
        }
        if (changes.amount() != null) {
            product.reprice(changes.amount(), now);
        }
        if (changes.metadata() != null) {
            product.replaceMetadata(Metadata.validated(changes.metadata()), now);
        }
        if (changes.active() != null) {
            product.setActive(changes.active(), now);
        }
        return ProductView.from(product);
    }

    private Product find(TenantContext tenant, UUID productId) {
        return products.findByIdAndMerchantIdAndMode(productId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("PRODUCT_NOT_FOUND", "Product not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
```

- [ ] **Step 7: Implement the API**

`src/main/java/com/fluxpay/catalog/api/CreateProductRequest.java`:
```java
package com.fluxpay.catalog.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Size(max = 500) @Pattern(regexp = "^https://\\S+$", message = "must be an https URL") String imageUrl,
        @NotNull @Min(100) @Max(50_000_000) Long amount,
        String currency,
        Map<String, String> metadata) {}
```

`src/main/java/com/fluxpay/catalog/api/UpdateProductRequest.java`:
```java
package com.fluxpay.catalog.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateProductRequest(
        @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Size(max = 1000) String description,
        @Size(max = 500) @Pattern(regexp = "^$|^https://\\S+$", message = "must be an https URL") String imageUrl,
        @Min(100) @Max(50_000_000) Long amount,
        Map<String, String> metadata,
        Boolean active) {}
```

`src/main/java/com/fluxpay/catalog/api/ProductResponse.java`:
```java
package com.fluxpay.catalog.api;

import com.fluxpay.catalog.domain.ProductType;
import com.fluxpay.catalog.service.ProductView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;

public record ProductResponse(
        String id,
        Mode mode,
        String name,
        String description,
        String imageUrl,
        long amount,
        String currency,
        ProductType type,
        Map<String, String> metadata,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(ProductView view) {
        return new ProductResponse(
                PublicId.of(IdPrefix.PRODUCT, view.id()),
                view.mode(),
                view.name(),
                view.description(),
                view.imageUrl(),
                view.amount(),
                view.currency(),
                view.type(),
                view.metadata(),
                view.active(),
                view.createdAt(),
                view.updatedAt());
    }
}
```

`src/main/java/com/fluxpay/catalog/api/DashboardProductController.java`:
```java
package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.NewProduct;
import com.fluxpay.catalog.service.ProductChanges;
import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/products")
public class DashboardProductController {

    private final ProductService productService;

    public DashboardProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public CursorPage<ProductResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean active) {
        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, startingAfter, limit);
        return productService.list(tenant, query, active).map(ProductResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(TenantContext tenant, @Valid @RequestBody CreateProductRequest body) {
        NewProduct product = new NewProduct(
                body.name(), body.description(), body.imageUrl(), body.amount(), body.currency(), body.metadata());
        return ProductResponse.from(productService.create(tenant, product));
    }

    @GetMapping("/{id}")
    public ProductResponse get(TenantContext tenant, @PathVariable String id) {
        return ProductResponse.from(productService.get(tenant, parseId(id)));
    }

    @PatchMapping("/{id}")
    public ProductResponse update(
            TenantContext tenant, @PathVariable String id, @Valid @RequestBody UpdateProductRequest body) {
        ProductChanges changes = new ProductChanges(
                body.name(), body.description(), body.imageUrl(), body.amount(), body.metadata(), body.active());
        return ProductResponse.from(productService.update(tenant, parseId(id), changes));
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.PRODUCT, id, "PRODUCT_NOT_FOUND", "Product not found");
    }
}
```

`src/main/java/com/fluxpay/catalog/api/ProductApiController.java`:
```java
package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only product access for merchant backends (API key). */
@RestController
@RequestMapping("/api/v1/products")
public class ProductApiController {

    private final ProductService productService;

    public ProductApiController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public CursorPage<ProductResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Boolean active) {
        PageQuery query = PageQuery.of(IdPrefix.PRODUCT, startingAfter, limit);
        return productService.list(tenant, query, active).map(ProductResponse::from);
    }

    @GetMapping("/{id}")
    public ProductResponse get(TenantContext tenant, @PathVariable String id) {
        return ProductResponse.from(productService.get(tenant, DashboardProductController.parseId(id)));
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.catalog.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(catalog): one-time products with dashboard management and api key read access

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 3: Catalog — payment links and redirect URL policy

**Files:**
- Create: `src/main/resources/db/migration/V5__payment_links.sql`, `src/main/resources/db/rollback/V5__down.sql`
- Create: `src/main/java/com/fluxpay/common/web/RedirectUrlPolicy.java`
- Create: `src/main/java/com/fluxpay/catalog/domain/PaymentLink.java`, `catalog/persistence/PaymentLinkRepository.java`
- Create: `src/main/java/com/fluxpay/catalog/service/{PaymentLinkView,NewPaymentLink,PaymentLinkChanges,PaymentLinkService,PaymentLinkServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/catalog/api/{PaymentLinkController,CreatePaymentLinkRequest,UpdatePaymentLinkRequest,PaymentLinkResponse}.java`
- Test: `src/test/java/com/fluxpay/common/web/RedirectUrlPolicyTest.java`, `src/test/java/com/fluxpay/catalog/PaymentLinkIntegrationTest.java`

**Interfaces:**
- Consumes: `ProductService.get`, `FluxpayProperties.frontendBaseUrl()`, `Base62.random`.
- Produces:
  - `RedirectUrlPolicy.validate(Mode mode, String field, String url)` — null allowed; > 2048 chars → `URL_TOO_LONG`; not absolute/has userinfo → `INVALID_URL`; not `https` → `INSECURE_URL`, except `http://localhost` / `http://127.0.0.1` in test mode
  - `record PaymentLinkView(UUID id, UUID merchantId, Mode mode, UUID productId, String slug, String url, String successUrl, String cancelUrl, boolean active, Instant createdAt)` (`url` = `{frontendBaseUrl}/l/{slug}`)
  - `interface PaymentLinkService { PaymentLinkView create(TenantContext, NewPaymentLink); PaymentLinkView get(TenantContext, UUID); CursorPage<PaymentLinkView> list(TenantContext, PageQuery); PaymentLinkView update(TenantContext, UUID, PaymentLinkChanges); Optional<PaymentLinkView> findActiveBySlug(String slug); }` — 404 `PAYMENT_LINK_NOT_FOUND`
  - Routes: `GET/POST /api/v1/dashboard/payment_links`, `GET/PATCH /api/v1/dashboard/payment_links/{id}`

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V5__payment_links.sql`:
```sql
CREATE TABLE payment_links (
    id          UUID PRIMARY KEY,
    merchant_id UUID          NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id  UUID          NOT NULL REFERENCES products (id),
    slug        VARCHAR(32)   NOT NULL UNIQUE,
    success_url VARCHAR(2048),
    cancel_url  VARCHAR(2048),
    active      BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX payment_links_merchant_mode_idx ON payment_links (merchant_id, mode, id DESC);
```

`src/main/resources/db/rollback/V5__down.sql`:
```sql
DROP TABLE IF EXISTS payment_links;
DELETE FROM flyway_schema_history WHERE version = '5';
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/fluxpay/common/web/RedirectUrlPolicyTest.java`:
```java
package com.fluxpay.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import org.junit.jupiter.api.Test;

class RedirectUrlPolicyTest {

    private static String code(Throwable e) {
        return ((FluxpayException) e).details().get(0).code();
    }

    @Test
    void should_accept_https_in_both_modes_and_null() {
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.LIVE, "success_url", "https://jextter.com/paid?x=1"))
                .doesNotThrowAnyException();
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", null)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_http_localhost_only_in_test_mode() {
        assertThatCode(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "http://localhost:3000/ok"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.LIVE, "success_url", "http://localhost:3000/ok"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INSECURE_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "http://jextter.com/ok"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INSECURE_URL"));
    }

    @Test
    void should_reject_scripts_relative_userinfo_and_overlong_urls() {
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "javascript:alert(1)"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "/relative"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() -> RedirectUrlPolicy.validate(Mode.TEST, "success_url", "https://user@evil.com"))
                .satisfies(e -> assertThat(code(e)).isEqualTo("INVALID_URL"));
        assertThatThrownBy(() ->
                        RedirectUrlPolicy.validate(Mode.TEST, "success_url", "https://a.com/" + "x".repeat(2048)))
                .satisfies(e -> assertThat(code(e)).isEqualTo("URL_TOO_LONG"));
    }
}
```

`src/test/java/com/fluxpay/catalog/PaymentLinkIntegrationTest.java`:
```java
package com.fluxpay.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PaymentLinkIntegrationTest extends AbstractIntegrationTest {

    private static final String LINKS = "/api/v1/dashboard/payment_links";

    private SignedIn owner;
    private String productId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
    }

    private ResultActions createLink(String body) throws Exception {
        return mockMvc.perform(post(LINKS)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void should_create_link_with_frontend_url_and_slug() throws Exception {
        createLink("{\"product_id\":\"%s\",\"success_url\":\"https://jextter.com/paid\"}".formatted(productId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("plink_")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.matchesPattern(
                        "http://localhost:3000/l/[0-9A-Za-z]{10}")))
                .andExpect(jsonPath("$.product_id").value(productId))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void should_list_and_deactivate_link() throws Exception {
        String id = JsonPath.read(
                createLink("{\"product_id\":\"%s\"}".formatted(productId))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.id");

        mockMvc.perform(patch(LINKS + "/" + id)
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get(LINKS).cookie(owner.session())).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void should_return_404_for_unknown_or_other_mode_product() throws Exception {
        String liveProduct = TestMerchants.createProduct(mockMvc, owner, Mode.LIVE, "Live", 4900);

        createLink("{\"product_id\":\"%s\"}".formatted(liveProduct))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        createLink("{\"product_id\":\"prod_nope\"}").andExpect(status().isNotFound());
    }

    @Test
    void should_return_422_for_insecure_redirect() throws Exception {
        createLink("{\"product_id\":\"%s\",\"success_url\":\"http://jextter.com\"}".formatted(productId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("INSECURE_URL"));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.common.web.RedirectUrlPolicyTest' --tests 'com.fluxpay.catalog.PaymentLinkIntegrationTest'`
Expected: FAIL — `RedirectUrlPolicy` does not compile.

- [ ] **Step 4: Implement the URL policy**

`src/main/java/com/fluxpay/common/web/RedirectUrlPolicy.java`:
```java
package com.fluxpay.common.web;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Where FluxPay may send a customer after checkout. Prevents open redirects and javascript: URLs. */
public final class RedirectUrlPolicy {

    static final int MAX_LENGTH = 2048;

    private RedirectUrlPolicy() {}

    public static void validate(Mode mode, String field, String url) {
        if (url == null) {
            return;
        }
        if (url.length() > MAX_LENGTH) {
            throw FluxpayException.validation(field, "URL_TOO_LONG", field + " must be at most 2048 characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw invalid(field);
        }
        if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null) {
            throw invalid(field);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean localHttp = "http".equals(scheme)
                && mode == Mode.TEST
                && ("localhost".equals(host) || "127.0.0.1".equals(host));
        if (!"https".equals(scheme) && !localHttp) {
            throw FluxpayException.validation(
                    field,
                    "INSECURE_URL",
                    field + " must use https (http is allowed only for localhost in test mode)");
        }
    }

    private static FluxpayException invalid(String field) {
        return FluxpayException.validation(field, "INVALID_URL", field + " must be an absolute http(s) URL");
    }
}
```

- [ ] **Step 5: Implement domain and persistence**

`src/main/java/com/fluxpay/catalog/domain/PaymentLink.java`:
```java
package com.fluxpay.catalog.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_links")
public class PaymentLink {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String slug;

    @Column(name = "success_url")
    private String successUrl;

    @Column(name = "cancel_url")
    private String cancelUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentLink() {}

    public PaymentLink(
            UUID merchantId,
            Mode mode,
            UUID productId,
            String slug,
            String successUrl,
            String cancelUrl,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.productId = productId;
        this.slug = slug;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void changeRedirects(String successUrl, String cancelUrl, Instant now) {
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSlug() {
        return slug;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/catalog/persistence/PaymentLinkRepository.java`:
```java
package com.fluxpay.catalog.persistence;

import com.fluxpay.catalog.domain.PaymentLink;
import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PaymentLinkRepository extends Repository<PaymentLink, UUID> {

    PaymentLink save(PaymentLink link);

    Optional<PaymentLink> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    /** Public lookup: the slug is the capability. */
    Optional<PaymentLink> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    @Query("select l from PaymentLink l where l.merchantId = :merchantId and l.mode = :mode and l.id < :before"
            + " order by l.id desc")
    List<PaymentLink> page(
            @Param("merchantId") UUID merchantId,
            @Param("mode") Mode mode,
            @Param("before") UUID before,
            Limit limit);
}
```

- [ ] **Step 6: Implement the service layer**

`src/main/java/com/fluxpay/catalog/service/PaymentLinkView.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

public record PaymentLinkView(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID productId,
        String slug,
        String url,
        String successUrl,
        String cancelUrl,
        boolean active,
        Instant createdAt) {}
```

`src/main/java/com/fluxpay/catalog/service/NewPaymentLink.java`:
```java
package com.fluxpay.catalog.service;

import java.util.UUID;

public record NewPaymentLink(UUID productId, String successUrl, String cancelUrl) {}
```

`src/main/java/com/fluxpay/catalog/service/PaymentLinkChanges.java`:
```java
package com.fluxpay.catalog.service;

/** Null leaves a field unchanged; an empty URL clears it. */
public record PaymentLinkChanges(String successUrl, String cancelUrl, Boolean active) {}
```

`src/main/java/com/fluxpay/catalog/service/PaymentLinkService.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;

public interface PaymentLinkService {

    /** PRODUCT_NOT_FOUND (not found) unless the product belongs to this tenant and mode. */
    PaymentLinkView create(TenantContext tenant, NewPaymentLink link);

    PaymentLinkView get(TenantContext tenant, UUID linkId);

    CursorPage<PaymentLinkView> list(TenantContext tenant, PageQuery query);

    PaymentLinkView update(TenantContext tenant, UUID linkId, PaymentLinkChanges changes);

    Optional<PaymentLinkView> findActiveBySlug(String slug);
}
```

`src/main/java/com/fluxpay/catalog/service/PaymentLinkServiceImpl.java`:
```java
package com.fluxpay.catalog.service;

import com.fluxpay.catalog.domain.PaymentLink;
import com.fluxpay.catalog.persistence.PaymentLinkRepository;
import com.fluxpay.common.config.FluxpayProperties;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.Base62;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.web.RedirectUrlPolicy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLinkServiceImpl implements PaymentLinkService {

    static final int SLUG_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentLinkRepository links;
    private final ProductService productService;
    private final FluxpayProperties properties;
    private final Clock clock;

    public PaymentLinkServiceImpl(
            PaymentLinkRepository links, ProductService productService, FluxpayProperties properties, Clock clock) {
        this.links = links;
        this.productService = productService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PaymentLinkView create(TenantContext tenant, NewPaymentLink link) {
        productService.get(tenant, link.productId());
        RedirectUrlPolicy.validate(tenant.mode(), "success_url", link.successUrl());
        RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", link.cancelUrl());
        PaymentLink saved = links.save(new PaymentLink(
                tenant.merchantId(),
                tenant.mode(),
                link.productId(),
                uniqueSlug(),
                blankToNull(link.successUrl()),
                blankToNull(link.cancelUrl()),
                Instant.now(clock)));
        return view(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentLinkView get(TenantContext tenant, UUID linkId) {
        return view(find(tenant, linkId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<PaymentLinkView> list(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                links.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                this::view,
                view -> PublicId.of(IdPrefix.PAYMENT_LINK, view.id()));
    }

    @Override
    @Transactional
    public PaymentLinkView update(TenantContext tenant, UUID linkId, PaymentLinkChanges changes) {
        PaymentLink link = find(tenant, linkId);
        Instant now = Instant.now(clock);
        if (changes.successUrl() != null || changes.cancelUrl() != null) {
            String success = changes.successUrl() == null ? link.getSuccessUrl() : blankToNull(changes.successUrl());
            String cancel = changes.cancelUrl() == null ? link.getCancelUrl() : blankToNull(changes.cancelUrl());
            RedirectUrlPolicy.validate(tenant.mode(), "success_url", success);
            RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", cancel);
            link.changeRedirects(success, cancel, now);
        }
        if (changes.active() != null) {
            link.setActive(changes.active(), now);
        }
        return view(link);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentLinkView> findActiveBySlug(String slug) {
        return links.findBySlugAndActiveTrue(slug).map(this::view);
    }

    private PaymentLink find(TenantContext tenant, UUID linkId) {
        return links.findByIdAndMerchantIdAndMode(linkId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("PAYMENT_LINK_NOT_FOUND", "Payment link not found"));
    }

    private String uniqueSlug() {
        String slug = Base62.random(SLUG_LENGTH, RANDOM);
        while (links.existsBySlug(slug)) {
            slug = Base62.random(SLUG_LENGTH, RANDOM);
        }
        return slug;
    }

    private PaymentLinkView view(PaymentLink link) {
        return new PaymentLinkView(
                link.getId(),
                link.getMerchantId(),
                link.getMode(),
                link.getProductId(),
                link.getSlug(),
                properties.frontendBaseUrl() + "/l/" + link.getSlug(),
                link.getSuccessUrl(),
                link.getCancelUrl(),
                link.isActive(),
                link.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
```

- [ ] **Step 7: Implement the API**

`src/main/java/com/fluxpay/catalog/api/CreatePaymentLinkRequest.java`:
```java
package com.fluxpay.catalog.api;

import jakarta.validation.constraints.NotBlank;

public record CreatePaymentLinkRequest(@NotBlank String productId, String successUrl, String cancelUrl) {}
```

`src/main/java/com/fluxpay/catalog/api/UpdatePaymentLinkRequest.java`:
```java
package com.fluxpay.catalog.api;

public record UpdatePaymentLinkRequest(String successUrl, String cancelUrl, Boolean active) {}
```

`src/main/java/com/fluxpay/catalog/api/PaymentLinkResponse.java`:
```java
package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.PaymentLinkView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record PaymentLinkResponse(
        String id,
        Mode mode,
        String productId,
        String slug,
        String url,
        String successUrl,
        String cancelUrl,
        boolean active,
        Instant createdAt) {

    public static PaymentLinkResponse from(PaymentLinkView view) {
        return new PaymentLinkResponse(
                PublicId.of(IdPrefix.PAYMENT_LINK, view.id()),
                view.mode(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                view.slug(),
                view.url(),
                view.successUrl(),
                view.cancelUrl(),
                view.active(),
                view.createdAt());
    }
}
```

`src/main/java/com/fluxpay/catalog/api/PaymentLinkController.java`:
```java
package com.fluxpay.catalog.api;

import com.fluxpay.catalog.service.NewPaymentLink;
import com.fluxpay.catalog.service.PaymentLinkChanges;
import com.fluxpay.catalog.service.PaymentLinkService;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/payment_links")
public class PaymentLinkController {

    private final PaymentLinkService paymentLinkService;

    public PaymentLinkController(PaymentLinkService paymentLinkService) {
        this.paymentLinkService = paymentLinkService;
    }

    @GetMapping
    public CursorPage<PaymentLinkResponse> list(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.PAYMENT_LINK, startingAfter, limit);
        return paymentLinkService.list(tenant, query).map(PaymentLinkResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentLinkResponse create(TenantContext tenant, @Valid @RequestBody CreatePaymentLinkRequest body) {
        UUID productId = DashboardProductController.parseId(body.productId());
        return PaymentLinkResponse.from(paymentLinkService.create(
                tenant, new NewPaymentLink(productId, body.successUrl(), body.cancelUrl())));
    }

    @GetMapping("/{id}")
    public PaymentLinkResponse get(TenantContext tenant, @PathVariable String id) {
        return PaymentLinkResponse.from(paymentLinkService.get(tenant, parseId(id)));
    }

    @PatchMapping("/{id}")
    public PaymentLinkResponse update(
            TenantContext tenant, @PathVariable String id, @Valid @RequestBody UpdatePaymentLinkRequest body) {
        PaymentLinkChanges changes = new PaymentLinkChanges(body.successUrl(), body.cancelUrl(), body.active());
        return PaymentLinkResponse.from(paymentLinkService.update(tenant, parseId(id), changes));
    }

    private static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.PAYMENT_LINK, id, "PAYMENT_LINK_NOT_FOUND", "Payment link not found");
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.common.web.*' --tests 'com.fluxpay.catalog.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 9: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(catalog): shareable payment links with safe redirect urls

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 4: Events — outbox write side

**Files:**
- Create: `src/main/resources/db/migration/V6__events.sql`, `src/main/resources/db/rollback/V6__down.sql`
- Create: `src/main/java/com/fluxpay/events/domain/{Event,EventType}.java`, `events/persistence/EventRepository.java`
- Create: `src/main/java/com/fluxpay/events/service/{EventView,EventPublisher,EventPublisherImpl}.java`
- Test: `src/test/java/com/fluxpay/events/EventPublisherIntegrationTest.java`

**Interfaces:**
- Produces:
  - `enum EventType { CHECKOUT_COMPLETED("checkout.completed"), CHECKOUT_EXPIRED("checkout.expired"), SALE_REFUNDED("sale.refunded") }` with `@JsonValue value()`
  - `record EventView(UUID id, UUID merchantId, Mode mode, EventType type, Map<String, Object> data, Instant createdAt)`
  - `interface EventPublisher { EventView publish(TenantContext tenant, EventType type, Map<String, Object> data); }` — `Propagation.MANDATORY`: calling without a transaction throws `IllegalTransactionStateException`
  - Plan 3 adds endpoints, deliveries and the dispatcher on top of the `events` table.

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V6__events.sql`:
```sql
CREATE TABLE events (
    id          UUID PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)  NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    type        VARCHAR(40) NOT NULL,
    data        JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX events_merchant_mode_idx ON events (merchant_id, mode, id DESC);
```

`src/main/resources/db/rollback/V6__down.sql`:
```sql
DROP TABLE IF EXISTS events;
DELETE FROM flyway_schema_history WHERE version = '6';
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/fluxpay/events/EventPublisherIntegrationTest.java`:
```java
package com.fluxpay.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import com.fluxpay.events.service.EventView;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class EventPublisherIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TenantContext tenant() throws Exception {
        String merchantId = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com").merchantId();
        UUID id = PublicId.parse(IdPrefix.MERCHANT, merchantId).orElseThrow();
        return new TenantContext(id, Mode.TEST);
    }

    @Test
    void should_store_event_when_published_inside_transaction() throws Exception {
        TenantContext tenant = tenant();

        EventView event = transactionTemplate.execute(status ->
                eventPublisher.publish(tenant, EventType.CHECKOUT_COMPLETED, Map.of("sale_id", "sale_x")));

        assertThat(event.type()).isEqualTo(EventType.CHECKOUT_COMPLETED);
        String stored = jdbcTemplate.queryForObject(
                "SELECT data->>'sale_id' FROM events WHERE type = 'CHECKOUT_COMPLETED'", String.class);
        assertThat(stored).isEqualTo("sale_x");
    }

    @Test
    void should_refuse_to_publish_outside_a_transaction() throws Exception {
        TenantContext tenant = tenant();

        assertThatThrownBy(() -> eventPublisher.publish(tenant, EventType.CHECKOUT_COMPLETED, Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_roll_back_event_when_surrounding_transaction_rolls_back() throws Exception {
        TenantContext tenant = tenant();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publish(tenant, EventType.SALE_REFUNDED, Map.of());
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class)).isZero();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.events.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement**

`src/main/java/com/fluxpay/events/domain/EventType.java`:
```java
package com.fluxpay.events.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EventType {
    CHECKOUT_COMPLETED("checkout.completed"),
    CHECKOUT_EXPIRED("checkout.expired"),
    SALE_REFUNDED("sale.refunded");

    private final String value;

    EventType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
```

`src/main/java/com/fluxpay/events/domain/Event.java`:
```java
package com.fluxpay.events.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "events")
public class Event {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Event() {}

    public Event(UUID merchantId, Mode mode, EventType type, Map<String, Object> data, Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.type = type;
        this.data = data;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public EventType getType() {
        return type;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/events/persistence/EventRepository.java`:
```java
package com.fluxpay.events.persistence;

import com.fluxpay.events.domain.Event;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface EventRepository extends Repository<Event, UUID> {

    Event save(Event event);
}
```

`src/main/java/com/fluxpay/events/service/EventView.java`:
```java
package com.fluxpay.events.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.events.domain.EventType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventView(
        UUID id, UUID merchantId, Mode mode, EventType type, Map<String, Object> data, Instant createdAt) {}
```

`src/main/java/com/fluxpay/events/service/EventPublisher.java`:
```java
package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import java.util.Map;

public interface EventPublisher {

    /**
     * Writes the event to the outbox in the caller's transaction, so an event exists if and only if the
     * change that caused it commits. Throws IllegalTransactionStateException when no transaction is active.
     */
    EventView publish(TenantContext tenant, EventType type, Map<String, Object> data);
}
```

`src/main/java/com/fluxpay/events/service/EventPublisherImpl.java`:
```java
package com.fluxpay.events.service;

import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.Event;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.persistence.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventPublisherImpl implements EventPublisher {

    private final EventRepository events;
    private final Clock clock;

    public EventPublisherImpl(EventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public EventView publish(TenantContext tenant, EventType type, Map<String, Object> data) {
        Event event = events.save(
                new Event(tenant.merchantId(), tenant.mode(), type, new LinkedHashMap<>(data), Instant.now(clock)));
        return new EventView(
                event.getId(),
                event.getMerchantId(),
                event.getMode(),
                event.getType(),
                event.getData(),
                event.getCreatedAt());
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.events.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(events): transactional outbox for merchant events

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 5: Ledger — entries, fees, balance

**Files:**
- Create: `src/main/resources/db/migration/V7__ledger_entries.sql`, `src/main/resources/db/rollback/V7__down.sql`
- Create: `src/main/java/com/fluxpay/ledger/domain/{LedgerEntry,LedgerEntryType}.java`, `ledger/persistence/LedgerEntryRepository.java`
- Create: `src/main/java/com/fluxpay/ledger/service/{FeeCalculator,SaleEntries,Balance,LedgerEntryView,LedgerService,LedgerServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/ledger/api/{BalanceController,BalanceResponse,LedgerEntryResponse}.java`
- Test: `src/test/java/com/fluxpay/ledger/{FeeCalculatorTest,LedgerIntegrationTest}.java`

**Interfaces:**
- Consumes: `MerchantService.get(merchantId).platformFeeBps()`.
- Produces:
  - `FeeCalculator.platformFee(long gross, int bps): long` — half-up rounding
  - `record SaleEntries(long platformFee, long gatewayFee, long net)`
  - `record Balance(long grossSales, long platformFees, long gatewayFees, long refunds, long payouts, long available, String currency)` — fee/refund/payout totals are the signed (negative) sums; `available` = sum of all entries
  - `record LedgerEntryView(UUID id, Mode mode, LedgerEntryType type, long amount, String currency, UUID saleId, String reference, Instant createdAt)`
  - `interface LedgerService { SaleEntries recordSale(TenantContext, UUID saleId, long gross, String currency, long gatewayFee); boolean recordRefund(TenantContext, UUID saleId, String gatewayRefundId, long amount, String currency); Balance balance(TenantContext); CursorPage<LedgerEntryView> entries(TenantContext, PageQuery); }` — `recordSale`/`recordRefund` are `MANDATORY`; `recordRefund` returns false when that refund ID was already recorded
  - Routes: `GET /api/v1/dashboard/balance`, `GET /api/v1/dashboard/ledger_entries`
  - `IdPrefix.LEDGER_ENTRY("le")` (new) for ledger entry IDs in the dashboard list

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V7__ledger_entries.sql`:
```sql
CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    merchant_id UUID         NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('SALE_GROSS', 'PLATFORM_FEE', 'GATEWAY_FEE', 'REFUND', 'PAYOUT')),
    amount      BIGINT       NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    sale_id     UUID,
    reference   VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ledger_entries_merchant_mode_idx ON ledger_entries (merchant_id, mode, id DESC);
CREATE INDEX ledger_entries_sale_idx ON ledger_entries (sale_id);
CREATE UNIQUE INDEX ledger_entries_type_reference_uq ON ledger_entries (type, reference) WHERE reference IS NOT NULL;
```

`src/main/resources/db/rollback/V7__down.sql`:
```sql
DROP TABLE IF EXISTS ledger_entries;
DELETE FROM flyway_schema_history WHERE version = '7';
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/fluxpay/ledger/FeeCalculatorTest.java`:
```java
package com.fluxpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.ledger.service.FeeCalculator;
import org.junit.jupiter.api.Test;

class FeeCalculatorTest {

    @Test
    void should_compute_exact_fee() {
        assertThat(FeeCalculator.platformFee(4900, 500)).isEqualTo(245);
    }

    @Test
    void should_round_half_up() {
        assertThat(FeeCalculator.platformFee(1999, 250)).isEqualTo(50); // 49.975
        assertThat(FeeCalculator.platformFee(1000, 5)).isEqualTo(1); // 0.5
        assertThat(FeeCalculator.platformFee(1000, 4)).isEqualTo(0); // 0.4
    }

    @Test
    void should_return_zero_for_zero_bps_and_full_amount_for_10000_bps() {
        assertThat(FeeCalculator.platformFee(4900, 0)).isZero();
        assertThat(FeeCalculator.platformFee(4900, 10_000)).isEqualTo(4900);
    }

    @Test
    void should_not_overflow_for_largest_product_amount() {
        assertThat(FeeCalculator.platformFee(50_000_000, 10_000)).isEqualTo(50_000_000);
    }
}
```

`src/test/java/com/fluxpay/ledger/LedgerIntegrationTest.java`:
```java
package com.fluxpay.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.service.Balance;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.ledger.service.SaleEntries;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private SignedIn owner;
    private TenantContext tenant;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        tenant = new TenantContext(PublicId.parse(IdPrefix.MERCHANT, owner.merchantId()).orElseThrow(), Mode.TEST);
    }

    @Test
    void should_record_gross_platform_fee_and_gateway_fee_for_a_sale() {
        SaleEntries entries = transactionTemplate.execute(
                status -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 116));

        assertThat(entries.platformFee()).isEqualTo(245);
        assertThat(entries.net()).isEqualTo(4900 - 245 - 116);
        Balance balance = ledgerService.balance(tenant);
        assertThat(balance.grossSales()).isEqualTo(4900);
        assertThat(balance.platformFees()).isEqualTo(-245);
        assertThat(balance.gatewayFees()).isEqualTo(-116);
        assertThat(balance.available()).isEqualTo(4539);
    }

    @Test
    void should_record_refund_once_per_gateway_refund_id() {
        UUID saleId = UuidV7.generate();
        transactionTemplate.executeWithoutResult(status -> ledgerService.recordSale(tenant, saleId, 4900, "INR", 0));

        Boolean first = transactionTemplate.execute(
                status -> ledgerService.recordRefund(tenant, saleId, "rfnd_1", 4900, "INR"));
        Boolean second = transactionTemplate.execute(
                status -> ledgerService.recordRefund(tenant, saleId, "rfnd_1", 4900, "INR"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(ledgerService.balance(tenant).refunds()).isEqualTo(-4900);
    }

    @Test
    void should_require_a_transaction_for_writes() {
        assertThatThrownBy(() -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 0))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void should_expose_balance_and_entries_on_dashboard_per_mode() throws Exception {
        transactionTemplate.executeWithoutResult(
                status -> ledgerService.recordSale(tenant, UuidV7.generate(), 4900, "INR", 116));

        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(owner.session()))
                .andExpect(jsonPath("$.gross_sales").value(4900))
                .andExpect(jsonPath("$.available").value(4539))
                .andExpect(jsonPath("$.currency").value("INR"));
        mockMvc.perform(get("/api/v1/dashboard/ledger_entries").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value(org.hamcrest.Matchers.startsWith("le_")));
        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(owner.session()).header("FluxPay-Mode", "live"))
                .andExpect(jsonPath("$.available").value(0));
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.ledger.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement domain and persistence**

Add `LEDGER_ENTRY("le"),` to `src/main/java/com/fluxpay/common/id/IdPrefix.java` (after `PAYOUT("po")`, changing its `;` to `,`).

`src/main/java/com/fluxpay/ledger/domain/LedgerEntryType.java`:
```java
package com.fluxpay.ledger.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum LedgerEntryType {
    SALE_GROSS,
    PLATFORM_FEE,
    GATEWAY_FEE,
    REFUND,
    PAYOUT;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/ledger/domain/LedgerEntry.java`:
```java
package com.fluxpay.ledger.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only: entries are never updated or deleted. Credits are positive, debits negative. */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "sale_id")
    private UUID saleId;

    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(
            UUID merchantId,
            Mode mode,
            LedgerEntryType type,
            long amount,
            String currency,
            UUID saleId,
            String reference,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.saleId = saleId;
        this.reference = reference;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Mode getMode() {
        return mode;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getSaleId() {
        return saleId;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/ledger/persistence/LedgerEntryRepository.java`:
```java
package com.fluxpay.ledger.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    boolean existsByTypeAndReference(LedgerEntryType type, String reference);

    @Query("select e from LedgerEntry e where e.merchantId = :merchantId and e.mode = :mode and e.id < :before"
            + " order by e.id desc")
    List<LedgerEntry> page(
            @Param("merchantId") UUID merchantId,
            @Param("mode") Mode mode,
            @Param("before") UUID before,
            Limit limit);

    @Query("select e.type, sum(e.amount) from LedgerEntry e where e.merchantId = :merchantId and e.mode = :mode"
            + " group by e.type")
    List<Object[]> totalsByType(@Param("merchantId") UUID merchantId, @Param("mode") Mode mode);
}
```

- [ ] **Step 5: Implement the service layer**

`src/main/java/com/fluxpay/ledger/service/FeeCalculator.java`:
```java
package com.fluxpay.ledger.service;

public final class FeeCalculator {

    private static final long BPS_DENOMINATOR = 10_000;

    private FeeCalculator() {}

    /** gross × bps / 10000, rounded half up. Inputs are non-negative. */
    public static long platformFee(long gross, int bps) {
        return (gross * bps + BPS_DENOMINATOR / 2) / BPS_DENOMINATOR;
    }
}
```

`src/main/java/com/fluxpay/ledger/service/SaleEntries.java`:
```java
package com.fluxpay.ledger.service;

public record SaleEntries(long platformFee, long gatewayFee, long net) {}
```

`src/main/java/com/fluxpay/ledger/service/Balance.java`:
```java
package com.fluxpay.ledger.service;

public record Balance(
        long grossSales,
        long platformFees,
        long gatewayFees,
        long refunds,
        long payouts,
        long available,
        String currency) {}
```

`src/main/java/com/fluxpay/ledger/service/LedgerEntryView.java`:
```java
package com.fluxpay.ledger.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        UUID id,
        Mode mode,
        LedgerEntryType type,
        long amount,
        String currency,
        UUID saleId,
        String reference,
        Instant createdAt) {

    static LedgerEntryView from(LedgerEntry entry) {
        return new LedgerEntryView(
                entry.getId(),
                entry.getMode(),
                entry.getType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getSaleId(),
                entry.getReference(),
                entry.getCreatedAt());
    }
}
```

`src/main/java/com/fluxpay/ledger/service/LedgerService.java`:
```java
package com.fluxpay.ledger.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface LedgerService {

    /** Writes gross, platform fee and gateway fee entries in the caller's transaction. */
    SaleEntries recordSale(TenantContext tenant, UUID saleId, long gross, String currency, long gatewayFee);

    /** Writes a refund entry in the caller's transaction. False when this gateway refund was already recorded. */
    boolean recordRefund(TenantContext tenant, UUID saleId, String gatewayRefundId, long amount, String currency);

    Balance balance(TenantContext tenant);

    CursorPage<LedgerEntryView> entries(TenantContext tenant, PageQuery query);
}
```

`src/main/java/com/fluxpay/ledger/service/LedgerServiceImpl.java`:
```java
package com.fluxpay.ledger.service;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.domain.LedgerEntry;
import com.fluxpay.ledger.domain.LedgerEntryType;
import com.fluxpay.ledger.persistence.LedgerEntryRepository;
import com.fluxpay.merchants.service.MerchantService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl implements LedgerService {

    static final String CURRENCY = "INR";

    private final LedgerEntryRepository entries;
    private final MerchantService merchantService;
    private final Clock clock;

    public LedgerServiceImpl(LedgerEntryRepository entries, MerchantService merchantService, Clock clock) {
        this.entries = entries;
        this.merchantService = merchantService;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public SaleEntries recordSale(TenantContext tenant, UUID saleId, long gross, String currency, long gatewayFee) {
        long platformFee =
                FeeCalculator.platformFee(gross, merchantService.get(tenant.merchantId()).platformFeeBps());
        Instant now = Instant.now(clock);
        save(tenant, LedgerEntryType.SALE_GROSS, gross, currency, saleId, null, now);
        if (platformFee > 0) {
            save(tenant, LedgerEntryType.PLATFORM_FEE, -platformFee, currency, saleId, null, now);
        }
        if (gatewayFee > 0) {
            save(tenant, LedgerEntryType.GATEWAY_FEE, -gatewayFee, currency, saleId, null, now);
        }
        return new SaleEntries(platformFee, gatewayFee, gross - platformFee - gatewayFee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordRefund(
            TenantContext tenant, UUID saleId, String gatewayRefundId, long amount, String currency) {
        if (entries.existsByTypeAndReference(LedgerEntryType.REFUND, gatewayRefundId)) {
            return false;
        }
        save(tenant, LedgerEntryType.REFUND, -amount, currency, saleId, gatewayRefundId, Instant.now(clock));
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Balance balance(TenantContext tenant) {
        Map<LedgerEntryType, Long> totals = new EnumMap<>(LedgerEntryType.class);
        for (Object[] row : entries.totalsByType(tenant.merchantId(), tenant.mode())) {
            totals.put((LedgerEntryType) row[0], ((Number) row[1]).longValue());
        }
        long available = totals.values().stream().mapToLong(Long::longValue).sum();
        return new Balance(
                totals.getOrDefault(LedgerEntryType.SALE_GROSS, 0L),
                totals.getOrDefault(LedgerEntryType.PLATFORM_FEE, 0L),
                totals.getOrDefault(LedgerEntryType.GATEWAY_FEE, 0L),
                totals.getOrDefault(LedgerEntryType.REFUND, 0L),
                totals.getOrDefault(LedgerEntryType.PAYOUT, 0L),
                available,
                CURRENCY);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<LedgerEntryView> entries(TenantContext tenant, PageQuery query) {
        return CursorPage.from(
                entries.page(tenant.merchantId(), tenant.mode(), query.before(), Limit.of(query.fetchSize())),
                query,
                LedgerEntryView::from,
                view -> PublicId.of(IdPrefix.LEDGER_ENTRY, view.id()));
    }

    private void save(
            TenantContext tenant,
            LedgerEntryType type,
            long amount,
            String currency,
            UUID saleId,
            String reference,
            Instant now) {
        entries.save(new LedgerEntry(
                tenant.merchantId(), tenant.mode(), type, amount, currency, saleId, reference, now));
    }
}
```

- [ ] **Step 6: Implement the dashboard API**

`src/main/java/com/fluxpay/ledger/api/BalanceResponse.java`:
```java
package com.fluxpay.ledger.api;

import com.fluxpay.ledger.service.Balance;

public record BalanceResponse(
        long grossSales,
        long platformFees,
        long gatewayFees,
        long refunds,
        long payouts,
        long available,
        String currency) {

    public static BalanceResponse from(Balance balance) {
        return new BalanceResponse(
                balance.grossSales(),
                balance.platformFees(),
                balance.gatewayFees(),
                balance.refunds(),
                balance.payouts(),
                balance.available(),
                balance.currency());
    }
}
```

`src/main/java/com/fluxpay/ledger/api/LedgerEntryResponse.java`:
```java
package com.fluxpay.ledger.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.ledger.domain.LedgerEntryType;
import com.fluxpay.ledger.service.LedgerEntryView;
import java.time.Instant;

public record LedgerEntryResponse(
        String id,
        Mode mode,
        LedgerEntryType type,
        long amount,
        String currency,
        String saleId,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntryView view) {
        return new LedgerEntryResponse(
                PublicId.of(IdPrefix.LEDGER_ENTRY, view.id()),
                view.mode(),
                view.type(),
                view.amount(),
                view.currency(),
                view.saleId() == null ? null : PublicId.of(IdPrefix.SALE, view.saleId()),
                view.createdAt());
    }
}
```

`src/main/java/com/fluxpay/ledger/api/BalanceController.java`:
```java
package com.fluxpay.ledger.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.ledger.service.LedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class BalanceController {

    private final LedgerService ledgerService;

    public BalanceController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/balance")
    public BalanceResponse balance(TenantContext tenant) {
        return BalanceResponse.from(ledgerService.balance(tenant));
    }

    @GetMapping("/ledger_entries")
    public CursorPage<LedgerEntryResponse> entries(
            TenantContext tenant,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.LEDGER_ENTRY, startingAfter, limit);
        return ledgerService.entries(tenant, query).map(LedgerEntryResponse::from);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.ledger.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 8: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(ledger): append-only ledger with platform and gateway fees and merchant balance

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 6: Payments — Razorpay gateway adapter

**Files:**
- Create: `src/main/java/com/fluxpay/payments/service/{RazorpayProperties,RazorpayHttpConfig,PaymentGateway,GatewayOrder,GatewayPayment,GatewayRefund,WebhookSignatures,RazorpayPayloads,RazorpayGateway,GatewayWebhookEvent,RazorpayWebhookParser}.java`
- Create: `src/test/java/com/fluxpay/support/{FakePaymentGateway,TestWebhooks}.java`
- Modify: `src/test/java/com/fluxpay/support/{TestcontainersConfiguration,AbstractIntegrationTest}.java`, `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, `.env.example`, local `.env`
- Test: `src/test/java/com/fluxpay/payments/{RazorpayGatewayTest,RazorpayWebhookParserTest,RazorpayPropertiesTest}.java`

**Interfaces:**
- Produces:
  - `record GatewayOrder(String id, long amount, String currency)`
  - `record GatewayPayment(String id, String orderId, long amount, String currency, String status, String method, long fee)` (`fee` 0 when absent)
  - `record GatewayRefund(String id, String paymentId, long amount, String currency)`
  - `interface PaymentGateway { String name(); boolean isEnabled(Mode); String publicKeyId(Mode); GatewayOrder createOrder(Mode, long amount, String currency, String receipt, Map<String, String> notes); List<GatewayPayment> fetchOrderPayments(Mode, String orderId); boolean verifyWebhookSignature(Mode, byte[] body, String signature); }` — failures → `FluxpayException(GATEWAY_ERROR, "GATEWAY_UNAVAILABLE")`; mode without credentials → `GATEWAY_ERROR` `GATEWAY_NOT_CONFIGURED`; signature check returns false (never throws) for unconfigured mode or null signature
  - `sealed interface GatewayWebhookEvent` with `record PaymentCaptured(GatewayPayment payment)`, `record RefundProcessed(GatewayRefund refund)`, `record Ignored(String type)`
  - `RazorpayWebhookParser.parse(byte[] body): GatewayWebhookEvent` — malformed → 400 `MALFORMED_WEBHOOK`
  - `WebhookSignatures.hmacSha256Hex(String secret, byte[] body)`, `WebhookSignatures.matches(String expectedHex, String providedHex)`
  - Test support: `FakePaymentGateway` (`@Primary` bean): `WEBHOOK_SECRET = "whsec_fake_test"`, `PUBLIC_KEY_ID = "rzp_test_fake"`, orders `order_fake_<n>`, `addPayment(GatewayPayment)`, `createdOrders()`, `setOrderDelay(Duration)`, `failNextOrder()`, `reset()`; `TestWebhooks.paymentCaptured(paymentId, orderId, amount, fee)`, `TestWebhooks.refundProcessed(refundId, paymentId, amount)`, `TestWebhooks.sign(String body)`
  - `AbstractIntegrationTest` gains `protected FakePaymentGateway paymentGateway` (reset after each test)

- [ ] **Step 1: Configure credentials**

Append to `src/main/resources/application.yml` under `fluxpay:`:
```yaml
  razorpay:
    base-url: https://api.razorpay.com
    connect-timeout: 5s
    read-timeout: 10s
    test:
      key-id: ${RAZORPAY_TEST_KEY_ID}
      key-secret: ${RAZORPAY_TEST_KEY_SECRET}
      webhook-secret: ${RAZORPAY_TEST_WEBHOOK_SECRET}
    live:
      key-id: ${RAZORPAY_LIVE_KEY_ID:}
      key-secret: ${RAZORPAY_LIVE_KEY_SECRET:}
      webhook-secret: ${RAZORPAY_LIVE_WEBHOOK_SECRET:}
```

Append to `src/test/resources/application-test.yml` under `fluxpay:`:
```yaml
  razorpay:
    base-url: https://razorpay.invalid
    connect-timeout: 1s
    read-timeout: 1s
    test:
      key-id: rzp_test_fake
      key-secret: fake_secret
      webhook-secret: whsec_fake_test
```

Append to `.env.example`:
```bash

# Razorpay test mode (required). Dashboard -> Test mode -> API keys / Webhooks.
# Webhook URL: https://<backend>/api/v1/gateway-webhooks/razorpay/test  (events: payment.captured, refund.processed)
RAZORPAY_TEST_KEY_ID=rzp_test_xxx
RAZORPAY_TEST_KEY_SECRET=
RAZORPAY_TEST_WEBHOOK_SECRET=

# Razorpay live mode (optional until go-live). Webhook URL: .../api/v1/gateway-webhooks/razorpay/live
RAZORPAY_LIVE_KEY_ID=
RAZORPAY_LIVE_KEY_SECRET=
RAZORPAY_LIVE_WEBHOOK_SECRET=
```

Rename the variables in the local (gitignored) `.env`: `sed -i '' 's/^RAZORPAY_KEY_ID=/RAZORPAY_TEST_KEY_ID=/; s/^RAZORPAY_KEY_SECRET=/RAZORPAY_TEST_KEY_SECRET=/; s/^RAZORPAY_WEBHOOK_SECRET=/RAZORPAY_TEST_WEBHOOK_SECRET=/' .env` (do not print the file).

- [ ] **Step 2: Write the failing unit tests**

`src/test/java/com/fluxpay/payments/RazorpayPropertiesTest.java`:
```java
package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.RazorpayProperties;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RazorpayPropertiesTest {

    private static final Credentials TEST = new Credentials("rzp_test_k", "secret", "whsec");

    private static RazorpayProperties withLive(Credentials live) {
        return new RazorpayProperties("https://x", Duration.ofSeconds(1), Duration.ofSeconds(1), TEST, live);
    }

    @Test
    void should_treat_missing_or_blank_live_credentials_as_disabled() {
        assertThat(withLive(null).credentials(Mode.LIVE)).isEmpty();
        assertThat(withLive(new Credentials("", "", "")).credentials(Mode.LIVE)).isEmpty();
        assertThat(withLive(null).credentials(Mode.TEST)).contains(TEST);
    }

    @Test
    void should_fail_fast_when_live_credentials_are_partial() {
        assertThatThrownBy(() -> withLive(new Credentials("rzp_live_k", "", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
```

`src/test/java/com/fluxpay/payments/RazorpayGatewayTest.java`:
```java
package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayOrder;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.RazorpayGateway;
import com.fluxpay.payments.service.RazorpayProperties;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import com.fluxpay.payments.service.WebhookSignatures;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RazorpayGatewayTest {

    private static final String BASE = "https://razorpay.test";

    private MockRestServiceServer server;
    private RazorpayGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        RazorpayProperties properties = new RazorpayProperties(
                BASE,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new Credentials("rzp_test_k", "secret", "whsec_test"),
                null);
        gateway = new RazorpayGateway(builder.build(), properties);
    }

    @Test
    void should_create_order_with_basic_auth_and_receipt() {
        String auth = "Basic "
                + Base64.getEncoder().encodeToString("rzp_test_k:secret".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", auth))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("cs_abc"))
                .andExpect(jsonPath("$.notes.checkout_session_id").value("cs_abc"))
                .andRespond(withSuccess(
                        "{\"id\":\"order_1\",\"amount\":4900,\"currency\":\"INR\",\"status\":\"created\"}",
                        MediaType.APPLICATION_JSON));

        GatewayOrder order =
                gateway.createOrder(Mode.TEST, 4900, "INR", "cs_abc", Map.of("checkout_session_id", "cs_abc"));

        assertThat(order).isEqualTo(new GatewayOrder("order_1", 4900, "INR"));
        server.verify();
    }

    @Test
    void should_raise_gateway_unavailable_when_razorpay_fails() {
        server.expect(requestTo(BASE + "/v1/orders")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createOrder(Mode.TEST, 4900, "INR", "cs_abc", Map.of()))
                .isInstanceOf(FluxpayException.class)
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("GATEWAY_UNAVAILABLE");
    }

    @Test
    void should_raise_not_configured_for_live_mode_without_credentials() {
        assertThat(gateway.isEnabled(Mode.LIVE)).isFalse();
        assertThatThrownBy(() -> gateway.createOrder(Mode.LIVE, 4900, "INR", "cs_abc", Map.of()))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("GATEWAY_NOT_CONFIGURED");
    }

    @Test
    void should_parse_order_payments() {
        server.expect(requestTo(BASE + "/v1/orders/order_1/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"entity\":\"collection\",\"count\":1,\"items\":[{\"id\":\"pay_1\",\"order_id\":\"order_1\","
                                + "\"amount\":4900,\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"upi\","
                                + "\"fee\":116}]}",
                        MediaType.APPLICATION_JSON));

        List<GatewayPayment> payments = gateway.fetchOrderPayments(Mode.TEST, "order_1");

        assertThat(payments)
                .containsExactly(new GatewayPayment("pay_1", "order_1", 4900, "INR", "captured", "upi", 116));
    }

    @Test
    void should_verify_webhook_signature_with_mode_secret() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String valid = WebhookSignatures.hmacSha256Hex("whsec_test", body);

        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, valid)).isTrue();
        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, valid.replace('a', 'b'))).isFalse();
        assertThat(gateway.verifyWebhookSignature(Mode.TEST, body, null)).isFalse();
        assertThat(gateway.verifyWebhookSignature(Mode.LIVE, body, valid)).isFalse();
    }
}
```

`src/test/java/com/fluxpay/payments/RazorpayWebhookParserTest.java`:
```java
package com.fluxpay.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import com.fluxpay.payments.service.GatewayWebhookEvent;
import com.fluxpay.payments.service.RazorpayWebhookParser;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RazorpayWebhookParserTest {

    private final RazorpayWebhookParser parser = new RazorpayWebhookParser(new ObjectMapper());

    private GatewayWebhookEvent parse(String json) {
        return parser.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void should_parse_payment_captured() {
        GatewayWebhookEvent event = parse("{\"entity\":\"event\",\"event\":\"payment.captured\",\"payload\":"
                + "{\"payment\":{\"entity\":{\"id\":\"pay_1\",\"order_id\":\"order_1\",\"amount\":4900,"
                + "\"currency\":\"INR\",\"status\":\"captured\",\"method\":\"card\",\"fee\":116,\"tax\":18}}}}");

        assertThat(event)
                .isEqualTo(new GatewayWebhookEvent.PaymentCaptured(
                        new GatewayPayment("pay_1", "order_1", 4900, "INR", "captured", "card", 116)));
    }

    @Test
    void should_parse_refund_processed() {
        GatewayWebhookEvent event = parse("{\"event\":\"refund.processed\",\"payload\":{\"refund\":{\"entity\":"
                + "{\"id\":\"rfnd_1\",\"payment_id\":\"pay_1\",\"amount\":2000,\"currency\":\"INR\"}}}}");

        assertThat(event)
                .isEqualTo(new GatewayWebhookEvent.RefundProcessed(new GatewayRefund("rfnd_1", "pay_1", 2000, "INR")));
    }

    @Test
    void should_ignore_other_events() {
        assertThat(parse("{\"event\":\"order.paid\",\"payload\":{}}"))
                .isEqualTo(new GatewayWebhookEvent.Ignored("order.paid"));
    }

    @Test
    void should_reject_malformed_json_or_missing_entity() {
        assertThatThrownBy(() -> parse("not json"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("MALFORMED_WEBHOOK");
        assertThatThrownBy(() -> parse("{\"event\":\"payment.captured\",\"payload\":{}}"))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("MALFORMED_WEBHOOK");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.payments.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement value types, properties and signatures**

`src/main/java/com/fluxpay/payments/service/GatewayOrder.java`:
```java
package com.fluxpay.payments.service;

public record GatewayOrder(String id, long amount, String currency) {}
```

`src/main/java/com/fluxpay/payments/service/GatewayPayment.java`:
```java
package com.fluxpay.payments.service;

/** A payment as the gateway reports it. {@code fee} includes the gateway's tax; 0 when not reported. */
public record GatewayPayment(
        String id, String orderId, long amount, String currency, String status, String method, long fee) {

    public boolean isCaptured() {
        return "captured".equals(status);
    }
}
```

`src/main/java/com/fluxpay/payments/service/GatewayRefund.java`:
```java
package com.fluxpay.payments.service;

public record GatewayRefund(String id, String paymentId, long amount, String currency) {}
```

`src/main/java/com/fluxpay/payments/service/RazorpayProperties.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Test credentials are required at startup; live credentials are optional but must be complete if present. */
@Validated
@ConfigurationProperties("fluxpay.razorpay")
public record RazorpayProperties(
        @NotBlank String baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Valid @NotNull Credentials test,
        Credentials live) {

    public RazorpayProperties {
        if (live != null && !live.isBlank() && !live.isComplete()) {
            throw new IllegalArgumentException(
                    "fluxpay.razorpay.live is partially configured: set key-id, key-secret and webhook-secret");
        }
    }

    public Optional<Credentials> credentials(Mode mode) {
        Credentials credentials = mode == Mode.TEST ? test : live;
        return credentials != null && credentials.isComplete() ? Optional.of(credentials) : Optional.empty();
    }

    public record Credentials(@NotBlank String keyId, @NotBlank String keySecret, @NotBlank String webhookSecret) {

        boolean isBlank() {
            return blank(keyId) && blank(keySecret) && blank(webhookSecret);
        }

        boolean isComplete() {
            return !blank(keyId) && !blank(keySecret) && !blank(webhookSecret);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        @Override
        public String toString() {
            return "Credentials[keyId=" + keyId + ", secrets=redacted]";
        }
    }
}
```

`src/main/java/com/fluxpay/payments/service/RazorpayHttpConfig.java`:
```java
package com.fluxpay.payments.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RazorpayHttpConfig {

    @Bean
    public RestClient razorpayRestClient(RazorpayProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}
```

`src/main/java/com/fluxpay/payments/service/WebhookSignatures.java`:
```java
package com.fluxpay.payments.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WebhookSignatures {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private WebhookSignatures() {}

    public static String hmacSha256Hex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /** Constant-time comparison of two hex signatures. */
    public static boolean matches(String expectedHex, String providedHex) {
        if (providedHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                providedHex.trim().getBytes(StandardCharsets.US_ASCII));
    }
}
```

`src/main/java/com/fluxpay/payments/service/RazorpayPayloads.java`:
```java
package com.fluxpay.payments.service;

import java.util.Map;

/** Maps Razorpay JSON entities (as maps) to gateway value types. */
final class RazorpayPayloads {

    private RazorpayPayloads() {}

    static GatewayPayment toPayment(Map<?, ?> entity) {
        return new GatewayPayment(
                text(entity, "id"),
                text(entity, "order_id"),
                number(entity, "amount"),
                text(entity, "currency"),
                text(entity, "status"),
                text(entity, "method"),
                entity.get("fee") == null ? 0 : number(entity, "fee"));
    }

    static GatewayRefund toRefund(Map<?, ?> entity) {
        return new GatewayRefund(
                text(entity, "id"), text(entity, "payment_id"), number(entity, "amount"), text(entity, "currency"));
    }

    private static String text(Map<?, ?> entity, String key) {
        Object value = entity.get(key);
        return value == null ? null : value.toString();
    }

    private static long number(Map<?, ?> entity, String key) {
        Object value = entity.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Razorpay field " + key + " is not a number");
        }
        return number.longValue();
    }
}
```

`src/main/java/com/fluxpay/payments/service/PaymentGateway.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import java.util.List;
import java.util.Map;

/** A payment provider. Razorpay today; Razorpay Route or others later (ADR-002). */
public interface PaymentGateway {

    String name();

    boolean isEnabled(Mode mode);

    /** The key the browser checkout script needs. GATEWAY_NOT_CONFIGURED when the mode has no credentials. */
    String publicKeyId(Mode mode);

    GatewayOrder createOrder(Mode mode, long amount, String currency, String receipt, Map<String, String> notes);

    List<GatewayPayment> fetchOrderPayments(Mode mode, String orderId);

    /** False for a wrong, missing or unverifiable signature. Never throws. */
    boolean verifyWebhookSignature(Mode mode, byte[] body, String signature);
}
```

- [ ] **Step 5: Implement the Razorpay adapter and webhook parser**

`src/main/java/com/fluxpay/payments/service/RazorpayGateway.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.error.ErrorType;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.RazorpayProperties.Credentials;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RazorpayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGateway.class);

    private final RestClient client;
    private final RazorpayProperties properties;

    public RazorpayGateway(@Qualifier("razorpayRestClient") RestClient client, RazorpayProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public boolean isEnabled(Mode mode) {
        return properties.credentials(mode).isPresent();
    }

    @Override
    public String publicKeyId(Mode mode) {
        return credentials(mode).keyId();
    }

    @Override
    public GatewayOrder createOrder(
            Mode mode, long amount, String currency, String receipt, Map<String, String> notes) {
        Credentials credentials = credentials(mode);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        try {
            Map<?, ?> response = client.post()
                    .uri("/v1/orders")
                    .headers(headers -> authenticate(headers, credentials))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return new GatewayOrder(
                    response.get("id").toString(),
                    ((Number) response.get("amount")).longValue(),
                    response.get("currency").toString());
        } catch (RestClientException | NullPointerException | ClassCastException e) {
            log.error("Razorpay order creation failed for receipt {}", receipt, e);
            throw unavailable();
        }
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(Mode mode, String orderId) {
        Credentials credentials = credentials(mode);
        try {
            Map<?, ?> response = client.get()
                    .uri("/v1/orders/{orderId}/payments", orderId)
                    .headers(headers -> authenticate(headers, credentials))
                    .retrieve()
                    .body(Map.class);
            List<?> items = (List<?>) response.get("items");
            return items.stream().map(item -> RazorpayPayloads.toPayment((Map<?, ?>) item)).toList();
        } catch (RestClientException | NullPointerException | ClassCastException | IllegalArgumentException e) {
            log.error("Razorpay payment lookup failed for order {}", orderId, e);
            throw unavailable();
        }
    }

    @Override
    public boolean verifyWebhookSignature(Mode mode, byte[] body, String signature) {
        return properties
                .credentials(mode)
                .map(credentials -> WebhookSignatures.matches(
                        WebhookSignatures.hmacSha256Hex(credentials.webhookSecret(), body), signature))
                .orElse(false);
    }

    private Credentials credentials(Mode mode) {
        return properties
                .credentials(mode)
                .orElseThrow(() -> new FluxpayException(
                        ErrorType.GATEWAY_ERROR,
                        "GATEWAY_NOT_CONFIGURED",
                        "Payments are not configured for " + mode.value() + " mode"));
    }

    private static void authenticate(HttpHeaders headers, Credentials credentials) {
        headers.setBasicAuth(credentials.keyId(), credentials.keySecret());
    }

    private static FluxpayException unavailable() {
        return new FluxpayException(
                ErrorType.GATEWAY_ERROR, "GATEWAY_UNAVAILABLE", "The payment gateway is unavailable, retry shortly");
    }
}
```

`src/main/java/com/fluxpay/payments/service/GatewayWebhookEvent.java`:
```java
package com.fluxpay.payments.service;

public sealed interface GatewayWebhookEvent {

    record PaymentCaptured(GatewayPayment payment) implements GatewayWebhookEvent {}

    record RefundProcessed(GatewayRefund refund) implements GatewayWebhookEvent {}

    record Ignored(String type) implements GatewayWebhookEvent {}
}
```

`src/main/java/com/fluxpay/payments/service/RazorpayWebhookParser.java`:
```java
package com.fluxpay.payments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RazorpayWebhookParser {

    private final ObjectMapper objectMapper;

    public RazorpayWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GatewayWebhookEvent parse(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException e) {
            throw malformed();
        }
        if (root == null || !root.isObject()) {
            throw malformed();
        }
        String type = root.path("event").asText("");
        try {
            return switch (type) {
                case "payment.captured" ->
                    new GatewayWebhookEvent.PaymentCaptured(RazorpayPayloads.toPayment(entity(root, "payment")));
                case "refund.processed" ->
                    new GatewayWebhookEvent.RefundProcessed(RazorpayPayloads.toRefund(entity(root, "refund")));
                default -> new GatewayWebhookEvent.Ignored(type);
            };
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    private Map<?, ?> entity(JsonNode root, String name) {
        JsonNode entity = root.path("payload").path(name).path("entity");
        if (!entity.isObject()) {
            throw malformed();
        }
        return objectMapper.convertValue(entity, Map.class);
    }

    private static FluxpayException malformed() {
        return FluxpayException.badRequest("MALFORMED_WEBHOOK", "Webhook payload is not a valid Razorpay event");
    }
}
```

- [ ] **Step 6: Add the fake gateway for integration tests**

`src/test/java/com/fluxpay/support/FakePaymentGateway.java`:
```java
package com.fluxpay.support;

import com.fluxpay.common.error.ErrorType;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayOrder;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.PaymentGateway;
import com.fluxpay.payments.service.WebhookSignatures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** In-memory stand-in for Razorpay. Signs webhooks with the same HMAC scheme as the real gateway. */
public class FakePaymentGateway implements PaymentGateway {

    public static final String WEBHOOK_SECRET = "whsec_fake_test";
    public static final String PUBLIC_KEY_ID = "rzp_test_fake";

    private final AtomicInteger orderCounter = new AtomicInteger();
    private final List<GatewayOrder> createdOrders = new CopyOnWriteArrayList<>();
    private final Map<String, List<GatewayPayment>> paymentsByOrder = new ConcurrentHashMap<>();
    private final AtomicBoolean failNextOrder = new AtomicBoolean();
    private volatile Duration orderDelay = Duration.ZERO;

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public boolean isEnabled(Mode mode) {
        return mode == Mode.TEST;
    }

    @Override
    public String publicKeyId(Mode mode) {
        requireTest(mode);
        return PUBLIC_KEY_ID;
    }

    @Override
    public GatewayOrder createOrder(
            Mode mode, long amount, String currency, String receipt, Map<String, String> notes) {
        requireTest(mode);
        if (failNextOrder.getAndSet(false)) {
            throw new FluxpayException(ErrorType.GATEWAY_ERROR, "GATEWAY_UNAVAILABLE", "fake gateway failure");
        }
        sleep(orderDelay);
        GatewayOrder order = new GatewayOrder("order_fake_" + orderCounter.incrementAndGet(), amount, currency);
        createdOrders.add(order);
        return order;
    }

    @Override
    public List<GatewayPayment> fetchOrderPayments(Mode mode, String orderId) {
        return List.copyOf(paymentsByOrder.getOrDefault(orderId, List.of()));
    }

    @Override
    public boolean verifyWebhookSignature(Mode mode, byte[] body, String signature) {
        return mode == Mode.TEST
                && WebhookSignatures.matches(WebhookSignatures.hmacSha256Hex(WEBHOOK_SECRET, body), signature);
    }

    public void addPayment(GatewayPayment payment) {
        paymentsByOrder
                .computeIfAbsent(payment.orderId(), id -> new CopyOnWriteArrayList<>())
                .add(payment);
    }

    public List<GatewayOrder> createdOrders() {
        return List.copyOf(createdOrders);
    }

    public void setOrderDelay(Duration delay) {
        this.orderDelay = delay;
    }

    public void failNextOrder() {
        failNextOrder.set(true);
    }

    public void reset() {
        createdOrders.clear();
        paymentsByOrder.clear();
        failNextOrder.set(false);
        orderDelay = Duration.ZERO;
    }

    private static void requireTest(Mode mode) {
        if (mode != Mode.TEST) {
            throw new FluxpayException(ErrorType.GATEWAY_ERROR, "GATEWAY_NOT_CONFIGURED", "live not configured");
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

`src/test/java/com/fluxpay/support/TestWebhooks.java`:
```java
package com.fluxpay.support;

import com.fluxpay.payments.service.WebhookSignatures;
import java.nio.charset.StandardCharsets;

/** Razorpay-shaped webhook bodies signed with {@link FakePaymentGateway#WEBHOOK_SECRET}. */
public final class TestWebhooks {

    private TestWebhooks() {}

    public static String paymentCaptured(String paymentId, String orderId, long amount, long fee) {
        return """
                {"entity":"event","event":"payment.captured","contains":["payment"],"payload":{"payment":{"entity":\
                {"id":"%s","entity":"payment","order_id":"%s","amount":%d,"currency":"INR","status":"captured",\
                "method":"upi","fee":%d,"tax":0}}},"created_at":1759363200}"""
                .formatted(paymentId, orderId, amount, fee);
    }

    public static String refundProcessed(String refundId, String paymentId, long amount) {
        return """
                {"entity":"event","event":"refund.processed","contains":["refund","payment"],"payload":{"refund":\
                {"entity":{"id":"%s","entity":"refund","payment_id":"%s","amount":%d,"currency":"INR",\
                "status":"processed"}}},"created_at":1759363200}"""
                .formatted(refundId, paymentId, amount);
    }

    public static String sign(String body) {
        return WebhookSignatures.hmacSha256Hex(
                FakePaymentGateway.WEBHOOK_SECRET, body.getBytes(StandardCharsets.UTF_8));
    }
}
```

In `src/test/java/com/fluxpay/support/TestcontainersConfiguration.java` add this bean (import `org.springframework.context.annotation.Primary`):
```java
    @Bean
    @Primary
    FakePaymentGateway fakePaymentGateway() {
        return new FakePaymentGateway();
    }
```

In `src/test/java/com/fluxpay/support/AbstractIntegrationTest.java` add a field and reset it in the existing `@AfterEach`:
```java
    @Autowired
    protected FakePaymentGateway paymentGateway;
```
and change `cleanDatabase()` to:
```java
    @AfterEach
    void cleanDatabase() {
        databaseCleaner.truncateAll();
        paymentGateway.reset();
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.payments.*'` then `./gradlew test`
Expected: PASS (the context starts with test credentials from `application-test.yml`; the `@Primary` fake replaces `RazorpayGateway` wherever `PaymentGateway` is injected).

- [ ] **Step 8: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(payments): razorpay gateway adapter with per-mode credentials and signed webhook parsing

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 7: Idempotency keys

**Files:**
- Create: `src/main/resources/db/migration/V8__idempotency_keys.sql`, `src/main/resources/db/rollback/V8__down.sql`
- Create: `src/main/java/com/fluxpay/common/idempotency/IdempotencyService.java`
- Test: `src/test/java/com/fluxpay/common/idempotency/IdempotencyServiceIntegrationTest.java`

**Interfaces:**
- Produces:
  - `IdempotencyService.execute(TenantContext tenant, String key, Object request, Class<T> responseType, Supplier<T> action): T` — null key runs `action` unconditionally; blank or > 255 chars → 400 `INVALID_IDEMPOTENCY_KEY`; same key + same request → stored response, `action` not run; same key + different request → 409 `IDEMPOTENCY_KEY_REUSED`; same key while the first is still running → 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`; `action` throwing → key released. **Must be called outside any transaction.**
  - `IdempotencyService.deleteOlderThan(Instant cutoff): int`

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V8__idempotency_keys.sql`:
```sql
CREATE TABLE idempotency_keys (
    merchant_id     UUID         NOT NULL REFERENCES merchants (id),
    mode            VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (merchant_id, mode, idempotency_key)
);

CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);
```

`src/main/resources/db/rollback/V8__down.sql`:
```sql
DROP TABLE IF EXISTS idempotency_keys;
DELETE FROM flyway_schema_history WHERE version = '8';
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/fluxpay/common/idempotency/IdempotencyServiceIntegrationTest.java`:
```java
package com.fluxpay.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class IdempotencyServiceIntegrationTest extends AbstractIntegrationTest {

    record Request(String productId, long amount) {}

    record Response(String id, long amount) {}

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TenantContext tenant;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws Exception {
        String merchantId = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com").merchantId();
        tenant = new TenantContext(PublicId.parse(IdPrefix.MERCHANT, merchantId).orElseThrow(), Mode.TEST);
    }

    private Response run(String key, Request request) {
        return idempotency.execute(
                tenant, key, request, Response.class, () -> new Response("cs_" + calls.incrementAndGet(), 4900));
    }

    @Test
    void should_return_stored_response_without_rerunning_when_key_and_request_repeat() {
        Response first = run("key-1", new Request("prod_1", 4900));
        Response second = run("key-1", new Request("prod_1", 4900));

        assertThat(second).isEqualTo(first);
        assertThat(calls).hasValue(1);
    }

    @Test
    void should_reject_same_key_with_different_request() {
        run("key-1", new Request("prod_1", 4900));

        assertThatThrownBy(() -> run("key-1", new Request("prod_2", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(calls).hasValue(1);
    }

    @Test
    void should_reject_while_first_request_is_in_progress() {
        Request request = new Request("prod_1", 4900);
        jdbcTemplate.update(
                "INSERT INTO idempotency_keys (merchant_id, mode, idempotency_key, request_hash, created_at)"
                        + " VALUES (?, 'TEST', 'key-1', ?, now())",
                tenant.merchantId(),
                IdempotencyService.sha256Hex("{\"product_id\":\"prod_1\",\"amount\":4900}"));

        assertThatThrownBy(() -> run("key-1", request))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");
    }

    @Test
    void should_release_key_when_action_fails() {
        Request request = new Request("prod_1", 4900);
        assertThatThrownBy(() -> idempotency.execute(tenant, "key-1", request, Response.class, () -> {
                    throw FluxpayException.conflict("PRODUCT_INACTIVE", "archived");
                }))
                .isInstanceOf(FluxpayException.class);

        assertThat(run("key-1", request).id()).isEqualTo("cs_1");
    }

    @Test
    void should_run_every_time_without_key_and_reject_invalid_keys() {
        run(null, new Request("prod_1", 4900));
        run(null, new Request("prod_1", 4900));

        assertThat(calls).hasValue(2);
        assertThatThrownBy(() -> run("x".repeat(256), new Request("prod_1", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
        assertThatThrownBy(() -> run(" ", new Request("prod_1", 4900)))
                .extracting(e -> ((FluxpayException) e).code())
                .isEqualTo("INVALID_IDEMPOTENCY_KEY");
    }

    @Test
    void should_delete_keys_older_than_cutoff() {
        run("old", new Request("prod_1", 4900));
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET created_at = ?", Timestamp.from(Instant.parse("2020-01-01T00:00:00Z")));
        run("new", new Request("prod_1", 4900));

        int deleted = idempotency.deleteOlderThan(Instant.parse("2021-01-01T00:00:00Z"));

        assertThat(deleted).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.common.idempotency.*'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement**

`src/main/java/com/fluxpay/common/idempotency/IdempotencyService.java`:
```java
package com.fluxpay.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Makes POST requests safe to retry (spec §9). The key row is claimed before the action runs, so a concurrent
 * duplicate gets IDEMPOTENCY_REQUEST_IN_PROGRESS instead of creating a second resource. Call outside any
 * transaction: each statement here auto-commits so other requests see the claim immediately.
 */
@Service
public class IdempotencyService {

    static final int MAX_KEY_LENGTH = 255;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> T execute(
            TenantContext tenant, String key, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null) {
            return action.get();
        }
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw FluxpayException.badRequest(
                    "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        String requestHash = sha256Hex(write(request));
        int claimed = jdbc.update(
                "INSERT INTO idempotency_keys (merchant_id, mode, idempotency_key, request_hash, created_at)"
                        + " VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                tenant.merchantId(),
                tenant.mode().name(),
                key,
                requestHash,
                Timestamp.from(Instant.now(clock)));
        if (claimed == 0) {
            return replay(tenant, key, requestHash, responseType);
        }
        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            jdbc.update(
                    "DELETE FROM idempotency_keys WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                    tenant.merchantId(),
                    tenant.mode().name(),
                    key);
            throw e;
        }
        jdbc.update(
                "UPDATE idempotency_keys SET response_body = ?::jsonb"
                        + " WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                write(response),
                tenant.merchantId(),
                tenant.mode().name(),
                key);
        return response;
    }

    public int deleteOlderThan(Instant cutoff) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < ?", Timestamp.from(cutoff));
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private <T> T replay(TenantContext tenant, String key, String requestHash, Class<T> responseType) {
        Map<String, Object> row;
        try {
            row = jdbc.queryForMap(
                    "SELECT request_hash, response_body::text AS response_body FROM idempotency_keys"
                            + " WHERE merchant_id = ? AND mode = ? AND idempotency_key = ?",
                    tenant.merchantId(),
                    tenant.mode().name(),
                    key);
        } catch (EmptyResultDataAccessException e) {
            throw inProgress();
        }
        if (!requestHash.equals(row.get("request_hash"))) {
            throw FluxpayException.conflict(
                    "IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used with a different request");
        }
        Object body = row.get("response_body");
        if (body == null) {
            throw inProgress();
        }
        try {
            return objectMapper.readValue(body.toString(), responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response cannot be read", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise for idempotency", e);
        }
    }

    private static FluxpayException inProgress() {
        return FluxpayException.conflict(
                "IDEMPOTENCY_REQUEST_IN_PROGRESS", "A request with this Idempotency-Key is still being processed");
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.common.idempotency.*'` then `./gradlew test`
Expected: PASS. The in-progress test hashes the exact JSON Spring's `ObjectMapper` produces for `Request` (snake_case, component order) — if it fails on the hash, print `objectMapper.writeValueAsString(request)` and align the literal.

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(common): idempotency keys for safe post retries

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 8: Checkout sessions — merchant API and hosted-checkout public API

**Files:**
- Create: `src/main/resources/db/migration/V9__checkout_sessions.sql`, `src/main/resources/db/rollback/V9__down.sql`
- Create: `src/main/java/com/fluxpay/checkout/domain/{CheckoutSession,CheckoutStatus}.java`, `checkout/persistence/CheckoutSessionRepository.java`
- Create: `src/main/java/com/fluxpay/checkout/service/{CheckoutProperties,NewCheckoutSession,CheckoutSessionView,PublicCheckoutView,PaymentInstructions,CheckoutService,CheckoutServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/checkout/api/{CheckoutSessionApiController,CreateCheckoutSessionRequest,CheckoutSessionResponse,PublicCheckoutController,PublicCheckoutResponse,PaymentInstructionsResponse,LinkCheckoutRequest,LinkCheckoutResponse}.java`
- Modify: `src/main/resources/application.yml`, `src/test/java/com/fluxpay/support/TestMerchants.java`
- Test: `src/test/java/com/fluxpay/checkout/{CheckoutSessionIntegrationTest,PublicCheckoutIntegrationTest}.java`

**Interfaces:**
- Consumes: `ProductService.get`, `PaymentLinkService.findActiveBySlug`, `MerchantService.get/isActive`, `PaymentGateway`, `IdempotencyService`, `RedirectUrlPolicy`, `Metadata`, `FluxpayProperties`.
- Produces:
  - `enum CheckoutStatus { OPEN, COMPLETED, EXPIRED }` (JSON lowercase)
  - `record CheckoutSessionView(UUID id, UUID merchantId, Mode mode, UUID productId, UUID paymentLinkId, long amount, String currency, String customerRef, String successUrl, String cancelUrl, Map<String, String> metadata, CheckoutStatus status, String gatewayOrderId, String url, Instant expiresAt, Instant completedAt, Instant createdAt)` (`url` = `{frontendBaseUrl}/pay/{cs_id}`)
  - `interface CheckoutService`:
    - `CheckoutSessionView create(TenantContext, NewCheckoutSession)` — 404 `PRODUCT_NOT_FOUND`, 409 `PRODUCT_INACTIVE`, 422 URL codes
    - `CheckoutSessionView get(TenantContext, UUID)` — 404 `CHECKOUT_SESSION_NOT_FOUND`
    - `CheckoutSessionView createFromLink(String slug, String customerRef)` — 404 `PAYMENT_LINK_NOT_FOUND` (missing, inactive, archived product or suspended merchant)
    - `PublicCheckoutView getPublic(UUID)` — status shown as `expired` once past `expires_at`
    - `PaymentInstructions startPayment(UUID)` — 409 `SESSION_COMPLETED` / `SESSION_EXPIRED` / `MERCHANT_UNAVAILABLE`; creates the gateway order outside any transaction and attaches it with a conditional update so concurrent calls converge on one order
    - `Optional<CheckoutSessionView> lockByGatewayOrderId(String)` (MANDATORY, `SELECT … FOR UPDATE`)
    - `void markCompleted(UUID, Instant)` (MANDATORY)
    - `List<CheckoutSessionView> expireDue(Instant now, int limit)` (MANDATORY)
    - `List<CheckoutSessionView> findReconcilable(Instant createdAfter, Instant createdBefore, int limit)` — sessions with an order, status `OPEN` or `EXPIRED`
  - Routes: API key `POST /api/v1/checkout_sessions` (header `Idempotency-Key`), `GET /api/v1/checkout_sessions/{id}`; public `GET /api/v1/public/checkout_sessions/{id}`, `POST /api/v1/public/checkout_sessions/{id}/pay`, `POST /api/v1/public/payment_links/{slug}/checkout_sessions` (body `{ "ref"?: string }`)
  - `TestMerchants.createCheckoutSession(MockMvc, String apiKey, String productId, String customerRef): String` (returns `cs_…`), `TestMerchants.pay(MockMvc, String sessionId): String` (returns the gateway order ID)

- [ ] **Step 1: Write the migration and config**

`src/main/resources/db/migration/V9__checkout_sessions.sql`:
```sql
CREATE TABLE checkout_sessions (
    id               UUID PRIMARY KEY,
    merchant_id      UUID          NOT NULL REFERENCES merchants (id),
    mode             VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id       UUID          NOT NULL REFERENCES products (id),
    payment_link_id  UUID          REFERENCES payment_links (id),
    amount           BIGINT        NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    customer_ref     VARCHAR(255),
    success_url      VARCHAR(2048),
    cancel_url       VARCHAR(2048),
    metadata         JSONB         NOT NULL DEFAULT '{}',
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'EXPIRED')),
    gateway_order_id VARCHAR(100)  UNIQUE,
    expires_at       TIMESTAMPTZ   NOT NULL,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX checkout_sessions_merchant_mode_idx ON checkout_sessions (merchant_id, mode, id DESC);
CREATE INDEX checkout_sessions_status_expires_idx ON checkout_sessions (status, expires_at);
CREATE INDEX checkout_sessions_reconcile_idx ON checkout_sessions (created_at) WHERE gateway_order_id IS NOT NULL;
```

`src/main/resources/db/rollback/V9__down.sql`:
```sql
DROP TABLE IF EXISTS checkout_sessions;
DELETE FROM flyway_schema_history WHERE version = '9';
```

Append to `src/main/resources/application.yml` under `fluxpay:`:
```yaml
  checkout:
    session-ttl: 30m
```

- [ ] **Step 2: Add test fixtures**

Add to `src/test/java/com/fluxpay/support/TestMerchants.java`:
```java
    public static String createCheckoutSession(MockMvc mockMvc, String apiKey, String productId, String customerRef)
            throws Exception {
        String body = """
                {"product_id":"%s","customer_ref":"%s","success_url":"https://jextter.com/paid",\
                "cancel_url":"https://jextter.com/store","metadata":{"order":"42"}}"""
                .formatted(productId, customerRef);
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/checkout_sessions")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    public static String pay(MockMvc mockMvc, String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.order_id");
    }
```

- [ ] **Step 3: Write the failing tests**

`src/test/java/com/fluxpay/checkout/CheckoutSessionIntegrationTest.java`:
```java
package com.fluxpay.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class CheckoutSessionIntegrationTest extends AbstractIntegrationTest {

    private SignedIn owner;
    private String productId;
    private String testKey;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        testKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
    }

    private ResultActions create(String key, String idempotencyKey, String body) throws Exception {
        var request = post("/api/v1/checkout_sessions")
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private String body(String product, String successUrl) {
        return """
                {"product_id":"%s","customer_ref":"u_123","success_url":"%s","cancel_url":"https://jextter.com/store"}"""
                .formatted(product, successUrl);
    }

    @Test
    void should_create_open_session_with_price_snapshot_and_hosted_url() throws Exception {
        create(testKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.startsWith("cs_")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://localhost:3000/pay/cs_")))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.customer_ref").value("u_123"))
                .andExpect(jsonPath("$.mode").value("test"));
    }

    @Test
    void should_keep_snapshot_price_when_product_is_repriced() throws Exception {
        String id = TestMerchants.createCheckoutSession(mockMvc, testKey, productId, "u_123");
        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9900}"));

        mockMvc.perform(get("/api/v1/checkout_sessions/" + id).header("Authorization", "Bearer " + testKey))
                .andExpect(jsonPath("$.amount").value(4900));
    }

    @Test
    void should_reject_archived_unknown_and_other_mode_products() throws Exception {
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);
        create(liveKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        create(testKey, null, body("prod_nope", "https://jextter.com/paid")).andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"));
        create(testKey, null, body(productId, "https://jextter.com/paid"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_INACTIVE"));
    }

    @Test
    void should_enforce_redirect_policy_per_mode() throws Exception {
        create(testKey, null, body(productId, "http://localhost:3000/paid")).andExpect(status().isCreated());
        create(testKey, null, body(productId, "http://jextter.com/paid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.details[0].code").value("INSECURE_URL"));
        create(testKey, null, body(productId, "javascript:alert(1)")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void should_replay_same_idempotency_key_and_reject_different_body() throws Exception {
        String first = create(testKey, "order-42", body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String replay = create(testKey, "order-42", body(productId, "https://jextter.com/paid"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat((String) JsonPath.read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        create(testKey, "order-42", body(productId, "https://jextter.com/other"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void should_return_404_for_session_of_other_mode() throws Exception {
        String id = TestMerchants.createCheckoutSession(mockMvc, testKey, productId, "u_123");
        String liveKey = TestMerchants.createApiKey(mockMvc, owner, Mode.LIVE);

        mockMvc.perform(get("/api/v1/checkout_sessions/" + id).header("Authorization", "Bearer " + liveKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_NOT_FOUND"));
    }
}
```

`src/test/java/com/fluxpay/checkout/PublicCheckoutIntegrationTest.java`:
```java
package com.fluxpay.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.FakePaymentGateway;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class PublicCheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;
    private String productId;
    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String key = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, key, productId, "u_123");
    }

    @Test
    void should_show_branding_and_price_without_success_url_while_open() throws Exception {
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.product.name").value("Pro Pack"))
                .andExpect(jsonPath("$.merchant.name").value("Jextter"))
                .andExpect(jsonPath("$.cancel_url").value("https://jextter.com/store"))
                .andExpect(jsonPath("$.success_url").isEmpty());
    }

    @Test
    void should_return_404_for_unknown_session() throws Exception {
        mockMvc.perform(get("/api/v1/public/checkout_sessions/cs_nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_NOT_FOUND"));
    }

    @Test
    void should_create_one_gateway_order_and_reuse_it() throws Exception {
        String first = TestMerchants.pay(mockMvc, sessionId);
        String second = TestMerchants.pay(mockMvc, sessionId);

        assertThat(second).isEqualTo(first);
        assertThat(paymentGateway.createdOrders()).hasSize(1);
        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(jsonPath("$.key_id").value(FakePaymentGateway.PUBLIC_KEY_ID))
                .andExpect(jsonPath("$.amount").value(4900))
                .andExpect(jsonPath("$.merchant_name").value("Jextter"));
    }

    @Test
    void should_converge_on_one_attached_order_when_pay_is_double_clicked() throws Exception {
        paymentGateway.setOrderDelay(Duration.ofMillis(300));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<String> pay = () -> TestMerchants.pay(mockMvc, sessionId);

        List<Future<String>> results = pool.invokeAll(List.of(pay, pay));
        pool.shutdown();

        String attached =
                jdbcTemplate.queryForObject("SELECT gateway_order_id FROM checkout_sessions", String.class);
        assertThat(results.get(0).get()).isEqualTo(attached);
        assertThat(results.get(1).get()).isEqualTo(attached);
    }

    @Test
    void should_refuse_payment_for_expired_session_and_show_expired() throws Exception {
        jdbcTemplate.update("UPDATE checkout_sessions SET expires_at = now() - interval '1 minute'");

        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(jsonPath("$.status").value("expired"));
    }

    @Test
    void should_return_502_when_gateway_fails() throws Exception {
        paymentGateway.failNextOrder();

        mockMvc.perform(post("/api/v1/public/checkout_sessions/" + sessionId + "/pay"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("GATEWAY_UNAVAILABLE"));
    }

    @Test
    void should_create_session_from_active_payment_link_with_ref() throws Exception {
        String link = mockMvc.perform(post("/api/v1/dashboard/payment_links")
                        .with(csrf())
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\"}".formatted(productId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String slug = JsonPath.read(link, "$.slug");

        String created = mockMvc.perform(post("/api/v1/public/payment_links/" + slug + "/checkout_sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ref\":\"u_777\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("http://localhost:3000/pay/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String linkedSession = JsonPath.read(created, "$.id");
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        mockMvc.perform(get("/api/v1/checkout_sessions/" + linkedSession).header("Authorization", "Bearer " + apiKey))
                .andExpect(jsonPath("$.customer_ref").value("u_777"))
                .andExpect(jsonPath("$.payment_link_id").value(JsonPath.<String>read(link, "$.id")));

        mockMvc.perform(patch("/api/v1/dashboard/payment_links/" + JsonPath.read(link, "$.id"))
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"));
        mockMvc.perform(post("/api/v1/public/payment_links/" + slug + "/checkout_sessions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_LINK_NOT_FOUND"));
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests 'com.fluxpay.checkout.*'`
Expected: FAIL — `TestMerchants.createCheckoutSession` gets 401/404 (no route); all tests fail.

- [ ] **Step 5: Implement domain and persistence**

`src/main/java/com/fluxpay/checkout/domain/CheckoutStatus.java`:
```java
package com.fluxpay.checkout.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CheckoutStatus {
    OPEN,
    COMPLETED,
    EXPIRED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/checkout/domain/CheckoutSession.java`:
```java
package com.fluxpay.checkout.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "payment_link_id")
    private UUID paymentLinkId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    @Column(name = "customer_ref")
    private String customerRef;

    @Column(name = "success_url")
    private String successUrl;

    @Column(name = "cancel_url")
    private String cancelUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutStatus status;

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CheckoutSession() {}

    public CheckoutSession(
            UUID merchantId,
            Mode mode,
            UUID productId,
            UUID paymentLinkId,
            long amount,
            String currency,
            String customerRef,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata,
            Instant expiresAt,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.productId = productId;
        this.paymentLinkId = paymentLinkId;
        this.amount = amount;
        this.currency = currency;
        this.customerRef = customerRef;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
        this.metadata = metadata;
        this.status = CheckoutStatus.OPEN;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** Allowed from OPEN or EXPIRED: a payment captured after expiry still took the customer's money. */
    public void complete(Instant now) {
        this.status = CheckoutStatus.COMPLETED;
        this.completedAt = now;
    }

    public void expire() {
        if (status == CheckoutStatus.OPEN) {
            this.status = CheckoutStatus.EXPIRED;
        }
    }

    public boolean isPayableAt(Instant now) {
        return status == CheckoutStatus.OPEN && now.isBefore(expiresAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getPaymentLinkId() {
        return paymentLinkId;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public CheckoutStatus getStatus() {
        return status;
    }

    public String getGatewayOrderId() {
        return gatewayOrderId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/checkout/persistence/CheckoutSessionRepository.java`:
```java
package com.fluxpay.checkout.persistence;

import com.fluxpay.checkout.domain.CheckoutSession;
import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CheckoutSessionRepository extends Repository<CheckoutSession, UUID> {

    CheckoutSession save(CheckoutSession session);

    /** Public lookup: the unguessable session id is the capability (spec §8). */
    Optional<CheckoutSession> findById(UUID id);

    Optional<CheckoutSession> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.gatewayOrderId = :orderId")
    Optional<CheckoutSession> lockByGatewayOrderId(@Param("orderId") String orderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CheckoutSession s set s.gatewayOrderId = :orderId where s.id = :id and s.gatewayOrderId is null")
    int attachOrder(@Param("id") UUID id, @Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSession s where s.status = :status and s.expiresAt <= :now order by s.expiresAt")
    List<CheckoutSession> lockDueForExpiry(
            @Param("status") CheckoutStatus status, @Param("now") Instant now, Limit limit);

    @Query("select s from CheckoutSession s where s.gatewayOrderId is not null and s.status in :statuses"
            + " and s.createdAt >= :from and s.createdAt <= :to order by s.createdAt")
    List<CheckoutSession> findReconcilable(
            @Param("statuses") Collection<CheckoutStatus> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Limit limit);
}
```

- [ ] **Step 6: Implement the service layer**

`src/main/java/com/fluxpay/checkout/service/CheckoutProperties.java`:
```java
package com.fluxpay.checkout.service;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.checkout")
public record CheckoutProperties(@NotNull Duration sessionTtl) {}
```

`src/main/java/com/fluxpay/checkout/service/NewCheckoutSession.java`:
```java
package com.fluxpay.checkout.service;

import java.util.Map;
import java.util.UUID;

public record NewCheckoutSession(
        UUID productId, String customerRef, String successUrl, String cancelUrl, Map<String, String> metadata) {}
```

`src/main/java/com/fluxpay/checkout/service/CheckoutSessionView.java`:
```java
package com.fluxpay.checkout.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CheckoutSessionView(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID productId,
        UUID paymentLinkId,
        long amount,
        String currency,
        String customerRef,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        CheckoutStatus status,
        String gatewayOrderId,
        String url,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt) {}
```

`src/main/java/com/fluxpay/checkout/service/PublicCheckoutView.java`:
```java
package com.fluxpay.checkout.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.UUID;

/** What the hosted checkout page may show. successUrl is present only once the session is completed. */
public record PublicCheckoutView(
        UUID id,
        Mode mode,
        CheckoutStatus status,
        long amount,
        String currency,
        String productName,
        String productDescription,
        String productImageUrl,
        String merchantName,
        String merchantLogoUrl,
        String merchantBrandColor,
        String successUrl,
        String cancelUrl,
        Instant expiresAt) {}
```

`src/main/java/com/fluxpay/checkout/service/PaymentInstructions.java`:
```java
package com.fluxpay.checkout.service;

/** Everything the browser needs to open the gateway's checkout for an order. */
public record PaymentInstructions(
        String gateway,
        String keyId,
        String orderId,
        long amount,
        String currency,
        String merchantName,
        String productName) {}
```

`src/main/java/com/fluxpay/checkout/service/CheckoutService.java`:
```java
package com.fluxpay.checkout.service;

import com.fluxpay.common.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutService {

    CheckoutSessionView create(TenantContext tenant, NewCheckoutSession session);

    CheckoutSessionView get(TenantContext tenant, UUID sessionId);

    CheckoutSessionView createFromLink(String slug, String customerRef);

    PublicCheckoutView getPublic(UUID sessionId);

    /** Creates (once) the gateway order for the session. Never holds a transaction during the gateway call. */
    PaymentInstructions startPayment(UUID sessionId);

    /** Row-locks the session owning this gateway order in the caller's transaction. */
    Optional<CheckoutSessionView> lockByGatewayOrderId(String gatewayOrderId);

    void markCompleted(UUID sessionId, Instant now);

    /** Expires up to {@code limit} open sessions past their expiry in the caller's transaction. */
    List<CheckoutSessionView> expireDue(Instant now, int limit);

    List<CheckoutSessionView> findReconcilable(Instant createdAfter, Instant createdBefore, int limit);
}
```

`src/main/java/com/fluxpay/checkout/service/CheckoutServiceImpl.java`:
```java
package com.fluxpay.checkout.service;

import com.fluxpay.catalog.service.PaymentLinkService;
import com.fluxpay.catalog.service.PaymentLinkView;
import com.fluxpay.catalog.service.ProductService;
import com.fluxpay.catalog.service.ProductView;
import com.fluxpay.checkout.domain.CheckoutSession;
import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.persistence.CheckoutSessionRepository;
import com.fluxpay.common.config.FluxpayProperties;
import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.common.validation.Metadata;
import com.fluxpay.common.web.RedirectUrlPolicy;
import com.fluxpay.merchants.service.MerchantService;
import com.fluxpay.merchants.service.MerchantView;
import com.fluxpay.payments.service.GatewayOrder;
import com.fluxpay.payments.service.PaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    private final CheckoutSessionRepository sessions;
    private final ProductService productService;
    private final PaymentLinkService paymentLinkService;
    private final MerchantService merchantService;
    private final PaymentGateway gateway;
    private final CheckoutProperties checkoutProperties;
    private final FluxpayProperties fluxpayProperties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public CheckoutServiceImpl(
            CheckoutSessionRepository sessions,
            ProductService productService,
            PaymentLinkService paymentLinkService,
            MerchantService merchantService,
            PaymentGateway gateway,
            CheckoutProperties checkoutProperties,
            FluxpayProperties fluxpayProperties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.productService = productService;
        this.paymentLinkService = paymentLinkService;
        this.merchantService = merchantService;
        this.gateway = gateway;
        this.checkoutProperties = checkoutProperties;
        this.fluxpayProperties = fluxpayProperties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public CheckoutSessionView create(TenantContext tenant, NewCheckoutSession command) {
        ProductView product = productService.get(tenant, command.productId());
        if (!product.active()) {
            throw FluxpayException.conflict("PRODUCT_INACTIVE", "This product is archived");
        }
        RedirectUrlPolicy.validate(tenant.mode(), "success_url", command.successUrl());
        RedirectUrlPolicy.validate(tenant.mode(), "cancel_url", command.cancelUrl());
        return view(save(tenant, product, null, command.customerRef(), command.successUrl(), command.cancelUrl(),
                Metadata.validated(command.metadata())));
    }

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionView get(TenantContext tenant, UUID sessionId) {
        return sessions.findByIdAndMerchantIdAndMode(sessionId, tenant.merchantId(), tenant.mode())
                .map(this::view)
                .orElseThrow(CheckoutServiceImpl::sessionNotFound);
    }

    @Override
    @Transactional
    public CheckoutSessionView createFromLink(String slug, String customerRef) {
        PaymentLinkView link =
                paymentLinkService.findActiveBySlug(slug).orElseThrow(CheckoutServiceImpl::linkNotFound);
        if (!merchantService.isActive(link.merchantId())) {
            throw linkNotFound();
        }
        TenantContext tenant = new TenantContext(link.merchantId(), link.mode());
        ProductView product = productService.get(tenant, link.productId());
        if (!product.active()) {
            throw linkNotFound();
        }
        return view(save(tenant, product, link.id(), customerRef, link.successUrl(), link.cancelUrl(),
                Metadata.validated(null)));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicCheckoutView getPublic(UUID sessionId) {
        CheckoutSession session = sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound);
        MerchantView merchant = merchantService.get(session.getMerchantId());
        ProductView product = productService.get(
                new TenantContext(session.getMerchantId(), session.getMode()), session.getProductId());
        CheckoutStatus status = session.getStatus() == CheckoutStatus.OPEN && !session.isPayableAt(Instant.now(clock))
                ? CheckoutStatus.EXPIRED
                : session.getStatus();
        return new PublicCheckoutView(
                session.getId(),
                session.getMode(),
                status,
                session.getAmount(),
                session.getCurrency(),
                product.name(),
                product.description(),
                product.imageUrl(),
                merchant.businessName(),
                merchant.logoUrl(),
                merchant.brandColor(),
                status == CheckoutStatus.COMPLETED ? session.getSuccessUrl() : null,
                session.getCancelUrl(),
                session.getExpiresAt());
    }

    @Override
    public PaymentInstructions startPayment(UUID sessionId) {
        CheckoutSession session = sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound);
        if (session.getStatus() == CheckoutStatus.COMPLETED) {
            throw FluxpayException.conflict("SESSION_COMPLETED", "This checkout session is already paid");
        }
        if (!session.isPayableAt(Instant.now(clock))) {
            throw FluxpayException.conflict("SESSION_EXPIRED", "This checkout session has expired");
        }
        if (!merchantService.isActive(session.getMerchantId())) {
            throw FluxpayException.conflict("MERCHANT_UNAVAILABLE", "This merchant cannot accept payments");
        }
        String orderId = session.getGatewayOrderId() != null ? session.getGatewayOrderId() : attachNewOrder(session);
        MerchantView merchant = merchantService.get(session.getMerchantId());
        ProductView product = productService.get(
                new TenantContext(session.getMerchantId(), session.getMode()), session.getProductId());
        return new PaymentInstructions(
                gateway.name(),
                gateway.publicKeyId(session.getMode()),
                orderId,
                session.getAmount(),
                session.getCurrency(),
                merchant.businessName(),
                product.name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CheckoutSessionView> lockByGatewayOrderId(String gatewayOrderId) {
        return sessions.lockByGatewayOrderId(gatewayOrderId).map(this::view);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markCompleted(UUID sessionId, Instant now) {
        sessions.findById(sessionId).orElseThrow(CheckoutServiceImpl::sessionNotFound).complete(now);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CheckoutSessionView> expireDue(Instant now, int limit) {
        List<CheckoutSession> due = sessions.lockDueForExpiry(CheckoutStatus.OPEN, now, Limit.of(limit));
        due.forEach(CheckoutSession::expire);
        return due.stream().map(this::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckoutSessionView> findReconcilable(Instant createdAfter, Instant createdBefore, int limit) {
        return sessions
                .findReconcilable(
                        List.of(CheckoutStatus.OPEN, CheckoutStatus.EXPIRED),
                        createdAfter,
                        createdBefore,
                        Limit.of(limit))
                .stream()
                .map(this::view)
                .toList();
    }

    /** Gateway call happens outside any transaction; the conditional update makes concurrent calls converge. */
    private String attachNewOrder(CheckoutSession session) {
        String publicId = PublicId.of(IdPrefix.CHECKOUT_SESSION, session.getId());
        GatewayOrder order = gateway.createOrder(
                session.getMode(),
                session.getAmount(),
                session.getCurrency(),
                publicId,
                Map.of(
                        "checkout_session_id", publicId,
                        "merchant_id", PublicId.of(IdPrefix.MERCHANT, session.getMerchantId())));
        Integer attached = transaction.execute(status -> sessions.attachOrder(session.getId(), order.id()));
        if (attached != null && attached == 1) {
            return order.id();
        }
        return sessions.findById(session.getId())
                .map(CheckoutSession::getGatewayOrderId)
                .orElseThrow(CheckoutServiceImpl::sessionNotFound);
    }

    private CheckoutSession save(
            TenantContext tenant,
            ProductView product,
            UUID paymentLinkId,
            String customerRef,
            String successUrl,
            String cancelUrl,
            Map<String, String> metadata) {
        Instant now = Instant.now(clock);
        return sessions.save(new CheckoutSession(
                tenant.merchantId(),
                tenant.mode(),
                product.id(),
                paymentLinkId,
                product.amount(),
                product.currency(),
                customerRef,
                successUrl,
                cancelUrl,
                metadata,
                now.plus(checkoutProperties.sessionTtl()),
                now));
    }

    private CheckoutSessionView view(CheckoutSession session) {
        return new CheckoutSessionView(
                session.getId(),
                session.getMerchantId(),
                session.getMode(),
                session.getProductId(),
                session.getPaymentLinkId(),
                session.getAmount(),
                session.getCurrency(),
                session.getCustomerRef(),
                session.getSuccessUrl(),
                session.getCancelUrl(),
                Map.copyOf(session.getMetadata()),
                session.getStatus(),
                session.getGatewayOrderId(),
                fluxpayProperties.frontendBaseUrl() + "/pay/" + PublicId.of(IdPrefix.CHECKOUT_SESSION, session.getId()),
                session.getExpiresAt(),
                session.getCompletedAt(),
                session.getCreatedAt());
    }

    private static FluxpayException sessionNotFound() {
        return FluxpayException.notFound("CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found");
    }

    private static FluxpayException linkNotFound() {
        return FluxpayException.notFound("PAYMENT_LINK_NOT_FOUND", "Payment link not found");
    }
}
```

- [ ] **Step 7: Implement the APIs**

`src/main/java/com/fluxpay/checkout/api/CreateCheckoutSessionRequest.java`:
```java
package com.fluxpay.checkout.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateCheckoutSessionRequest(
        @NotBlank String productId,
        @Size(max = 255) String customerRef,
        @NotBlank @Size(max = 2048) String successUrl,
        @NotBlank @Size(max = 2048) String cancelUrl,
        Map<String, String> metadata) {}
```

`src/main/java/com/fluxpay/checkout/api/CheckoutSessionResponse.java`:
```java
package com.fluxpay.checkout.api;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;
import java.util.Map;

public record CheckoutSessionResponse(
        String id,
        String url,
        Mode mode,
        CheckoutStatus status,
        String productId,
        String paymentLinkId,
        long amount,
        String currency,
        String customerRef,
        String successUrl,
        String cancelUrl,
        Map<String, String> metadata,
        Instant expiresAt,
        Instant completedAt,
        Instant createdAt) {

    public static CheckoutSessionResponse from(CheckoutSessionView view) {
        return new CheckoutSessionResponse(
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.id()),
                view.url(),
                view.mode(),
                view.status(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                view.paymentLinkId() == null ? null : PublicId.of(IdPrefix.PAYMENT_LINK, view.paymentLinkId()),
                view.amount(),
                view.currency(),
                view.customerRef(),
                view.successUrl(),
                view.cancelUrl(),
                view.metadata(),
                view.expiresAt(),
                view.completedAt(),
                view.createdAt());
    }
}
```

`src/main/java/com/fluxpay/checkout/api/CheckoutSessionApiController.java`:
```java
package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.NewCheckoutSession;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.idempotency.IdempotencyService;
import com.fluxpay.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Merchant backends create checkout sessions with their secret API key (spec §4.1). */
@RestController
@RequestMapping("/api/v1/checkout_sessions")
public class CheckoutSessionApiController {

    private final CheckoutService checkoutService;
    private final IdempotencyService idempotency;

    public CheckoutSessionApiController(CheckoutService checkoutService, IdempotencyService idempotency) {
        this.checkoutService = checkoutService;
        this.idempotency = idempotency;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutSessionResponse create(
            TenantContext tenant,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateCheckoutSessionRequest body) {
        UUID productId =
                PublicId.parseOrNotFound(IdPrefix.PRODUCT, body.productId(), "PRODUCT_NOT_FOUND", "Product not found");
        NewCheckoutSession command = new NewCheckoutSession(
                productId, body.customerRef(), body.successUrl(), body.cancelUrl(), body.metadata());
        return idempotency.execute(
                tenant,
                idempotencyKey,
                body,
                CheckoutSessionResponse.class,
                () -> CheckoutSessionResponse.from(checkoutService.create(tenant, command)));
    }

    @GetMapping("/{id}")
    public CheckoutSessionResponse get(TenantContext tenant, @PathVariable String id) {
        return CheckoutSessionResponse.from(checkoutService.get(tenant, parseId(id)));
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(
                IdPrefix.CHECKOUT_SESSION, id, "CHECKOUT_SESSION_NOT_FOUND", "Checkout session not found");
    }
}
```

`src/main/java/com/fluxpay/checkout/api/PublicCheckoutResponse.java`:
```java
package com.fluxpay.checkout.api;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.PublicCheckoutView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import java.time.Instant;

public record PublicCheckoutResponse(
        String id,
        Mode mode,
        CheckoutStatus status,
        long amount,
        String currency,
        Product product,
        Merchant merchant,
        String successUrl,
        String cancelUrl,
        Instant expiresAt) {

    public record Product(String name, String description, String imageUrl) {}

    public record Merchant(String name, String logoUrl, String brandColor) {}

    public static PublicCheckoutResponse from(PublicCheckoutView view) {
        return new PublicCheckoutResponse(
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.id()),
                view.mode(),
                view.status(),
                view.amount(),
                view.currency(),
                new Product(view.productName(), view.productDescription(), view.productImageUrl()),
                new Merchant(view.merchantName(), view.merchantLogoUrl(), view.merchantBrandColor()),
                view.successUrl(),
                view.cancelUrl(),
                view.expiresAt());
    }
}
```

`src/main/java/com/fluxpay/checkout/api/PaymentInstructionsResponse.java`:
```java
package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.PaymentInstructions;

public record PaymentInstructionsResponse(
        String gateway,
        String keyId,
        String orderId,
        long amount,
        String currency,
        String merchantName,
        String productName) {

    public static PaymentInstructionsResponse from(PaymentInstructions instructions) {
        return new PaymentInstructionsResponse(
                instructions.gateway(),
                instructions.keyId(),
                instructions.orderId(),
                instructions.amount(),
                instructions.currency(),
                instructions.merchantName(),
                instructions.productName());
    }
}
```

`src/main/java/com/fluxpay/checkout/api/LinkCheckoutRequest.java`:
```java
package com.fluxpay.checkout.api;

import jakarta.validation.constraints.Size;

/** {@code ref} is the merchant's customer reference, passed as {@code ?ref=} on the payment link. */
public record LinkCheckoutRequest(@Size(max = 255) String ref) {}
```

`src/main/java/com/fluxpay/checkout/api/LinkCheckoutResponse.java`:
```java
package com.fluxpay.checkout.api;

public record LinkCheckoutResponse(String id, String url) {}
```

`src/main/java/com/fluxpay/checkout/api/PublicCheckoutController.java`:
```java
package com.fluxpay.checkout.api;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated endpoints used by the hosted checkout pages. Rate-limited by config. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicCheckoutController {

    private final CheckoutService checkoutService;

    public PublicCheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @GetMapping("/checkout_sessions/{id}")
    public PublicCheckoutResponse get(@PathVariable String id) {
        return PublicCheckoutResponse.from(checkoutService.getPublic(CheckoutSessionApiController.parseId(id)));
    }

    @PostMapping("/checkout_sessions/{id}/pay")
    public PaymentInstructionsResponse pay(@PathVariable String id) {
        return PaymentInstructionsResponse.from(
                checkoutService.startPayment(CheckoutSessionApiController.parseId(id)));
    }

    @PostMapping("/payment_links/{slug}/checkout_sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkCheckoutResponse createFromLink(
            @PathVariable String slug, @Valid @RequestBody(required = false) LinkCheckoutRequest body) {
        String ref = body == null ? null : body.ref();
        CheckoutSessionView session = checkoutService.createFromLink(slug, ref);
        return new LinkCheckoutResponse(PublicId.of(IdPrefix.CHECKOUT_SESSION, session.id()), session.url());
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.checkout.*'` then `./gradlew test`
Expected: PASS. If the double-click test sees two different order IDs, check that `attachOrder` is a conditional update (`gatewayOrderId is null`) and that the loser re-reads the session.

- [ ] **Step 9: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(checkout): checkout sessions with idempotent creation, payment links and hosted checkout api

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 9: Payment capture — Razorpay webhooks, payments, sales

**Files:**
- Create: `src/main/resources/db/migration/V10__payments_and_sales.sql`, `src/main/resources/db/rollback/V10__down.sql`
- Create: `src/main/java/com/fluxpay/payments/domain/{Payment,PaymentStatus}.java`, `payments/persistence/PaymentRepository.java`
- Create: `src/main/java/com/fluxpay/payments/service/{NewPaymentRecord,RecordedPayment,PaymentRecordService,PaymentRecordServiceImpl,GatewayEventHandler}.java`, `payments/api/RazorpayWebhookController.java`
- Create: `src/main/java/com/fluxpay/sales/domain/{Sale,SaleStatus}.java`, `sales/persistence/SaleRepository.java`
- Create: `src/main/java/com/fluxpay/sales/service/{SalePayloads,SaleCaptureService,SaleCaptureServiceImpl,GatewayEventRouter}.java`
- Modify: `src/main/java/com/fluxpay/config/SecurityConfig.java`
- Test: `src/test/java/com/fluxpay/sales/CaptureFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `CheckoutService.lockByGatewayOrderId/markCompleted`, `LedgerService.recordSale`, `EventPublisher.publish`, `PaymentGateway.verifyWebhookSignature`, `RazorpayWebhookParser`.
- Produces:
  - `enum PaymentStatus { CAPTURED, AMOUNT_MISMATCH, DUPLICATE }` (JSON lowercase)
  - `record RecordedPayment(UUID id, UUID merchantId, Mode mode, UUID checkoutSessionId, String gatewayPaymentId, PaymentStatus status, long amount, String currency, String method, long gatewayFee, Instant createdAt)`
  - `interface PaymentRecordService { Optional<RecordedPayment> findByGatewayPaymentId(String); Optional<RecordedPayment> findById(UUID); RecordedPayment record(NewPaymentRecord); }` (`record` is MANDATORY)
  - `interface GatewayEventHandler { void onPaymentCaptured(Mode, GatewayPayment); void onRefundProcessed(Mode, GatewayRefund); }` — implemented by `sales.GatewayEventRouter`
  - Route: `POST /api/v1/gateway-webhooks/razorpay/{test|live}` (public, signature-checked; unknown mode → 404; bad signature → 400 `INVALID_SIGNATURE`; returns `{"received": true}`)
  - `enum SaleStatus { PAID, PARTIALLY_REFUNDED, REFUNDED }` (JSON lowercase, `static Optional<SaleStatus> parse(String)`)
  - `interface SaleCaptureService { void capture(Mode mode, GatewayPayment payment); }` — idempotent per gateway payment ID
  - `SalePayloads.completed(Sale): Map<String, Object>` keys: `sale_id, checkout_session_id, product_id, customer_ref, amount, currency, metadata`
  - Task 10 replaces `GatewayEventRouter.onRefundProcessed`.

- [ ] **Step 1: Write the migration**

`src/main/resources/db/migration/V10__payments_and_sales.sql`:
```sql
CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchants (id),
    mode                VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    checkout_session_id UUID          NOT NULL REFERENCES checkout_sessions (id),
    gateway             VARCHAR(20)   NOT NULL,
    gateway_payment_id  VARCHAR(100)  NOT NULL UNIQUE,
    gateway_order_id    VARCHAR(100)  NOT NULL,
    status              VARCHAR(30)   NOT NULL CHECK (status IN ('CAPTURED', 'AMOUNT_MISMATCH', 'DUPLICATE')),
    amount              BIGINT        NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    method              VARCHAR(30),
    gateway_fee         BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX payments_session_idx ON payments (checkout_session_id);

CREATE TABLE sales (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchants (id),
    mode                VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id          UUID          NOT NULL REFERENCES products (id),
    checkout_session_id UUID          NOT NULL UNIQUE REFERENCES checkout_sessions (id),
    payment_id          UUID          NOT NULL UNIQUE REFERENCES payments (id),
    customer_ref        VARCHAR(255),
    amount              BIGINT        NOT NULL,
    refunded_amount     BIGINT        NOT NULL DEFAULT 0,
    currency            VARCHAR(3)    NOT NULL,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    metadata            JSONB         NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX sales_merchant_mode_idx ON sales (merchant_id, mode, id DESC);
CREATE INDEX sales_merchant_mode_customer_idx ON sales (merchant_id, mode, customer_ref);
CREATE INDEX sales_merchant_mode_product_idx ON sales (merchant_id, mode, product_id);
```

`src/main/resources/db/rollback/V10__down.sql`:
```sql
DROP TABLE IF EXISTS sales;
DROP TABLE IF EXISTS payments;
DELETE FROM flyway_schema_history WHERE version = '10';
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/fluxpay/sales/CaptureFlowIntegrationTest.java`:
```java
package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class CaptureFlowIntegrationTest extends AbstractIntegrationTest {

    private static final String WEBHOOK = "/api/v1/gateway-webhooks/razorpay/test";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SignedIn owner;
    private String productId;
    private String apiKey;
    private String sessionId;
    private String orderId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, apiKey, productId, "u_123");
        orderId = TestMerchants.pay(mockMvc, sessionId);
    }

    private ResultActions deliver(String path, String body, String signature) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) {
            request.header("X-Razorpay-Signature", signature);
        }
        return mockMvc.perform(request);
    }

    private ResultActions deliver(String body) throws Exception {
        return deliver(WEBHOOK, body, TestWebhooks.sign(body));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    void should_record_sale_ledger_and_event_when_payment_is_captured() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));

        mockMvc.perform(get("/api/v1/checkout_sessions/" + sessionId).header("Authorization", "Bearer " + apiKey))
                .andExpect(jsonPath("$.status").value("completed"));
        assertThat(jdbcTemplate.queryForObject("SELECT customer_ref FROM sales", String.class)).isEqualTo("u_123");
        assertThat(jdbcTemplate.queryForList("SELECT amount FROM ledger_entries ORDER BY amount DESC", Long.class))
                .containsExactly(4900L, -116L, -245L);
        assertThat(jdbcTemplate.queryForObject("SELECT data->>'customer_ref' FROM events", String.class))
                .isEqualTo("u_123");
        assertThat(jdbcTemplate.queryForObject("SELECT data->'metadata'->>'order' FROM events", String.class))
                .isEqualTo("42");
        mockMvc.perform(get("/api/v1/public/checkout_sessions/" + sessionId))
                .andExpect(jsonPath("$.success_url").value("https://jextter.com/paid"));
    }

    @Test
    void should_record_exactly_once_when_webhook_is_delivered_twice() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);

        deliver(body).andExpect(status().isOk());
        deliver(body).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger_entries")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM events")).isEqualTo(1);
    }

    @Test
    void should_record_exactly_once_when_duplicates_arrive_concurrently() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        Callable<Integer> delivery = () -> deliver(body).andReturn().getResponse().getStatus();

        pool.invokeAll(List.of(delivery, delivery, delivery));
        pool.shutdown();

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM payments")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM events")).isEqualTo(1);
    }

    @Test
    void should_still_record_sale_when_payment_arrives_after_expiry() throws Exception {
        jdbcTemplate.update("UPDATE checkout_sessions SET status = 'EXPIRED', expires_at = now() - interval '5 minutes'");

        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
    }

    @Test
    void should_flag_payment_and_skip_sale_when_amount_differs_from_snapshot() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 100, 0)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payments", String.class))
                .isEqualTo("AMOUNT_MISMATCH");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("OPEN");
    }

    @Test
    void should_record_snapshot_price_after_product_is_repriced() throws Exception {
        mockMvc.perform(patch("/api/v1/dashboard/products/" + productId)
                .with(csrf())
                .cookie(owner.session())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9900}"));

        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM sales", Long.class)).isEqualTo(4900L);
    }

    @Test
    void should_flag_second_payment_for_completed_session_as_duplicate() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116));

        deliver(TestWebhooks.paymentCaptured("pay_2", orderId, 4900, 116)).andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE gateway_payment_id = 'pay_2'", String.class))
                .isEqualTo("DUPLICATE");
    }

    @Test
    void should_reject_missing_or_wrong_signature_and_unconfigured_mode() throws Exception {
        String body = TestWebhooks.paymentCaptured("pay_1", orderId, 4900, 116);

        deliver(WEBHOOK, body, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_SIGNATURE"));
        deliver(WEBHOOK, body, "deadbeef").andExpect(status().isBadRequest());
        deliver("/api/v1/gateway-webhooks/razorpay/live", body, TestWebhooks.sign(body))
                .andExpect(status().isBadRequest());
        deliver("/api/v1/gateway-webhooks/razorpay/prod", body, TestWebhooks.sign(body))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM sales")).isZero();
    }

    @Test
    void should_acknowledge_unknown_orders_and_ignored_event_types() throws Exception {
        deliver(TestWebhooks.paymentCaptured("pay_9", "order_unknown", 4900, 116)).andExpect(status().isOk());
        deliver("{\"event\":\"order.paid\",\"payload\":{}}").andExpect(status().isOk());

        assertThat(count("SELECT count(*) FROM payments")).isZero();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.sales.CaptureFlowIntegrationTest'`
Expected: FAIL — webhook route returns 401 (not permitted) / 404; every test fails.

- [ ] **Step 4: Implement payments domain, persistence and service**

`src/main/java/com/fluxpay/payments/domain/PaymentStatus.java`:
```java
package com.fluxpay.payments.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** CAPTURED produced a sale; AMOUNT_MISMATCH and DUPLICATE need manual review and refund. */
public enum PaymentStatus {
    CAPTURED,
    AMOUNT_MISMATCH,
    DUPLICATE;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
```

`src/main/java/com/fluxpay/payments/domain/Payment.java`:
```java
package com.fluxpay.payments.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Column(nullable = false)
    private String gateway;

    @Column(name = "gateway_payment_id", nullable = false)
    private String gatewayPaymentId;

    @Column(name = "gateway_order_id", nullable = false)
    private String gatewayOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private String currency;

    private String method;

    @Column(name = "gateway_fee", nullable = false)
    private long gatewayFee;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(
            UUID merchantId,
            Mode mode,
            UUID checkoutSessionId,
            String gateway,
            String gatewayPaymentId,
            String gatewayOrderId,
            PaymentStatus status,
            long amount,
            String currency,
            String method,
            long gatewayFee,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.checkoutSessionId = checkoutSessionId;
        this.gateway = gateway;
        this.gatewayPaymentId = gatewayPaymentId;
        this.gatewayOrderId = gatewayOrderId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.method = method;
        this.gatewayFee = gatewayFee;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public String getGatewayPaymentId() {
        return gatewayPaymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public long getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMethod() {
        return method;
    }

    public long getGatewayFee() {
        return gatewayFee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/payments/persistence/PaymentRepository.java`:
```java
package com.fluxpay.payments.persistence;

import com.fluxpay.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Lookups by gateway id serve the webhook flow; callers re-check tenant on the returned record. */
public interface PaymentRepository extends Repository<Payment, UUID> {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);
}
```

`src/main/java/com/fluxpay/payments/service/NewPaymentRecord.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.domain.PaymentStatus;
import java.util.UUID;

public record NewPaymentRecord(
        UUID merchantId,
        Mode mode,
        UUID checkoutSessionId,
        String gateway,
        GatewayPayment payment,
        PaymentStatus status) {}
```

`src/main/java/com/fluxpay/payments/service/RecordedPayment.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.domain.Payment;
import com.fluxpay.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record RecordedPayment(
        UUID id,
        UUID merchantId,
        Mode mode,
        UUID checkoutSessionId,
        String gatewayPaymentId,
        PaymentStatus status,
        long amount,
        String currency,
        String method,
        long gatewayFee,
        Instant createdAt) {

    static RecordedPayment from(Payment payment) {
        return new RecordedPayment(
                payment.getId(),
                payment.getMerchantId(),
                payment.getMode(),
                payment.getCheckoutSessionId(),
                payment.getGatewayPaymentId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getGatewayFee(),
                payment.getCreatedAt());
    }
}
```

`src/main/java/com/fluxpay/payments/service/PaymentRecordService.java`:
```java
package com.fluxpay.payments.service;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRecordService {

    Optional<RecordedPayment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<RecordedPayment> findById(UUID paymentId);

    /** Inserts in the caller's transaction. The unique gateway payment id makes duplicates fail loudly. */
    RecordedPayment record(NewPaymentRecord record);
}
```

`src/main/java/com/fluxpay/payments/service/PaymentRecordServiceImpl.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.payments.domain.Payment;
import com.fluxpay.payments.persistence.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRecordServiceImpl implements PaymentRecordService {

    private final PaymentRepository payments;
    private final Clock clock;

    public PaymentRecordServiceImpl(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedPayment> findByGatewayPaymentId(String gatewayPaymentId) {
        return payments.findByGatewayPaymentId(gatewayPaymentId).map(RecordedPayment::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedPayment> findById(UUID paymentId) {
        return payments.findById(paymentId).map(RecordedPayment::from);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedPayment record(NewPaymentRecord record) {
        GatewayPayment payment = record.payment();
        Payment saved = payments.save(new Payment(
                record.merchantId(),
                record.mode(),
                record.checkoutSessionId(),
                record.gateway(),
                payment.id(),
                payment.orderId(),
                record.status(),
                payment.amount(),
                payment.currency(),
                payment.method(),
                payment.fee(),
                Instant.now(clock)));
        return RecordedPayment.from(saved);
    }
}
```

`src/main/java/com/fluxpay/payments/service/GatewayEventHandler.java`:
```java
package com.fluxpay.payments.service;

import com.fluxpay.common.tenant.Mode;

/** Implemented outside the payments module (by sales) so payments never depends on sales. */
public interface GatewayEventHandler {

    void onPaymentCaptured(Mode mode, GatewayPayment payment);

    void onRefundProcessed(Mode mode, GatewayRefund refund);
}
```

`src/main/java/com/fluxpay/payments/api/RazorpayWebhookController.java`:
```java
package com.fluxpay.payments.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayWebhookEvent;
import com.fluxpay.payments.service.PaymentGateway;
import com.fluxpay.payments.service.RazorpayWebhookParser;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay calls this for every event. The signature over the raw body is the only authentication, so it is
 * checked before parsing. Processing errors return 5xx so Razorpay retries; processing is idempotent.
 */
@RestController
public class RazorpayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

    private final PaymentGateway gateway;
    private final RazorpayWebhookParser parser;
    private final GatewayEventHandler handler;

    public RazorpayWebhookController(
            PaymentGateway gateway, RazorpayWebhookParser parser, GatewayEventHandler handler) {
        this.gateway = gateway;
        this.parser = parser;
        this.handler = handler;
    }

    @PostMapping("/api/v1/gateway-webhooks/razorpay/{mode}")
    public Map<String, Boolean> receive(
            @PathVariable("mode") String modeValue,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        Mode mode = Mode.parse(modeValue)
                .orElseThrow(() -> FluxpayException.notFound("NOT_FOUND", "Unknown webhook endpoint"));
        if (!gateway.verifyWebhookSignature(mode, body, signature)) {
            log.warn("Rejected Razorpay webhook with invalid signature for mode {}", mode.value());
            throw FluxpayException.badRequest("INVALID_SIGNATURE", "Webhook signature verification failed");
        }
        switch (parser.parse(body)) {
            case GatewayWebhookEvent.PaymentCaptured captured -> handler.onPaymentCaptured(mode, captured.payment());
            case GatewayWebhookEvent.RefundProcessed refund -> handler.onRefundProcessed(mode, refund.refund());
            case GatewayWebhookEvent.Ignored ignored -> log.debug("Ignoring Razorpay event {}", ignored.type());
        }
        return Map.of("received", true);
    }
}
```

In `src/main/java/com/fluxpay/config/SecurityConfig.java`, add before `.requestMatchers("/api/v1/public/**")`:
```java
                        .requestMatchers(HttpMethod.POST, "/api/v1/gateway-webhooks/**")
                        .permitAll()
```

- [ ] **Step 5: Implement sales domain and capture**

`src/main/java/com/fluxpay/sales/domain/SaleStatus.java`:
```java
package com.fluxpay.sales.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SaleStatus {
    PAID,
    PARTIALLY_REFUNDED,
    REFUNDED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SaleStatus> parse(String raw) {
        return Arrays.stream(values()).filter(status -> status.value().equals(raw)).findFirst();
    }
}
```

`src/main/java/com/fluxpay/sales/domain/Sale.java`:
```java
package com.fluxpay.sales.domain;

import com.fluxpay.common.id.UuidV7;
import com.fluxpay.common.tenant.Mode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** FluxPay's record that a customer bought a product (spec §5). Exactly one per captured payment. */
@Entity
@Table(name = "sales")
public class Sale {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "checkout_session_id", nullable = false)
    private UUID checkoutSessionId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "customer_ref")
    private String customerRef;

    @Column(nullable = false)
    private long amount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Sale() {}

    public Sale(
            UUID merchantId,
            Mode mode,
            UUID productId,
            UUID checkoutSessionId,
            UUID paymentId,
            String customerRef,
            long amount,
            String currency,
            Map<String, String> metadata,
            Instant now) {
        this.id = UuidV7.generate();
        this.merchantId = merchantId;
        this.mode = mode;
        this.productId = productId;
        this.checkoutSessionId = checkoutSessionId;
        this.paymentId = paymentId;
        this.customerRef = customerRef;
        this.amount = amount;
        this.currency = currency;
        this.metadata = metadata;
        this.status = SaleStatus.PAID;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyRefund(long refund, Instant now) {
        this.refundedAmount += refund;
        this.status = refundedAmount >= amount ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Mode getMode() {
        return mode;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getCheckoutSessionId() {
        return checkoutSessionId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public long getAmount() {
        return amount;
    }

    public long getRefundedAmount() {
        return refundedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

`src/main/java/com/fluxpay/sales/persistence/SaleRepository.java`:
```java
package com.fluxpay.sales.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends Repository<Sale, UUID>, JpaSpecificationExecutor<Sale> {

    Sale save(Sale sale);

    Optional<Sale> findByIdAndMerchantIdAndMode(UUID id, UUID merchantId, Mode mode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.paymentId = :paymentId")
    Optional<Sale> lockByPaymentId(@Param("paymentId") UUID paymentId);
}
```

`src/main/java/com/fluxpay/sales/service/SalePayloads.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.sales.domain.Sale;
import java.util.LinkedHashMap;
import java.util.Map;

/** The `data` object of merchant webhook events (spec §7). */
public final class SalePayloads {

    private SalePayloads() {}

    public static Map<String, Object> completed(Sale sale) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sale_id", PublicId.of(IdPrefix.SALE, sale.getId()));
        data.put("checkout_session_id", PublicId.of(IdPrefix.CHECKOUT_SESSION, sale.getCheckoutSessionId()));
        data.put("product_id", PublicId.of(IdPrefix.PRODUCT, sale.getProductId()));
        data.put("customer_ref", sale.getCustomerRef());
        data.put("amount", sale.getAmount());
        data.put("currency", sale.getCurrency());
        data.put("metadata", sale.getMetadata());
        return data;
    }

    public static Map<String, Object> refunded(Sale sale, long refundAmount) {
        Map<String, Object> data = completed(sale);
        data.put("refund_amount", refundAmount);
        data.put("amount_refunded", sale.getRefundedAmount());
        data.put("status", sale.getStatus().value());
        return data;
    }

    public static Map<String, Object> expired(CheckoutSessionView session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("checkout_session_id", PublicId.of(IdPrefix.CHECKOUT_SESSION, session.id()));
        data.put("product_id", PublicId.of(IdPrefix.PRODUCT, session.productId()));
        data.put("customer_ref", session.customerRef());
        data.put("amount", session.amount());
        data.put("currency", session.currency());
        data.put("metadata", session.metadata());
        return data;
    }
}
```

`src/main/java/com/fluxpay/sales/service/SaleCaptureService.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayPayment;

public interface SaleCaptureService {

    /** Records the sale for a captured payment. Safe to call any number of times for the same payment. */
    void capture(Mode mode, GatewayPayment payment);
}
```

`src/main/java/com/fluxpay/sales/service/SaleCaptureServiceImpl.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.checkout.domain.CheckoutStatus;
import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.NewPaymentRecord;
import com.fluxpay.payments.service.PaymentGateway;
import com.fluxpay.payments.service.PaymentRecordService;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.persistence.SaleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec §6 step 3. One transaction: payment row, session completion, sale, ledger entries and the
 * checkout.completed outbox event. The session row lock serialises concurrent deliveries of the same order.
 */
@Service
public class SaleCaptureServiceImpl implements SaleCaptureService {

    private static final Logger log = LoggerFactory.getLogger(SaleCaptureServiceImpl.class);

    private final CheckoutService checkoutService;
    private final PaymentRecordService paymentRecords;
    private final PaymentGateway gateway;
    private final SaleRepository sales;
    private final LedgerService ledgerService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public SaleCaptureServiceImpl(
            CheckoutService checkoutService,
            PaymentRecordService paymentRecords,
            PaymentGateway gateway,
            SaleRepository sales,
            LedgerService ledgerService,
            EventPublisher eventPublisher,
            Clock clock) {
        this.checkoutService = checkoutService;
        this.paymentRecords = paymentRecords;
        this.gateway = gateway;
        this.sales = sales;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void capture(Mode mode, GatewayPayment payment) {
        if (!payment.isCaptured() || alreadyRecorded(payment)) {
            return;
        }
        Optional<CheckoutSessionView> locked = checkoutService.lockByGatewayOrderId(payment.orderId());
        if (locked.isEmpty()) {
            log.warn("Captured payment {} references unknown order {}", payment.id(), payment.orderId());
            return;
        }
        CheckoutSessionView session = locked.get();
        if (session.mode() != mode) {
            log.warn("Captured payment {} arrived on {} endpoint for a {} session", payment.id(), mode, session.mode());
            return;
        }
        if (alreadyRecorded(payment)) {
            return;
        }
        if (session.status() == CheckoutStatus.COMPLETED) {
            record(session, payment, PaymentStatus.DUPLICATE);
            log.error("Second payment {} for completed session {}: refund it manually", payment.id(), session.id());
            return;
        }
        if (payment.amount() != session.amount() || !session.currency().equals(payment.currency())) {
            record(session, payment, PaymentStatus.AMOUNT_MISMATCH);
            log.error("Payment {} amount {} does not match session {} amount {}",
                    payment.id(), payment.amount(), session.id(), session.amount());
            return;
        }
        completeSale(session, payment);
    }

    private void completeSale(CheckoutSessionView session, GatewayPayment payment) {
        Instant now = Instant.now(clock);
        TenantContext tenant = new TenantContext(session.merchantId(), session.mode());
        RecordedPayment recorded = record(session, payment, PaymentStatus.CAPTURED);
        checkoutService.markCompleted(session.id(), now);
        Sale sale = sales.save(new Sale(
                session.merchantId(),
                session.mode(),
                session.productId(),
                session.id(),
                recorded.id(),
                session.customerRef(),
                session.amount(),
                session.currency(),
                session.metadata(),
                now));
        ledgerService.recordSale(tenant, sale.getId(), sale.getAmount(), sale.getCurrency(), payment.fee());
        eventPublisher.publish(tenant, EventType.CHECKOUT_COMPLETED, SalePayloads.completed(sale));
    }

    private boolean alreadyRecorded(GatewayPayment payment) {
        return paymentRecords.findByGatewayPaymentId(payment.id()).isPresent();
    }

    private RecordedPayment record(CheckoutSessionView session, GatewayPayment payment, PaymentStatus status) {
        return paymentRecords.record(new NewPaymentRecord(
                session.merchantId(), session.mode(), session.id(), gateway.name(), payment, status));
    }
}
```

`src/main/java/com/fluxpay/sales/service/GatewayEventRouter.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GatewayEventRouter implements GatewayEventHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventRouter.class);

    private final SaleCaptureService captureService;

    public GatewayEventRouter(SaleCaptureService captureService) {
        this.captureService = captureService;
    }

    @Override
    public void onPaymentCaptured(Mode mode, GatewayPayment payment) {
        captureService.capture(mode, payment);
    }

    @Override
    public void onRefundProcessed(Mode mode, GatewayRefund refund) {
        log.info("Refund {} received; refund handling arrives in the next task", refund.id());
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.sales.*'` then `./gradlew test`
Expected: PASS. If the concurrent-duplicate test records two payments, confirm `alreadyRecorded` is re-checked **after** `lockByGatewayOrderId`.

- [ ] **Step 7: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(sales): record exactly one sale per captured razorpay payment with ledger and outbox event

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 10: Refunds from Razorpay

**Files:**
- Create: `src/main/java/com/fluxpay/sales/service/{SaleRefundService,SaleRefundServiceImpl}.java`
- Modify: `src/main/java/com/fluxpay/sales/service/GatewayEventRouter.java`, `src/test/java/com/fluxpay/support/TestMerchants.java`
- Test: `src/test/java/com/fluxpay/sales/RefundFlowIntegrationTest.java`

**Interfaces:**
- Consumes: `PaymentRecordService.findByGatewayPaymentId`, `SaleRepository.lockByPaymentId`, `LedgerService.recordRefund`, `EventPublisher`, `SalePayloads.refunded`.
- Produces:
  - `interface SaleRefundService { void refund(Mode mode, GatewayRefund refund); }` — idempotent per gateway refund ID; partial refunds → `partially_refunded`, cumulative ≥ amount → `refunded`; emits `sale.refunded` with `refund_amount`, `amount_refunded`, `status`
  - `TestMerchants.completeSale(MockMvc, String apiKey, String productId, long amount, String customerRef, String gatewayPaymentId): String` — creates a session, pays, delivers a signed `payment.captured`; returns the `cs_…` id

- [ ] **Step 1: Add the sale fixture**

Add to `src/test/java/com/fluxpay/support/TestMerchants.java`:
```java
    public static String completeSale(
            MockMvc mockMvc, String apiKey, String productId, long amount, String customerRef, String gatewayPaymentId)
            throws Exception {
        String sessionId = createCheckoutSession(mockMvc, apiKey, productId, customerRef);
        String orderId = pay(mockMvc, sessionId);
        String body = TestWebhooks.paymentCaptured(gatewayPaymentId, orderId, amount, 116);
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/gateway-webhooks/razorpay/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", TestWebhooks.sign(body))
                        .content(body))
                .andExpect(status().isOk());
        return sessionId;
    }
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/fluxpay/sales/RefundFlowIntegrationTest.java`:
```java
package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class RefundFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void captureSale() throws Exception {
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        TestMerchants.completeSale(mockMvc, apiKey, productId, 4900, "u_123", "pay_1");
    }

    private void deliver(String body) throws Exception {
        mockMvc.perform(post("/api/v1/gateway-webhooks/razorpay/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", TestWebhooks.sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private String saleStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM sales", String.class);
    }

    private long refundTotal() {
        return jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(amount), 0) FROM ledger_entries WHERE type = 'REFUND'", Long.class);
    }

    @Test
    void should_mark_partial_then_full_refund_with_ledger_entries_and_events() throws Exception {
        deliver(TestWebhooks.refundProcessed("rfnd_1", "pay_1", 2000));

        assertThat(saleStatus()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(refundTotal()).isEqualTo(-2000);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT data->>'amount_refunded' FROM events WHERE type = 'SALE_REFUNDED'", String.class))
                .isEqualTo("2000");

        deliver(TestWebhooks.refundProcessed("rfnd_2", "pay_1", 2900));

        assertThat(saleStatus()).isEqualTo("REFUNDED");
        assertThat(refundTotal()).isEqualTo(-4900);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM events WHERE type = 'SALE_REFUNDED'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void should_apply_each_refund_once_when_webhook_repeats() throws Exception {
        String body = TestWebhooks.refundProcessed("rfnd_1", "pay_1", 2000);

        deliver(body);
        deliver(body);

        assertThat(refundTotal()).isEqualTo(-2000);
        assertThat(jdbcTemplate.queryForObject("SELECT refunded_amount FROM sales", Long.class))
                .isEqualTo(2000L);
    }

    @Test
    void should_ignore_refund_for_unknown_payment() throws Exception {
        deliver(TestWebhooks.refundProcessed("rfnd_9", "pay_unknown", 2000));

        assertThat(refundTotal()).isZero();
        assertThat(saleStatus()).isEqualTo("PAID");
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.sales.RefundFlowIntegrationTest'`
Expected: FAIL — refunds are only logged, so status stays `PAID` and no `REFUND` entries exist.

- [ ] **Step 4: Implement**

`src/main/java/com/fluxpay/sales/service/SaleRefundService.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayRefund;

public interface SaleRefundService {

    /** Applies a gateway refund to its sale. Safe to call any number of times for the same refund. */
    void refund(Mode mode, GatewayRefund refund);
}
```

`src/main/java/com/fluxpay/sales/service/SaleRefundServiceImpl.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import com.fluxpay.ledger.service.LedgerService;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.GatewayRefund;
import com.fluxpay.payments.service.PaymentRecordService;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.persistence.SaleRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Spec §6 step 7. Refunds are initiated in the Razorpay dashboard; the platform fee is kept (TD-002). */
@Service
public class SaleRefundServiceImpl implements SaleRefundService {

    private static final Logger log = LoggerFactory.getLogger(SaleRefundServiceImpl.class);

    private final PaymentRecordService paymentRecords;
    private final SaleRepository sales;
    private final LedgerService ledgerService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public SaleRefundServiceImpl(
            PaymentRecordService paymentRecords,
            SaleRepository sales,
            LedgerService ledgerService,
            EventPublisher eventPublisher,
            Clock clock) {
        this.paymentRecords = paymentRecords;
        this.sales = sales;
        this.ledgerService = ledgerService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void refund(Mode mode, GatewayRefund refund) {
        Optional<RecordedPayment> payment = paymentRecords.findByGatewayPaymentId(refund.paymentId());
        if (payment.isEmpty()
                || payment.get().status() != PaymentStatus.CAPTURED
                || payment.get().mode() != mode) {
            log.warn("Refund {} for payment {} has no matching sale", refund.id(), refund.paymentId());
            return;
        }
        Optional<Sale> locked = sales.lockByPaymentId(payment.get().id());
        if (locked.isEmpty()) {
            log.warn("Refund {} for payment {} has no sale", refund.id(), refund.paymentId());
            return;
        }
        Sale sale = locked.get();
        TenantContext tenant = new TenantContext(sale.getMerchantId(), sale.getMode());
        boolean recorded =
                ledgerService.recordRefund(tenant, sale.getId(), refund.id(), refund.amount(), refund.currency());
        if (!recorded) {
            return;
        }
        sale.applyRefund(refund.amount(), Instant.now(clock));
        eventPublisher.publish(tenant, EventType.SALE_REFUNDED, SalePayloads.refunded(sale, refund.amount()));
    }
}
```

Replace `src/main/java/com/fluxpay/sales/service/GatewayEventRouter.java` with:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayEventHandler;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.GatewayRefund;
import org.springframework.stereotype.Component;

@Component
public class GatewayEventRouter implements GatewayEventHandler {

    private final SaleCaptureService captureService;
    private final SaleRefundService refundService;

    public GatewayEventRouter(SaleCaptureService captureService, SaleRefundService refundService) {
        this.captureService = captureService;
        this.refundService = refundService;
    }

    @Override
    public void onPaymentCaptured(Mode mode, GatewayPayment payment) {
        captureService.capture(mode, payment);
    }

    @Override
    public void onRefundProcessed(Mode mode, GatewayRefund refund) {
        refundService.refund(mode, refund);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.sales.*'` then `./gradlew test`
Expected: PASS.

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(sales): apply razorpay refunds once with ledger entries and sale.refunded events

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 11: Sales queries — API and dashboard

**Files:**
- Create: `src/main/java/com/fluxpay/sales/persistence/SaleSpecifications.java`
- Create: `src/main/java/com/fluxpay/sales/service/{SaleView,SaleDetailView,SaleFilter,SalesQueryService,SalesQueryServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/sales/api/{SaleApiController,DashboardSaleController,SaleResponse,SaleDetailResponse}.java`
- Test: `src/test/java/com/fluxpay/sales/SalesQueryIntegrationTest.java`

**Interfaces:**
- Consumes: `SaleRepository` (`JpaSpecificationExecutor`), `PaymentRecordService.findById`, `PageQuery`, `CursorPage`.
- Produces:
  - `record SaleFilter(String customerRef, UUID productId, SaleStatus status)` (nulls = no filter)
  - `record SaleView(UUID id, Mode mode, UUID productId, UUID checkoutSessionId, String customerRef, long amount, long refundedAmount, String currency, SaleStatus status, Map<String, String> metadata, Instant createdAt)`
  - `record SaleDetailView(SaleView sale, RecordedPayment payment)`
  - `interface SalesQueryService { CursorPage<SaleView> list(TenantContext, PageQuery, SaleFilter); SaleDetailView get(TenantContext, UUID saleId); }` — 404 `SALE_NOT_FOUND`
  - Routes: API key `GET /api/v1/sales?customer_ref=&product_id=&limit=&starting_after=`, `GET /api/v1/sales/{id}`; dashboard `GET /api/v1/dashboard/sales` (also `status=paid|partially_refunded|refunded`), `GET /api/v1/dashboard/sales/{id}` (with `payment`). Bad `product_id` filter → 400 `INVALID_PRODUCT_ID`; bad `status` → 400 `INVALID_STATUS`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/fluxpay/sales/SalesQueryIntegrationTest.java`:
```java
package com.fluxpay.sales;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import com.fluxpay.support.TestWebhooks;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class SalesQueryIntegrationTest extends AbstractIntegrationTest {

    private SignedIn owner;
    private String apiKey;
    private String proPack;
    private String elitePack;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        proPack = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        elitePack = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Elite Pack", 9900);
        TestMerchants.completeSale(mockMvc, apiKey, proPack, 4900, "u_1", "pay_1");
        TestMerchants.completeSale(mockMvc, apiKey, elitePack, 9900, "u_2", "pay_2");
        TestMerchants.completeSale(mockMvc, apiKey, proPack, 4900, "u_1", "pay_3");
    }

    private org.springframework.test.web.servlet.ResultActions apiGet(String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + apiKey));
    }

    @Test
    void should_list_sales_for_a_customer_newest_first() throws Exception {
        apiGet("/api/v1/sales?customer_ref=u_1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].customer_ref").value("u_1"))
                .andExpect(jsonPath("$.data[0].product_id").value(proPack))
                .andExpect(jsonPath("$.data[0].status").value("paid"))
                .andExpect(jsonPath("$.data[0].id").value(org.hamcrest.Matchers.startsWith("sale_")));
    }

    @Test
    void should_filter_by_product_and_paginate() throws Exception {
        apiGet("/api/v1/sales?product_id=" + elitePack).andExpect(jsonPath("$.data.length()").value(1));

        String first = apiGet("/api/v1/sales?limit=2")
                .andExpect(jsonPath("$.has_more").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        apiGet("/api/v1/sales?limit=2&starting_after=" + JsonPath.read(first, "$.cursor"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.has_more").value(false));
    }

    @Test
    void should_return_sale_and_payment_details_on_dashboard() throws Exception {
        String saleId = JsonPath.read(
                apiGet("/api/v1/sales?product_id=" + elitePack).andReturn().getResponse().getContentAsString(),
                "$.data[0].id");

        apiGet("/api/v1/sales/" + saleId).andExpect(jsonPath("$.amount").value(9900));
        mockMvc.perform(get("/api/v1/dashboard/sales/" + saleId).cookie(owner.session()))
                .andExpect(jsonPath("$.customer_ref").value("u_2"))
                .andExpect(jsonPath("$.payment.gateway_payment_id").value("pay_2"))
                .andExpect(jsonPath("$.payment.method").value("upi"))
                .andExpect(jsonPath("$.payment.gateway_fee").value(116));
    }

    @Test
    void should_filter_dashboard_sales_by_status() throws Exception {
        String refund = TestWebhooks.refundProcessed("rfnd_1", "pay_2", 9900);
        mockMvc.perform(post("/api/v1/gateway-webhooks/razorpay/test")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Razorpay-Signature", TestWebhooks.sign(refund))
                .content(refund));

        mockMvc.perform(get("/api/v1/dashboard/sales?status=refunded").cookie(owner.session()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].refunded_amount").value(9900));
    }

    @Test
    void should_reject_bad_filters_and_unknown_sales() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/sales?status=lost").cookie(owner.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS"));
        apiGet("/api/v1/sales?product_id=nope")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PRODUCT_ID"));
        apiGet("/api/v1/sales/sale_nope")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SALE_NOT_FOUND"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.sales.SalesQueryIntegrationTest'`
Expected: FAIL — `/api/v1/sales` routes return 404.

- [ ] **Step 3: Implement persistence and service**

`src/main/java/com/fluxpay/sales/persistence/SaleSpecifications.java`:
```java
package com.fluxpay.sales.persistence;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.domain.SaleStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class SaleSpecifications {

    private SaleSpecifications() {}

    public static Specification<Sale> forTenant(UUID merchantId, Mode mode) {
        return (root, query, cb) ->
                cb.and(cb.equal(root.get("merchantId"), merchantId), cb.equal(root.get("mode"), mode));
    }

    public static Specification<Sale> idBefore(UUID before) {
        return (root, query, cb) -> cb.lessThan(root.<UUID>get("id"), before);
    }

    public static Specification<Sale> customerRef(String customerRef) {
        return (root, query, cb) -> cb.equal(root.get("customerRef"), customerRef);
    }

    public static Specification<Sale> productId(UUID productId) {
        return (root, query, cb) -> cb.equal(root.get("productId"), productId);
    }

    public static Specification<Sale> status(SaleStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
```

`src/main/java/com/fluxpay/sales/service/SaleFilter.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.sales.domain.SaleStatus;
import java.util.UUID;

public record SaleFilter(String customerRef, UUID productId, SaleStatus status) {}
```

`src/main/java/com/fluxpay/sales/service/SaleView.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.domain.SaleStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SaleView(
        UUID id,
        Mode mode,
        UUID productId,
        UUID checkoutSessionId,
        String customerRef,
        long amount,
        long refundedAmount,
        String currency,
        SaleStatus status,
        Map<String, String> metadata,
        Instant createdAt) {

    static SaleView from(Sale sale) {
        return new SaleView(
                sale.getId(),
                sale.getMode(),
                sale.getProductId(),
                sale.getCheckoutSessionId(),
                sale.getCustomerRef(),
                sale.getAmount(),
                sale.getRefundedAmount(),
                sale.getCurrency(),
                sale.getStatus(),
                Map.copyOf(sale.getMetadata()),
                sale.getCreatedAt());
    }
}
```

`src/main/java/com/fluxpay/sales/service/SaleDetailView.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.payments.service.RecordedPayment;

public record SaleDetailView(SaleView sale, RecordedPayment payment) {}
```

`src/main/java/com/fluxpay/sales/service/SalesQueryService.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import java.util.UUID;

public interface SalesQueryService {

    CursorPage<SaleView> list(TenantContext tenant, PageQuery query, SaleFilter filter);

    /** SALE_NOT_FOUND (not found) unless the sale belongs to this tenant and mode. */
    SaleDetailView get(TenantContext tenant, UUID saleId);
}
```

`src/main/java/com/fluxpay/sales/service/SalesQueryServiceImpl.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.payments.service.PaymentRecordService;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.domain.Sale;
import com.fluxpay.sales.persistence.SaleRepository;
import com.fluxpay.sales.persistence.SaleSpecifications;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesQueryServiceImpl implements SalesQueryService {

    private final SaleRepository sales;
    private final PaymentRecordService paymentRecords;

    public SalesQueryServiceImpl(SaleRepository sales, PaymentRecordService paymentRecords) {
        this.sales = sales;
        this.paymentRecords = paymentRecords;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<SaleView> list(TenantContext tenant, PageQuery query, SaleFilter filter) {
        List<Specification<Sale>> specs = new ArrayList<>();
        specs.add(SaleSpecifications.forTenant(tenant.merchantId(), tenant.mode()));
        specs.add(SaleSpecifications.idBefore(query.before()));
        if (filter.customerRef() != null) {
            specs.add(SaleSpecifications.customerRef(filter.customerRef()));
        }
        if (filter.productId() != null) {
            specs.add(SaleSpecifications.productId(filter.productId()));
        }
        if (filter.status() != null) {
            specs.add(SaleSpecifications.status(filter.status()));
        }
        List<Sale> rows = sales.findBy(Specification.allOf(specs), fluent -> fluent.sortBy(
                        Sort.by(Sort.Direction.DESC, "id"))
                .limit(query.fetchSize())
                .all());
        return CursorPage.from(rows, query, SaleView::from, view -> PublicId.of(IdPrefix.SALE, view.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public SaleDetailView get(TenantContext tenant, UUID saleId) {
        Sale sale = sales.findByIdAndMerchantIdAndMode(saleId, tenant.merchantId(), tenant.mode())
                .orElseThrow(() -> FluxpayException.notFound("SALE_NOT_FOUND", "Sale not found"));
        RecordedPayment payment = paymentRecords.findById(sale.getPaymentId()).orElseThrow();
        return new SaleDetailView(SaleView.from(sale), payment);
    }
}
```

- [ ] **Step 4: Implement the API**

`src/main/java/com/fluxpay/sales/api/SaleResponse.java`:
```java
package com.fluxpay.sales.api;

import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.tenant.Mode;
import com.fluxpay.sales.domain.SaleStatus;
import com.fluxpay.sales.service.SaleView;
import java.time.Instant;
import java.util.Map;

public record SaleResponse(
        String id,
        Mode mode,
        String productId,
        String checkoutSessionId,
        String customerRef,
        long amount,
        long refundedAmount,
        String currency,
        SaleStatus status,
        Map<String, String> metadata,
        Instant createdAt) {

    public static SaleResponse from(SaleView view) {
        return new SaleResponse(
                PublicId.of(IdPrefix.SALE, view.id()),
                view.mode(),
                PublicId.of(IdPrefix.PRODUCT, view.productId()),
                PublicId.of(IdPrefix.CHECKOUT_SESSION, view.checkoutSessionId()),
                view.customerRef(),
                view.amount(),
                view.refundedAmount(),
                view.currency(),
                view.status(),
                view.metadata(),
                view.createdAt());
    }
}
```

`src/main/java/com/fluxpay/sales/api/SaleDetailResponse.java`:
```java
package com.fluxpay.sales.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fluxpay.payments.domain.PaymentStatus;
import com.fluxpay.payments.service.RecordedPayment;
import com.fluxpay.sales.service.SaleDetailView;

public record SaleDetailResponse(@JsonUnwrapped SaleResponse sale, Payment payment) {

    public record Payment(String gatewayPaymentId, String method, long gatewayFee, PaymentStatus status) {}

    public static SaleDetailResponse from(SaleDetailView view) {
        RecordedPayment payment = view.payment();
        return new SaleDetailResponse(
                SaleResponse.from(view.sale()),
                new Payment(payment.gatewayPaymentId(), payment.method(), payment.gatewayFee(), payment.status()));
    }
}
```

`src/main/java/com/fluxpay/sales/api/SaleApiController.java`:
```java
package com.fluxpay.sales.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.id.PublicId;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.sales.service.SaleFilter;
import com.fluxpay.sales.service.SalesQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Lets a merchant backend check what a customer bought (spec §4.3). */
@RestController
@RequestMapping("/api/v1/sales")
public class SaleApiController {

    private final SalesQueryService salesQueryService;

    public SaleApiController(SalesQueryService salesQueryService) {
        this.salesQueryService = salesQueryService;
    }

    @GetMapping
    public CursorPage<SaleResponse> list(
            TenantContext tenant,
            @RequestParam(name = "customer_ref", required = false) String customerRef,
            @RequestParam(name = "product_id", required = false) String productId,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.SALE, startingAfter, limit);
        SaleFilter filter = new SaleFilter(customerRef, parseProductFilter(productId), null);
        return salesQueryService.list(tenant, query, filter).map(SaleResponse::from);
    }

    @GetMapping("/{id}")
    public SaleResponse get(TenantContext tenant, @PathVariable String id) {
        return SaleResponse.from(salesQueryService.get(tenant, parseId(id)).sale());
    }

    static UUID parseId(String id) {
        return PublicId.parseOrNotFound(IdPrefix.SALE, id, "SALE_NOT_FOUND", "Sale not found");
    }

    static UUID parseProductFilter(String productId) {
        if (productId == null) {
            return null;
        }
        return PublicId.parse(IdPrefix.PRODUCT, productId)
                .orElseThrow(() -> FluxpayException.badRequest("INVALID_PRODUCT_ID", "product_id is not a prod_ id"));
    }
}
```

`src/main/java/com/fluxpay/sales/api/DashboardSaleController.java`:
```java
package com.fluxpay.sales.api;

import com.fluxpay.common.error.FluxpayException;
import com.fluxpay.common.id.IdPrefix;
import com.fluxpay.common.pagination.CursorPage;
import com.fluxpay.common.pagination.PageQuery;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.sales.domain.SaleStatus;
import com.fluxpay.sales.service.SaleFilter;
import com.fluxpay.sales.service.SalesQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/sales")
public class DashboardSaleController {

    private final SalesQueryService salesQueryService;

    public DashboardSaleController(SalesQueryService salesQueryService) {
        this.salesQueryService = salesQueryService;
    }

    @GetMapping
    public CursorPage<SaleResponse> list(
            TenantContext tenant,
            @RequestParam(name = "customer_ref", required = false) String customerRef,
            @RequestParam(name = "product_id", required = false) String productId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "starting_after", required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) {
        PageQuery query = PageQuery.of(IdPrefix.SALE, startingAfter, limit);
        SaleStatus parsedStatus = status == null
                ? null
                : SaleStatus.parse(status)
                        .orElseThrow(() -> FluxpayException.badRequest(
                                "INVALID_STATUS", "status must be paid, partially_refunded or refunded"));
        SaleFilter filter =
                new SaleFilter(customerRef, SaleApiController.parseProductFilter(productId), parsedStatus);
        return salesQueryService.list(tenant, query, filter).map(SaleResponse::from);
    }

    @GetMapping("/{id}")
    public SaleDetailResponse get(TenantContext tenant, @PathVariable String id) {
        return SaleDetailResponse.from(salesQueryService.get(tenant, SaleApiController.parseId(id)));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.sales.*'` then `./gradlew test`
Expected: PASS. If `FetchableFluentQuery.limit` is unavailable in the resolved Spring Data version, replace the `findBy(...)` call with `sales.findAll(Specification.allOf(specs), PageRequest.of(0, query.fetchSize(), Sort.by(Sort.Direction.DESC, "id"))).getContent()` and record the ruling.

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(sales): sales lookup for merchant backends and dashboard with filters and cursors

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 12: Scheduled jobs — expiry, reconciliation, idempotency cleanup; public rate limits

**Files:**
- Create: `src/main/java/com/fluxpay/common/config/JobsProperties.java`, `src/main/java/com/fluxpay/config/SchedulingConfig.java`
- Create: `src/main/java/com/fluxpay/sales/service/{CheckoutExpiryService,CheckoutExpiryServiceImpl,ReconciliationService,ReconciliationServiceImpl}.java`
- Create: `src/main/java/com/fluxpay/sales/jobs/{CheckoutExpiryJob,ReconciliationJob}.java`, `src/main/java/com/fluxpay/common/idempotency/IdempotencyCleanupJob.java`
- Modify: `src/main/resources/application.yml`, `src/test/resources/application-test.yml`, `.env.example`
- Test: `src/test/java/com/fluxpay/sales/JobsIntegrationTest.java`

**Interfaces:**
- Consumes: `CheckoutService.expireDue/findReconcilable`, `PaymentGateway.fetchOrderPayments`, `SaleCaptureService.capture`, `EventPublisher`, `IdempotencyService.deleteOlderThan`.
- Produces:
  - `record JobsProperties(boolean enabled, Duration expiryInterval, Duration reconciliationInterval, Duration reconciliationMinAge, Duration reconciliationWindow, Duration idempotencyRetention)` under `fluxpay.jobs`
  - `interface CheckoutExpiryService { int expireDue(); }` — expires ≤ 100 due sessions and emits one `checkout.expired` each, in one transaction
  - `interface ReconciliationService { int reconcile(); }` — for sessions with an order, status open/expired, created between `now - window` and `now - minAge`: fetches gateway payments (no transaction held) and calls `capture` for captured ones; returns sessions checked; one failing session does not stop the rest
  - Jobs only run when `fluxpay.jobs.enabled=true` (false in tests; tests call the services directly)
  - Rate-limit rules `public-write` (POST `/api/v1/public/**`, 30/min/IP) and `public-read` (GET, 300/min/IP)

- [ ] **Step 1: Configure**

Append to `src/main/resources/application.yml` under `fluxpay:`:
```yaml
  jobs:
    enabled: ${FLUXPAY_JOBS_ENABLED:true}
    expiry-interval: 1m
    reconciliation-interval: 5m
    reconciliation-min-age: 10m
    reconciliation-window: 24h
    idempotency-retention: 24h
```

In the same file, extend `fluxpay.rate-limit.rules` (keep the existing `auth` rule first):
```yaml
      - name: public-write
        method: POST
        paths: [/api/v1/public/**]
        capacity: 30
        period: 1m
      - name: public-read
        method: GET
        paths: [/api/v1/public/**]
        capacity: 300
        period: 1m
```

Append to `src/test/resources/application-test.yml` under `fluxpay:`:
```yaml
  jobs:
    enabled: false
```

Append to `.env.example`:
```bash

# Background jobs (checkout expiry, Razorpay reconciliation, idempotency cleanup). Run on exactly one instance.
FLUXPAY_JOBS_ENABLED=true
```

- [ ] **Step 2: Write the failing test**

`src/test/java/com/fluxpay/sales/JobsIntegrationTest.java`:
```java
package com.fluxpay.sales;

import static org.assertj.core.api.Assertions.assertThat;

import com.fluxpay.common.tenant.Mode;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.sales.service.CheckoutExpiryService;
import com.fluxpay.sales.service.ReconciliationService;
import com.fluxpay.support.AbstractIntegrationTest;
import com.fluxpay.support.TestMerchants;
import com.fluxpay.support.TestMerchants.SignedIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class JobsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CheckoutExpiryService expiryService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String sessionId;

    @BeforeEach
    void setUp() throws Exception {
        SignedIn owner = TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com");
        String productId = TestMerchants.createProduct(mockMvc, owner, Mode.TEST, "Pro Pack", 4900);
        String apiKey = TestMerchants.createApiKey(mockMvc, owner, Mode.TEST);
        sessionId = TestMerchants.createCheckoutSession(mockMvc, apiKey, productId, "u_123");
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    @Test
    void should_expire_due_sessions_once_and_emit_checkout_expired() {
        jdbcTemplate.update("UPDATE checkout_sessions SET expires_at = now() - interval '1 minute'");

        assertThat(expiryService.expireDue()).isEqualTo(1);
        assertThat(expiryService.expireDue()).isZero();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("EXPIRED");
        assertThat(count("SELECT count(*) FROM events WHERE type = 'CHECKOUT_EXPIRED'")).isEqualTo(1);
    }

    @Test
    void should_not_expire_sessions_that_are_still_valid() {
        assertThat(expiryService.expireDue()).isZero();
    }

    @Test
    void should_record_missed_capture_when_reconciling_old_sessions() throws Exception {
        String orderId = TestMerchants.pay(mockMvc, sessionId);
        paymentGateway.addPayment(new GatewayPayment("pay_r", orderId, 4900, "INR", "captured", "upi", 116));
        jdbcTemplate.update("UPDATE checkout_sessions SET created_at = now() - interval '20 minutes'");

        assertThat(reconciliationService.reconcile()).isEqualTo(1);
        reconciliationService.reconcile();

        assertThat(count("SELECT count(*) FROM sales")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM checkout_sessions", String.class))
                .isEqualTo("COMPLETED");
    }

    @Test
    void should_skip_recent_sessions_and_ignore_uncaptured_payments() throws Exception {
        String orderId = TestMerchants.pay(mockMvc, sessionId);
        paymentGateway.addPayment(new GatewayPayment("pay_f", orderId, 4900, "INR", "failed", "upi", 0));

        assertThat(reconciliationService.reconcile()).isZero();

        jdbcTemplate.update("UPDATE checkout_sessions SET created_at = now() - interval '20 minutes'");
        reconciliationService.reconcile();
        assertThat(count("SELECT count(*) FROM sales")).isZero();
    }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests 'com.fluxpay.sales.JobsIntegrationTest'`
Expected: FAIL — compilation errors.

- [ ] **Step 4: Implement**

`src/main/java/com/fluxpay/common/config/JobsProperties.java`:
```java
package com.fluxpay.common.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fluxpay.jobs")
public record JobsProperties(
        boolean enabled,
        @NotNull Duration expiryInterval,
        @NotNull Duration reconciliationInterval,
        @NotNull Duration reconciliationMinAge,
        @NotNull Duration reconciliationWindow,
        @NotNull Duration idempotencyRetention) {}
```

`src/main/java/com/fluxpay/config/SchedulingConfig.java`:
```java
package com.fluxpay.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs run on one instance only; set FLUXPAY_JOBS_ENABLED=false on any additional instance. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class SchedulingConfig {}
```

`src/main/java/com/fluxpay/sales/service/CheckoutExpiryService.java`:
```java
package com.fluxpay.sales.service;

public interface CheckoutExpiryService {

    /** Expires due open sessions and emits checkout.expired for each. Returns how many expired. */
    int expireDue();
}
```

`src/main/java/com/fluxpay/sales/service/CheckoutExpiryServiceImpl.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.tenant.TenantContext;
import com.fluxpay.events.domain.EventType;
import com.fluxpay.events.service.EventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutExpiryServiceImpl implements CheckoutExpiryService {

    static final int BATCH_SIZE = 100;

    private final CheckoutService checkoutService;
    private final EventPublisher eventPublisher;
    private final Clock clock;

    public CheckoutExpiryServiceImpl(CheckoutService checkoutService, EventPublisher eventPublisher, Clock clock) {
        this.checkoutService = checkoutService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int expireDue() {
        List<CheckoutSessionView> expired = checkoutService.expireDue(Instant.now(clock), BATCH_SIZE);
        for (CheckoutSessionView session : expired) {
            eventPublisher.publish(
                    new TenantContext(session.merchantId(), session.mode()),
                    EventType.CHECKOUT_EXPIRED,
                    SalePayloads.expired(session));
        }
        return expired.size();
    }
}
```

`src/main/java/com/fluxpay/sales/service/ReconciliationService.java`:
```java
package com.fluxpay.sales.service;

public interface ReconciliationService {

    /** Asks the gateway about older sessions with an order, recording any captured payment we missed. */
    int reconcile();
}
```

`src/main/java/com/fluxpay/sales/service/ReconciliationServiceImpl.java`:
```java
package com.fluxpay.sales.service;

import com.fluxpay.checkout.service.CheckoutService;
import com.fluxpay.checkout.service.CheckoutSessionView;
import com.fluxpay.common.config.JobsProperties;
import com.fluxpay.payments.service.GatewayPayment;
import com.fluxpay.payments.service.PaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Spec §6 step 5. Not transactional: gateway calls never run inside a transaction; capture opens its own. */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);

    private final CheckoutService checkoutService;
    private final PaymentGateway gateway;
    private final SaleCaptureService captureService;
    private final JobsProperties properties;
    private final Clock clock;

    public ReconciliationServiceImpl(
            CheckoutService checkoutService,
            PaymentGateway gateway,
            SaleCaptureService captureService,
            JobsProperties properties,
            Clock clock) {
        this.checkoutService = checkoutService;
        this.gateway = gateway;
        this.captureService = captureService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public int reconcile() {
        Instant now = Instant.now(clock);
        List<CheckoutSessionView> candidates = checkoutService.findReconcilable(
                now.minus(properties.reconciliationWindow()),
                now.minus(properties.reconciliationMinAge()),
                BATCH_SIZE);
        for (CheckoutSessionView session : candidates) {
            try {
                for (GatewayPayment payment : gateway.fetchOrderPayments(session.mode(), session.gatewayOrderId())) {
                    if (payment.isCaptured()) {
                        captureService.capture(session.mode(), payment);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Reconciliation failed for checkout session {}", session.id(), e);
            }
        }
        return candidates.size();
    }
}
```

`src/main/java/com/fluxpay/sales/jobs/CheckoutExpiryJob.java`:
```java
package com.fluxpay.sales.jobs;

import com.fluxpay.sales.service.CheckoutExpiryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class CheckoutExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutExpiryJob.class);

    private final CheckoutExpiryService expiryService;

    public CheckoutExpiryJob(CheckoutExpiryService expiryService) {
        this.expiryService = expiryService;
    }

    @Scheduled(fixedDelayString = "${fluxpay.jobs.expiry-interval}")
    public void run() {
        int expired = expiryService.expireDue();
        if (expired > 0) {
            log.info("Expired {} checkout sessions", expired);
        }
    }
}
```

`src/main/java/com/fluxpay/sales/jobs/ReconciliationJob.java`:
```java
package com.fluxpay.sales.jobs;

import com.fluxpay.sales.service.ReconciliationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;

    public ReconciliationJob(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(
            fixedDelayString = "${fluxpay.jobs.reconciliation-interval}",
            initialDelayString = "${fluxpay.jobs.reconciliation-interval}")
    public void run() {
        reconciliationService.reconcile();
    }
}
```

`src/main/java/com/fluxpay/common/idempotency/IdempotencyCleanupJob.java`:
```java
package com.fluxpay.common.idempotency;

import com.fluxpay.common.config.JobsProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fluxpay.jobs", name = "enabled", havingValue = "true")
public class IdempotencyCleanupJob {

    private final IdempotencyService idempotencyService;
    private final JobsProperties properties;
    private final Clock clock;

    public IdempotencyCleanupJob(IdempotencyService idempotencyService, JobsProperties properties, Clock clock) {
        this.idempotencyService = idempotencyService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void run() {
        idempotencyService.deleteOlderThan(Instant.now(clock).minus(properties.idempotencyRetention()));
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests 'com.fluxpay.sales.JobsIntegrationTest'` then `./gradlew test`
Expected: PASS (no job beans exist in tests because `fluxpay.jobs.enabled=false`).

- [ ] **Step 6: Commit and push**

```bash
./gradlew spotlessApply
git add -A
git commit -m "feat(sales): checkout expiry, razorpay reconciliation and idempotency cleanup jobs with public rate limits

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```

---

### Task 13: Tenant isolation for the new endpoints, ProjectOS records, real Razorpay smoke test

**Files:**
- Modify: `src/test/java/com/fluxpay/apikeys/TenantIsolationIntegrationTest.java`
- Create: `.engineering/memory/adr/ADR-007-snake-case-json.md`, `.engineering/memory/adr/ADR-008-per-mode-gateway-credentials.md`
- Modify: `.engineering/memory/features.yaml`, `.engineering/memory/tech-debt/registry.yaml`, `README.md`

**Interfaces:**
- Consumes: every route added in Tasks 2–11.
- Produces: the isolation suite covers products, payment links, checkout sessions, sales and balance.

- [ ] **Step 1: Extend the isolation suite**

Add these imports to `src/test/java/com/fluxpay/apikeys/TenantIsolationIntegrationTest.java`: `static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch`, `org.springframework.http.MediaType`. Add these tests before the final `}`:
```java
    @Test
    void should_hide_other_merchants_products_from_dashboard_and_api() throws Exception {
        String productOfB = TestMerchants.createProduct(mockMvc, merchantB, Mode.TEST, "B Pack", 4900);
        String keyOfA = TestMerchants.createApiKey(mockMvc, merchantA, Mode.TEST);

        mockMvc.perform(get("/api/v1/dashboard/products/" + productOfB).cookie(merchantA.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/dashboard/products/" + productOfB)
                        .with(csrf())
                        .cookie(merchantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/" + productOfB).header("Authorization", "Bearer " + keyOfA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + keyOfA))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void should_not_let_a_merchant_sell_or_link_another_merchants_product() throws Exception {
        String productOfB = TestMerchants.createProduct(mockMvc, merchantB, Mode.TEST, "B Pack", 4900);
        String keyOfA = TestMerchants.createApiKey(mockMvc, merchantA, Mode.TEST);

        mockMvc.perform(post("/api/v1/checkout_sessions")
                        .header("Authorization", "Bearer " + keyOfA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\",\"success_url\":\"https://a.com\",\"cancel_url\":\"https://a.com\"}"
                                .formatted(productOfB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/dashboard/payment_links")
                        .with(csrf())
                        .cookie(merchantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\":\"%s\"}".formatted(productOfB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_hide_other_merchants_sessions_sales_and_balance() throws Exception {
        String productOfB = TestMerchants.createProduct(mockMvc, merchantB, Mode.TEST, "B Pack", 4900);
        String keyOfB = TestMerchants.createApiKey(mockMvc, merchantB, Mode.TEST);
        String keyOfA = TestMerchants.createApiKey(mockMvc, merchantA, Mode.TEST);
        String sessionOfB = TestMerchants.completeSale(mockMvc, keyOfB, productOfB, 4900, "u_b", "pay_b");
        String saleOfB = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + keyOfB))
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.data[0].id");

        mockMvc.perform(get("/api/v1/checkout_sessions/" + sessionOfB).header("Authorization", "Bearer " + keyOfA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sales/" + saleOfB).header("Authorization", "Bearer " + keyOfA))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/sales").header("Authorization", "Bearer " + keyOfA))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/v1/dashboard/sales/" + saleOfB).cookie(merchantA.session()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(merchantA.session()))
                .andExpect(jsonPath("$.available").value(0));
        mockMvc.perform(get("/api/v1/dashboard/balance").cookie(merchantB.session()))
                .andExpect(jsonPath("$.gross_sales").value(4900));
    }
```

Run: `./gradlew test --tests 'com.fluxpay.apikeys.TenantIsolationIntegrationTest'`
Expected: PASS (these exercise already-built scoping; to confirm they can fail, temporarily change `ProductServiceImpl.find` to `findById`-style lookup ignoring merchant — the first test must fail — then revert).

- [ ] **Step 2: Record decisions and features**

`.engineering/memory/adr/ADR-007-snake-case-json.md`:
```markdown
# ADR-007: snake_case JSON everywhere

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md §4, §9

## Context
ProjectOS `standards/api.md` shows camelCase examples (`traceId`, `hasMore`). The spec's merchant API is Stripe-style (`product_id`, `customer_ref`, `success_url`).

## Decision
All JSON — requests, responses, error envelopes and webhook payloads — uses snake_case via the global Jackson naming strategy. The error envelope field is `trace_id`; list responses use `has_more`.

## Consequences
One convention for merchants to learn. Deviates from the ProjectOS example field names; the ProjectOS rule (consistent envelope, cursor pagination) is otherwise followed.
```

`.engineering/memory/adr/ADR-008-per-mode-gateway-credentials.md`:
```markdown
# ADR-008: Per-mode Razorpay credentials and webhook URLs

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md §3, §6, §8

## Context
Razorpay test and live modes have separate API keys and separate webhook configurations and secrets. FluxPay sessions carry a mode.

## Decision
Credentials are configured per mode (`RAZORPAY_TEST_*` required, `RAZORPAY_LIVE_*` optional and all-or-nothing). Each mode has its own webhook URL, `/api/v1/gateway-webhooks/razorpay/{test|live}`, verified with that mode's secret. Orders for a session are created with the session's mode credentials. Integration tests replace the gateway with an in-memory fake that signs webhooks with the same HMAC scheme.

## Consequences
Live mode can stay disabled until go-live (`GATEWAY_NOT_CONFIGURED`). A webhook can never be accepted for the wrong mode. Two webhook entries must be configured in the Razorpay dashboard.
```

Append to `.engineering/memory/features.yaml` (under `features:`):
```yaml
  - name: "Products and payment links"
    description: "One-time products (dashboard CRUD, API read) and shareable /l/<slug> payment links with safe redirects."
    modules: ["src/main/java/com/fluxpay/catalog"]
  - name: "Checkout sessions and hosted checkout API"
    description: "Idempotent POST /api/v1/checkout_sessions, price snapshot, 30-minute expiry, public view/pay endpoints, payment-link sessions."
    modules: ["src/main/java/com/fluxpay/checkout", "src/main/java/com/fluxpay/common/idempotency"]
  - name: "Razorpay payments"
    description: "Per-mode Razorpay orders, signed webhook intake, payment records, reconciliation of missed webhooks."
    modules: ["src/main/java/com/fluxpay/payments", "src/main/java/com/fluxpay/sales/service"]
  - name: "Sales, refunds and ledger"
    description: "Exactly-once sale per captured payment, refunds from Razorpay, append-only ledger with platform and gateway fees, balance."
    modules: ["src/main/java/com/fluxpay/sales", "src/main/java/com/fluxpay/ledger"]
  - name: "Event outbox"
    description: "checkout.completed, checkout.expired and sale.refunded written transactionally to events (delivery in Plan 3)."
    modules: ["src/main/java/com/fluxpay/events"]
```

Append to `.engineering/memory/tech-debt/registry.yaml`:
```yaml
- id: TD-007
  date: "2026-10-02"
  description: "Razorpay calls use timeouts but no circuit breaker (ProjectOS backend standard #13)."
  impact: "Low - a Razorpay outage makes each pay click wait up to the read timeout."
  location: "src/main/java/com/fluxpay/payments/service/RazorpayGateway.java"
  status: "open"
- id: TD-008
  date: "2026-10-02"
  description: "Payments flagged AMOUNT_MISMATCH or DUPLICATE are only logged; there is no admin view or automatic refund."
  impact: "Medium - customer money held without a sale until someone refunds it in Razorpay."
  location: "src/main/java/com/fluxpay/sales/service/SaleCaptureServiceImpl.java"
  status: "open"
```

Append to `README.md`:
```markdown

## Razorpay setup

1. Put test-mode keys in `.env` (`RAZORPAY_TEST_KEY_ID`, `RAZORPAY_TEST_KEY_SECRET`).
2. Razorpay Dashboard → Test mode → Webhooks → add `https://<backend>/api/v1/gateway-webhooks/razorpay/test` with events `payment.captured` and `refund.processed`; copy its secret to `RAZORPAY_TEST_WEBHOOK_SECRET`.
3. Enable automatic capture for payments (Account & Settings → Payment capture) so payments reach `captured`.
4. For live mode repeat with `RAZORPAY_LIVE_*` and the `/razorpay/live` URL.
```

- [ ] **Step 3: Full verification**

Run: `./gradlew spotlessApply && ./gradlew spotlessCheck build`
Expected: `BUILD SUCCESSFUL`; architecture guards pass (new modules respect `architecture.yaml`).

- [ ] **Step 4: Smoke-test real Razorpay order creation (test mode, local throwaway database)**

```bash
docker run -d --name fluxpay-smoke-db -p 55432:5432 -e POSTGRES_DB=fluxpay -e POSTGRES_USER=fluxpay -e POSTGRES_PASSWORD=smoke postgres:18-alpine
(set -a; source .env; set +a; \
 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/fluxpay SPRING_DATASOURCE_USERNAME=fluxpay \
 SPRING_DATASOURCE_PASSWORD=smoke FLUXPAY_JOBS_ENABLED=false ./gradlew bootRun > /tmp/fluxpay-smoke.log 2>&1 &)
```
Wait for `GET localhost:8080/health/ready` = UP, then with a cookie jar: `GET /api/v1/auth/csrf` → `POST /api/v1/auth/signup` → `POST /api/v1/dashboard/products` `{"name":"Smoke","amount":100}` → `POST /api/v1/dashboard/api_keys` → `POST /api/v1/checkout_sessions` with the key → `POST /api/v1/public/checkout_sessions/{id}/pay`.
Expected: the pay response has `order_id` starting `order_` (a real Razorpay test order) and `key_id` starting `rzp_test_`.
Then stop the app (`pkill -f FluxpayApplication; ./gradlew --stop`) and `docker rm -f fluxpay-smoke-db`. Neon is not touched.

- [ ] **Step 5: Commit and push**

```bash
git add -A
git commit -m "test(isolation): cover catalog, checkout and sales; record plan 2 decisions in projectos memory

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push origin main
```
