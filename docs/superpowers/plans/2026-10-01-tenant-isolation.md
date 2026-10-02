# Tenant Isolation (Per-Merchant Ownership) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every merchant-scoped API endpoint only reads or changes the calling merchant's own data. The merchant comes from the login token, never from the URL or request body, so Fluxpay can safely host many independent clients (Jextter and others), like Instamojo.

**Architecture:** `JwtAuthenticationFilter` already puts the token's `merchantId` claim on the request, and `CurrentMerchant` (in `shared/security`) reads it. Each controller enforces ownership with three rules:

1. **List endpoints** (`/merchant/{merchantId}`) call `CurrentMerchant.assertIs`. A foreign ID returns 403.
2. **Create endpoints** overwrite the body's `merchantId` with the token's merchant.
3. **By-ID endpoints** load the resource and call `CurrentMerchant.requireOwner`. A foreign resource returns 404, so nobody can probe which IDs exist.

Services stay unscoped. Public flows (checkout, the pay page, gateway callbacks) and internal callers keep using them unchanged.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security (JWT, stateless), Gradle multi-module, JUnit 5, Mockito, `spring-test` (`MockHttpServletRequest`).

**Spec:** No separate spec doc; the requirements are below. Background: finding #4 and proposal P0-1 in `../FLUXPAY_REVIEW_REPORT.md` (workspace root).

Requirements:
- **R1:** A merchant can only list, read, create, change or delete resources whose `merchantId` is their own.
- **R2:** The merchant identity comes from the JWT. Any `merchantId` in a request body is ignored or overwritten.
- **R3:** The dashboard (`fluxpay-frontend`) keeps working unchanged. It sends `getMerchantId()` from localStorage in paths and bodies, which is always the logged-in merchant.
- **R4:** Public customer flows keep working without login:
  - `GET /api/v1/products/{id}` (pay page)
  - `POST /api/v1/checkout/sessions`
  - `GET /api/v1/checkout/sessions/{id}`
  - `POST /api/v1/payments/razorpay/verify`
  - `POST /api/v1/webhooks/razorpay`
- **R5:** No database migrations. No changes to the response JSON shapes.

## Global Constraints

- Work in `fluxpay-backend/` on a new branch `feat/tenant-isolation`.
- Use the existing helper `com.fluxpay.shared.security.CurrentMerchant`. Don't add a second mechanism such as Spring `@PreAuthorize` or argument resolvers.
- A foreign list request throws `com.fluxpay.shared.exception.ForbiddenException` (HTTP 403, code `FORBIDDEN`).
- A foreign single resource throws `com.fluxpay.shared.exception.ResourceNotFoundException` (HTTP 404, code `NOT_FOUND`).
- Every controller method that needs the merchant takes `HttpServletRequest httpRequest` as its last parameter, matching `WebhookController`.
- Remove `@NotNull` only from **`merchantId`** fields in request DTOs. Keep it on every other field, e.g. `ApplicationCreateRequest.organizationId`.
- Modules `analytics`, `audit-logs` and `notifications` are **not** in the running app (commented out in `apps/fluxpay-api/build.gradle.kts`). Leave them alone.
- Run tests with `./gradlew :<module path>:test` from `fluxpay-backend/`. Java 21 toolchain; Gradle downloads it if needed.

## Review Focus

1. **Token without a `merchantId` claim** (malformed or forged-but-signed token). Expect 403, not a 500. *Pinned in Task 1:* `requireFailsWhenTokenHasNoMerchant`.
2. **Pay page product fetch by a logged-out customer** (`GET /api/v1/products/{id}`). Must still return 200. *Pinned in Task 9, step 4 (curl).*
3. **Logged-out request to `GET /api/v1/products/merchant/{id}`.** It used to be public and must now be rejected. *Pinned in Task 9, step 4 (curl), and Task 2's `SecurityConfig` change.*
4. **Delete or cancel on another merchant's resource.** Must return 404 **and must not change anything.** *Pinned in each task with `verify(service, never())`: Tasks 2, 4, 5, 6.*
5. **Body `merchantId` pointing at another merchant on create.** It must be silently replaced with the caller's ID; the request must not be rejected (the dashboard always sends one). *Pinned in each task:* `create...UsesTokenMerchant`.

---

## File Structure

| File | Responsibility | Task |
|---|---|---|
| `shared/security/src/main/java/com/fluxpay/shared/security/CurrentMerchant.java` | Read the merchant from the request; ownership assertions | 1 |
| `shared/security/src/test/java/com/fluxpay/shared/security/CurrentMerchantTest.java` | Helper tests | 1 |
| `shared/security/src/main/java/com/fluxpay/shared/security/SecurityConfig.java` | Make only `GET /products/{id}` public | 2 |
| `modules/product-catalog/...controller/{Product,Asset}Controller.java` + tests | Products and assets | 2 |
| `modules/orders/...controller/OrderController.java`, `modules/payments/...controller/PaymentController.java` + tests | Orders and payments | 3 |
| `modules/coupons/...controller/CouponController.java` + test | Coupons | 4 |
| `modules/invoices/...controller/InvoiceController.java`, `modules/subscriptions/...controller/SubscriptionController.java` + tests | Invoices and subscriptions | 5 |
| `modules/api-keys/...` (controller, service) + test, `modules/customers/...controller/CustomerController.java` + test | API keys and customers | 6 |
| `modules/organization-management/...controller/OrganizationController.java` + test | Organizations and applications | 7 |
| `modules/merchant-management/...controller/MerchantController.java` + test | Merchant profile | 8 |
| Each touched module's `build.gradle.kts` | Add `api(project(":shared:security"))` | 2–7 |
| `FLUXPAY_REVIEW_REPORT.md` (workspace root) | Mark P0-1 done | 9 |

Every controller test uses this helper, defined in each test class (it's 4 lines; copying beats a shared test fixture module):

```java
private static MockHttpServletRequest as(UUID merchantId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
    return request;
}
```

---

### Task 1: Ownership helpers in `CurrentMerchant`

**Files:**
- Modify: `shared/security/src/main/java/com/fluxpay/shared/security/CurrentMerchant.java`
- Create: `shared/security/src/test/java/com/fluxpay/shared/security/CurrentMerchantTest.java`

**Interfaces:**
- Produces:
  - `public static final String CurrentMerchant.REQUEST_ATTRIBUTE` (was package-private)
  - `UUID CurrentMerchant.require(HttpServletRequest)`
  - `void CurrentMerchant.assertIs(HttpServletRequest, UUID merchantId)` (throws `ForbiddenException`)
  - **new** `void CurrentMerchant.requireOwner(HttpServletRequest, UUID ownerMerchantId, String resource, UUID resourceId)` (throws `ResourceNotFoundException`)

- [ ] **Step 1: Create the branch**

```bash
cd fluxpay-backend && git checkout -b feat/tenant-isolation
```

- [ ] **Step 2: Write the failing test**

Create `shared/security/src/test/java/com/fluxpay/shared/security/CurrentMerchantTest.java`:

```java
package com.fluxpay.shared.security;

import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrentMerchantTest {

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void requireReturnsTokenMerchant() {
        assertEquals(me, CurrentMerchant.require(as(me)));
    }

    @Test
    void requireFailsWhenTokenHasNoMerchant() {
        assertThrows(ForbiddenException.class, () -> CurrentMerchant.require(new MockHttpServletRequest()));
    }

    @Test
    void assertIsRejectsOtherMerchant() {
        assertDoesNotThrow(() -> CurrentMerchant.assertIs(as(me), me));
        assertThrows(ForbiddenException.class, () -> CurrentMerchant.assertIs(as(me), other));
    }

    @Test
    void requireOwnerHidesOtherMerchantsResourceAsNotFound() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> CurrentMerchant.requireOwner(as(me), me, "Product", id));
        assertThrows(ResourceNotFoundException.class, () -> CurrentMerchant.requireOwner(as(me), other, "Product", id));
        assertThrows(ResourceNotFoundException.class, () -> CurrentMerchant.requireOwner(as(me), null, "Product", id));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :shared:security:test --tests 'com.fluxpay.shared.security.CurrentMerchantTest'`
Expected: compilation FAIL. `REQUEST_ATTRIBUTE` isn't public and `requireOwner` doesn't exist.

- [ ] **Step 4: Implement**

Replace the body of `CurrentMerchant.java` with:

```java
package com.fluxpay.shared.security;

import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * The merchant id from the caller's JWT (set by JwtAuthenticationFilter).
 * Controllers use it so one merchant can never read or change another merchant's data.
 */
public final class CurrentMerchant {

    public static final String REQUEST_ATTRIBUTE = "fluxpay.merchantId";

    private CurrentMerchant() {
    }

    public static UUID require(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value == null) {
            throw new ForbiddenException("No merchant in token");
        }
        return UUID.fromString(value.toString());
    }

    /** For list endpoints addressed by merchant id: a different merchant is a 403. */
    public static void assertIs(HttpServletRequest request, UUID merchantId) {
        if (!require(request).equals(merchantId)) {
            throw new ForbiddenException("Not allowed to access another merchant's resources");
        }
    }

    /** For a single resource: someone else's resource looks exactly like a missing one (404). */
    public static void requireOwner(HttpServletRequest request, UUID ownerMerchantId, String resource, UUID resourceId) {
        if (ownerMerchantId == null || !require(request).equals(ownerMerchantId)) {
            throw new ResourceNotFoundException(resource, resourceId.toString());
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :shared:security:test :modules:webhooks:test`
Expected: PASS. Webhooks still compile against the now-public constant.

- [ ] **Step 6: Commit**

```bash
git add shared/security
git commit -m "feat(security): add CurrentMerchant.requireOwner for per-merchant resource checks"
```

---

### Task 2: Products and assets (plus the public-route fix)

**Files:**
- Modify: `modules/product-catalog/build.gradle.kts`
- Modify: `modules/product-catalog/src/main/java/com/fluxpay/product/controller/ProductController.java`
- Modify: `modules/product-catalog/src/main/java/com/fluxpay/product/controller/AssetController.java`
- Modify: `modules/product-catalog/src/main/java/com/fluxpay/product/dto/ProductCreateRequest.java` (remove `@NotNull` on `merchantId`)
- Modify: `modules/product-catalog/src/main/java/com/fluxpay/product/dto/CreateAssetRequest.java` (remove `@NotNull` on `merchantId`)
- Modify: `shared/security/src/main/java/com/fluxpay/shared/security/SecurityConfig.java`
- Create: `modules/product-catalog/src/test/java/com/fluxpay/product/controller/ProductControllerTest.java`
- Create: `modules/product-catalog/src/test/java/com/fluxpay/product/controller/AssetControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant.require / assertIs / requireOwner` (Task 1)
- Existing services, unchanged:
  - `ProductService.createProduct(ProductCreateRequest)`
  - `ProductService.getProduct(UUID): ProductDto` (`@Cacheable`, also used by checkout)
  - `ProductService.getActiveProductsByMerchant(UUID)`
  - `ProductService.deactivateProduct(UUID)`
  - `AssetService.createAsset(CreateAssetRequest)`
  - `AssetService.getAssetsByMerchant(UUID)`
  - `AssetService.getAsset(UUID): AssetDto`

- [ ] **Step 1: Add the dependency**

In `modules/product-catalog/build.gradle.kts`, add this line inside `dependencies { }`:

```kotlin
    api(project(":shared:security"))
```

- [ ] **Step 2: Write the failing tests**

`ProductControllerTest.java`:

```java
package com.fluxpay.product.controller;

import com.fluxpay.product.dto.ProductCreateRequest;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductController controller = new ProductController(productService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createProductUsesTokenMerchant() {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setMerchantId(other);
        when(productService.createProduct(any())).thenReturn(ProductDto.builder().build());

        controller.createProduct(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void listingAnotherMerchantsProductsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getProductsByMerchant(other, as(me)));
        verify(productService, never()).getActiveProductsByMerchant(any());
    }

    @Test
    void deactivatingAnotherMerchantsProductIsNotFoundAndChangesNothing() {
        UUID id = UUID.randomUUID();
        when(productService.getProduct(id)).thenReturn(ProductDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.deactivateProduct(id, as(me)));
        verify(productService, never()).deactivateProduct(any());
    }

    @Test
    void deactivatingOwnProductWorks() {
        UUID id = UUID.randomUUID();
        when(productService.getProduct(id)).thenReturn(ProductDto.builder().id(id).merchantId(me).build());

        controller.deactivateProduct(id, as(me));

        verify(productService).deactivateProduct(id);
    }
}
```

`AssetControllerTest.java`:

```java
package com.fluxpay.product.controller;

import com.fluxpay.product.dto.AssetDto;
import com.fluxpay.product.dto.CreateAssetRequest;
import com.fluxpay.product.service.AssetService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetControllerTest {

    private final AssetService assetService = mock(AssetService.class);
    private final AssetController controller = new AssetController(assetService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createAssetUsesTokenMerchant() {
        CreateAssetRequest request = new CreateAssetRequest();
        request.setMerchantId(other);
        when(assetService.createAsset(any())).thenReturn(AssetDto.builder().build());

        controller.createAsset(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void listingAnotherMerchantsAssetsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getMerchantAssets(other, as(me)));
    }

    @Test
    void readingAnotherMerchantsAssetIsNotFound() {
        UUID id = UUID.randomUUID();
        when(assetService.getAsset(id)).thenReturn(AssetDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getAsset(id, as(me)));
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :modules:product-catalog:test`
Expected: compilation FAIL. The controller methods don't accept `HttpServletRequest` yet.

- [ ] **Step 4: Implement the controllers**

Replace `ProductController.java`:

```java
package com.fluxpay.product.controller;

import com.fluxpay.product.dto.ProductCreateRequest;
import com.fluxpay.product.dto.ProductDto;
import com.fluxpay.product.service.ProductService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductDto>> createProduct(@Valid @RequestBody ProductCreateRequest request,
                                                                 HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        ProductDto product = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(product, "Product created successfully"));
    }

    // Public (pay page): no ownership check by design - see SecurityConfig
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDto>> getProduct(@PathVariable UUID id) {
        ProductDto product = productService.getProduct(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<ProductDto>>> getProductsByMerchant(@PathVariable UUID merchantId,
                                                                               HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<ProductDto> products = productService.getActiveProductsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable UUID id, HttpServletRequest httpRequest) {
        CurrentMerchant.requireOwner(httpRequest, productService.getProduct(id).getMerchantId(), "Product", id);
        productService.deactivateProduct(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Product deactivated successfully"));
    }
}
```

Replace `AssetController.java`:

```java
package com.fluxpay.product.controller;

import com.fluxpay.product.dto.AssetDto;
import com.fluxpay.product.dto.CreateAssetRequest;
import com.fluxpay.product.service.AssetService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @PostMapping
    public ResponseEntity<ApiResponse<AssetDto>> createAsset(@Valid @RequestBody CreateAssetRequest request,
                                                             HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        AssetDto asset = assetService.createAsset(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(asset, "Asset created successfully"));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<AssetDto>>> getMerchantAssets(@PathVariable UUID merchantId,
                                                                         HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<AssetDto> assets = assetService.getAssetsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssetDto>> getAsset(@PathVariable UUID id, HttpServletRequest httpRequest) {
        AssetDto asset = assetService.getAsset(id);
        CurrentMerchant.requireOwner(httpRequest, asset.getMerchantId(), "Asset", id);
        return ResponseEntity.ok(ApiResponse.success(asset));
    }
}
```

- [ ] **Step 5: Make `merchantId` optional in the create DTOs**

In `ProductCreateRequest.java` and `CreateAssetRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. Keep every other annotation. If `jakarta.validation.constraints.NotNull` is no longer used in a file, remove its import.

- [ ] **Step 6: Narrow the public product route**

In `SecurityConfig.java`, change:

```java
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/**").permitAll()
```

to:

```java
                // Only single-product reads are public (pay page). "*" = one path segment,
                // so /api/v1/products/merchant/{id} now requires a login.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/products/*").permitAll()
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :modules:product-catalog:test :shared:security:test`
Expected: PASS (7 product-catalog tests, 4 security tests).

- [ ] **Step 8: Commit**

```bash
git add modules/product-catalog shared/security
git commit -m "feat(products): enforce merchant ownership on products and assets"
```

---

### Task 3: Orders and payments

**Files:**
- Modify: `modules/orders/build.gradle.kts`, `modules/payments/build.gradle.kts`
- Modify: `modules/orders/src/main/java/com/fluxpay/order/controller/OrderController.java`
- Modify: `modules/orders/src/main/java/com/fluxpay/order/dto/OrderCreateRequest.java` (remove `@NotNull` on `merchantId`)
- Modify: `modules/payments/src/main/java/com/fluxpay/payment/controller/PaymentController.java`
- Create: `modules/orders/src/test/java/com/fluxpay/order/controller/OrderControllerTest.java`
- Create: `modules/payments/src/test/java/com/fluxpay/payment/controller/PaymentControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1)
- Existing services:
  - `OrderService.createOrder(OrderCreateRequest)`
  - `OrderService.getOrder(UUID): OrderDto` (has `getMerchantId()`)
  - `OrderService.getOrdersByMerchant(UUID)`
  - `PaymentService.processPayment(ProcessPaymentRequest)` (request has `getOrderId()`)
  - `PaymentService.getPaymentIntent(UUID): PaymentIntentDto` (has `getOrderId()`, no merchant)
  - `PaymentService.getAllPayments(UUID)`
- Produces: `PaymentController` now takes `(PaymentService, OrderService)` in its constructor.

- [ ] **Step 1: Add the dependency**

Add `api(project(":shared:security"))` inside `dependencies { }` of **both** `modules/orders/build.gradle.kts` and `modules/payments/build.gradle.kts`.

- [ ] **Step 2: Write the failing tests**

`OrderControllerTest.java`:

```java
package com.fluxpay.order.controller;

import com.fluxpay.order.dto.OrderCreateRequest;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderControllerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final OrderController controller = new OrderController(orderService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createOrderUsesTokenMerchant() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setMerchantId(other);
        when(orderService.createOrder(any())).thenReturn(OrderDto.builder().build());

        controller.createOrder(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void readingAnotherMerchantsOrderIsNotFound() {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenReturn(OrderDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getOrder(id, as(me)));
    }

    @Test
    void listingAnotherMerchantsOrdersIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getOrdersByMerchant(other, as(me)));
    }
}
```

`PaymentControllerTest.java`:

```java
package com.fluxpay.payment.controller;

import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.PaymentIntentDto;
import com.fluxpay.payment.dto.ProcessPaymentRequest;
import com.fluxpay.payment.service.PaymentService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentControllerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final PaymentController controller = new PaymentController(paymentService, orderService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void processingPaymentForAnotherMerchantsOrderIsNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(OrderDto.builder().id(orderId).merchantId(other).build());
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setOrderId(orderId);

        assertThrows(ResourceNotFoundException.class, () -> controller.processPayment(request, as(me)));
        verify(paymentService, never()).processPayment(any());
    }

    @Test
    void readingAnotherMerchantsPaymentIntentIsNotFound() {
        UUID intentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(paymentService.getPaymentIntent(intentId)).thenReturn(PaymentIntentDto.builder().id(intentId).orderId(orderId).build());
        when(orderService.getOrder(orderId)).thenReturn(OrderDto.builder().id(orderId).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getPaymentIntent(intentId, as(me)));
    }

    @Test
    void listingAnotherMerchantsPaymentsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getAllPayments(other, as(me)));
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :modules:orders:test :modules:payments:test`
Expected: compilation FAIL (signatures don't match yet).

- [ ] **Step 4: Implement the controllers**

Replace `OrderController.java`:

```java
package com.fluxpay.order.controller;

import com.fluxpay.order.dto.OrderCreateRequest;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                                             HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        OrderDto order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(order, "Order created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable UUID id, HttpServletRequest httpRequest) {
        OrderDto order = orderService.getOrder(id);
        CurrentMerchant.requireOwner(httpRequest, order.getMerchantId(), "Order", id);
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<OrderDto>>> getOrdersByMerchant(@PathVariable UUID merchantId,
                                                                           HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<OrderDto> orders = orderService.getOrdersByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(orders));
    }
}
```

> Products in `OrderCreateRequest.items` are not checked against the caller here: `OrderService.createOrder` loads them by ID. A merchant ordering another merchant's product through this authenticated API is out of scope. Real customer orders come through checkout sessions.

Replace `PaymentController.java`:

```java
package com.fluxpay.payment.controller;

import com.fluxpay.order.service.OrderService;
import com.fluxpay.payment.dto.PaymentIntentDto;
import com.fluxpay.payment.dto.ProcessPaymentRequest;
import com.fluxpay.payment.service.PaymentService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentIntentDto>> processPayment(@Valid @RequestBody ProcessPaymentRequest request,
                                                                        HttpServletRequest httpRequest) {
        UUID orderId = request.getOrderId();
        CurrentMerchant.requireOwner(httpRequest, orderService.getOrder(orderId).getMerchantId(), "Order", orderId);
        PaymentIntentDto intent = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(intent, "Payment processing initiated"));
    }

    @GetMapping("/intent/{id}")
    public ResponseEntity<ApiResponse<PaymentIntentDto>> getPaymentIntent(@PathVariable UUID id, HttpServletRequest httpRequest) {
        PaymentIntentDto intent = paymentService.getPaymentIntent(id);
        CurrentMerchant.requireOwner(httpRequest, orderService.getOrder(intent.getOrderId()).getMerchantId(), "PaymentIntent", id);
        return ResponseEntity.ok(ApiResponse.success(intent));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<PaymentIntentDto>>> getAllPayments(@PathVariable UUID merchantId,
                                                                              HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<PaymentIntentDto> payments = paymentService.getAllPayments(merchantId);
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
}
```

- [ ] **Step 5: Make `merchantId` optional**

In `OrderCreateRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. Keep `@NotNull`/`@NotEmpty` on `items` and on any other field. Remove the import only if nothing else uses it.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :modules:orders:test :modules:payments:test`
Expected: PASS: 3 order tests, plus 3 payment-controller tests and the 5 existing `RazorpayPaymentServiceTest` tests.

- [ ] **Step 7: Commit**

```bash
git add modules/orders modules/payments
git commit -m "feat(orders,payments): enforce merchant ownership"
```

---

### Task 4: Coupons

**Files:**
- Modify: `modules/coupons/build.gradle.kts`
- Modify: `modules/coupons/src/main/java/com/fluxpay/coupon/controller/CouponController.java`
- Modify: `modules/coupons/src/main/java/com/fluxpay/coupon/dto/CreateCouponRequest.java`, `ApplyCouponRequest.java` (remove `@NotNull` on `merchantId`)
- Create: `modules/coupons/src/test/java/com/fluxpay/coupon/controller/CouponControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1)
- Existing services:
  - `CouponService.createCoupon(CreateCouponRequest)`
  - `CouponService.getCoupon(UUID): CouponDto`
  - `CouponService.getAllCoupons(UUID)`
  - `CouponService.applyCoupon(ApplyCouponRequest)`
  - `CouponService.deactivateCoupon(UUID)`

- [ ] **Step 1: Add the dependency**

Add `api(project(":shared:security"))` to `modules/coupons/build.gradle.kts`.

- [ ] **Step 2: Write the failing test**

`CouponControllerTest.java`:

```java
package com.fluxpay.coupon.controller;

import com.fluxpay.coupon.dto.ApplyCouponRequest;
import com.fluxpay.coupon.dto.CouponDto;
import com.fluxpay.coupon.dto.CreateCouponRequest;
import com.fluxpay.coupon.service.CouponService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponControllerTest {

    private final CouponService couponService = mock(CouponService.class);
    private final CouponController controller = new CouponController(couponService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createCouponUsesTokenMerchant() {
        CreateCouponRequest request = new CreateCouponRequest();
        request.setMerchantId(other);
        when(couponService.createCoupon(any())).thenReturn(CouponDto.builder().build());

        controller.createCoupon(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void applyCouponUsesTokenMerchant() {
        ApplyCouponRequest request = new ApplyCouponRequest();
        request.setMerchantId(other);
        request.setCode("SAVE10");
        when(couponService.applyCoupon(any())).thenReturn(CouponDto.builder().build());

        controller.applyCoupon(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void readingAnotherMerchantsCouponIsNotFound() {
        UUID id = UUID.randomUUID();
        when(couponService.getCoupon(id)).thenReturn(CouponDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getCoupon(id, as(me)));
    }

    @Test
    void listingAnotherMerchantsCouponsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getAllCoupons(other, as(me)));
    }

    @Test
    void deactivatingAnotherMerchantsCouponIsNotFoundAndChangesNothing() {
        UUID id = UUID.randomUUID();
        when(couponService.getCoupon(id)).thenReturn(CouponDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.deactivateCoupon(id, as(me)));
        verify(couponService, never()).deactivateCoupon(any());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :modules:coupons:test`
Expected: compilation FAIL.

- [ ] **Step 4: Implement the controller**

Replace `CouponController.java`:

```java
package com.fluxpay.coupon.controller;

import com.fluxpay.coupon.dto.ApplyCouponRequest;
import com.fluxpay.coupon.dto.CouponDto;
import com.fluxpay.coupon.dto.CreateCouponRequest;
import com.fluxpay.coupon.service.CouponService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public ResponseEntity<ApiResponse<CouponDto>> createCoupon(@Valid @RequestBody CreateCouponRequest request,
                                                               HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        CouponDto coupon = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(coupon, "Coupon created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponDto>> getCoupon(@PathVariable UUID id, HttpServletRequest httpRequest) {
        CouponDto coupon = couponService.getCoupon(id);
        CurrentMerchant.requireOwner(httpRequest, coupon.getMerchantId(), "Coupon", id);
        return ResponseEntity.ok(ApiResponse.success(coupon));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<CouponDto>>> getAllCoupons(@PathVariable UUID merchantId,
                                                                      HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<CouponDto> coupons = couponService.getAllCoupons(merchantId);
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<CouponDto>> applyCoupon(@Valid @RequestBody ApplyCouponRequest request,
                                                              HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        CouponDto coupon = couponService.applyCoupon(request);
        return ResponseEntity.ok(ApiResponse.success(coupon, "Coupon applied successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(@PathVariable UUID id, HttpServletRequest httpRequest) {
        CurrentMerchant.requireOwner(httpRequest, couponService.getCoupon(id).getMerchantId(), "Coupon", id);
        couponService.deactivateCoupon(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Coupon deactivated successfully"));
    }
}
```

- [ ] **Step 5: Make `merchantId` optional**

In `CreateCouponRequest.java` and `ApplyCouponRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. Keep `@NotBlank` on `code` and every other annotation.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :modules:coupons:test`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add modules/coupons
git commit -m "feat(coupons): enforce merchant ownership"
```

---

### Task 5: Invoices and subscriptions

**Files:**
- Modify: `modules/invoices/build.gradle.kts`, `modules/subscriptions/build.gradle.kts`
- Modify: `modules/invoices/src/main/java/com/fluxpay/invoice/controller/InvoiceController.java`
- Modify: `modules/subscriptions/src/main/java/com/fluxpay/subscription/controller/SubscriptionController.java`
- Modify: `modules/subscriptions/src/main/java/com/fluxpay/subscription/dto/SubscriptionCreateRequest.java` (remove `@NotNull` on `merchantId`)
- Create: `modules/invoices/src/test/java/com/fluxpay/invoice/controller/InvoiceControllerTest.java`
- Create: `modules/subscriptions/src/test/java/com/fluxpay/subscription/controller/SubscriptionControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1)
- Existing services:
  - `InvoiceService.generateInvoice(GenerateInvoiceRequest)` (request has only `orderId`)
  - `InvoiceService.getInvoice(UUID): InvoiceDto`
  - `InvoiceService.getCustomerInvoices(UUID, String)`
  - `InvoiceService.getAllInvoices(UUID)`
  - `OrderService.getOrder(UUID)` (the invoices module already depends on orders)
  - `SubscriptionService.createSubscription(SubscriptionCreateRequest)`
  - `SubscriptionService.getSubscription(UUID): SubscriptionDto`
  - `SubscriptionService.getCustomerSubscriptions(UUID, String)`
  - `SubscriptionService.getAllSubscriptions(UUID)`
  - `SubscriptionService.cancelSubscription(UUID): SubscriptionDto`
- Produces: `InvoiceController` now takes `(InvoiceService, OrderService)` in its constructor.

- [ ] **Step 1: Add the dependency**

Add `api(project(":shared:security"))` to `modules/invoices/build.gradle.kts` and `modules/subscriptions/build.gradle.kts`.

- [ ] **Step 2: Write the failing tests**

`InvoiceControllerTest.java`:

```java
package com.fluxpay.invoice.controller;

import com.fluxpay.invoice.dto.GenerateInvoiceRequest;
import com.fluxpay.invoice.dto.InvoiceDto;
import com.fluxpay.invoice.service.InvoiceService;
import com.fluxpay.order.dto.OrderDto;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceControllerTest {

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final OrderService orderService = mock(OrderService.class);
    private final InvoiceController controller = new InvoiceController(invoiceService, orderService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void invoicingAnotherMerchantsOrderIsNotFoundAndCreatesNothing() {
        UUID orderId = UUID.randomUUID();
        when(orderService.getOrder(orderId)).thenReturn(OrderDto.builder().id(orderId).merchantId(other).build());
        GenerateInvoiceRequest request = new GenerateInvoiceRequest();
        request.setOrderId(orderId);

        assertThrows(ResourceNotFoundException.class, () -> controller.generateInvoice(request, as(me)));
        verify(invoiceService, never()).generateInvoice(any());
    }

    @Test
    void readingAnotherMerchantsInvoiceIsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceService.getInvoice(id)).thenReturn(InvoiceDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getInvoice(id, as(me)));
    }

    @Test
    void listingAnotherMerchantsInvoicesIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getAllInvoices(other, as(me)));
        assertThrows(ForbiddenException.class, () -> controller.getCustomerInvoices(other, "a@b.com", as(me)));
    }
}
```

`SubscriptionControllerTest.java`:

```java
package com.fluxpay.subscription.controller;

import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import com.fluxpay.subscription.dto.SubscriptionCreateRequest;
import com.fluxpay.subscription.dto.SubscriptionDto;
import com.fluxpay.subscription.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionControllerTest {

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final SubscriptionController controller = new SubscriptionController(subscriptionService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createSubscriptionUsesTokenMerchant() {
        SubscriptionCreateRequest request = new SubscriptionCreateRequest();
        request.setMerchantId(other);
        when(subscriptionService.createSubscription(any())).thenReturn(SubscriptionDto.builder().build());

        controller.createSubscription(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void readingAnotherMerchantsSubscriptionIsNotFound() {
        UUID id = UUID.randomUUID();
        when(subscriptionService.getSubscription(id)).thenReturn(SubscriptionDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getSubscription(id, as(me)));
    }

    @Test
    void listingAnotherMerchantsSubscriptionsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getAllSubscriptions(other, as(me)));
        assertThrows(ForbiddenException.class, () -> controller.getCustomerSubscriptions(other, "a@b.com", as(me)));
    }

    @Test
    void cancellingAnotherMerchantsSubscriptionIsNotFoundAndChangesNothing() {
        UUID id = UUID.randomUUID();
        when(subscriptionService.getSubscription(id)).thenReturn(SubscriptionDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.cancelSubscription(id, as(me)));
        verify(subscriptionService, never()).cancelSubscription(any());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :modules:invoices:test :modules:subscriptions:test`
Expected: compilation FAIL.

- [ ] **Step 4: Implement the controllers**

Replace `InvoiceController.java`:

```java
package com.fluxpay.invoice.controller;

import com.fluxpay.invoice.dto.GenerateInvoiceRequest;
import com.fluxpay.invoice.dto.InvoiceDto;
import com.fluxpay.invoice.service.InvoiceService;
import com.fluxpay.order.service.OrderService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceDto>> generateInvoice(@Valid @RequestBody GenerateInvoiceRequest request,
                                                                   HttpServletRequest httpRequest) {
        UUID orderId = request.getOrderId();
        CurrentMerchant.requireOwner(httpRequest, orderService.getOrder(orderId).getMerchantId(), "Order", orderId);
        InvoiceDto invoice = invoiceService.generateInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invoice, "Invoice generated successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InvoiceDto>> getInvoice(@PathVariable UUID id, HttpServletRequest httpRequest) {
        InvoiceDto invoice = invoiceService.getInvoice(id);
        CurrentMerchant.requireOwner(httpRequest, invoice.getMerchantId(), "Invoice", id);
        return ResponseEntity.ok(ApiResponse.success(invoice));
    }

    @GetMapping("/merchant/{merchantId}/customer/{email}")
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getCustomerInvoices(
            @PathVariable UUID merchantId, @PathVariable String email, HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<InvoiceDto> invoices = invoiceService.getCustomerInvoices(merchantId, email);
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> getAllInvoices(@PathVariable UUID merchantId,
                                                                        HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<InvoiceDto> invoices = invoiceService.getAllInvoices(merchantId);
        return ResponseEntity.ok(ApiResponse.success(invoices));
    }
}
```

Replace `SubscriptionController.java`:

```java
package com.fluxpay.subscription.controller;

import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import com.fluxpay.subscription.dto.SubscriptionCreateRequest;
import com.fluxpay.subscription.dto.SubscriptionDto;
import com.fluxpay.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionDto>> createSubscription(@Valid @RequestBody SubscriptionCreateRequest request,
                                                                           HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        SubscriptionDto subscription = subscriptionService.createSubscription(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscription, "Subscription created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(@PathVariable UUID id, HttpServletRequest httpRequest) {
        SubscriptionDto subscription = subscriptionService.getSubscription(id);
        CurrentMerchant.requireOwner(httpRequest, subscription.getMerchantId(), "Subscription", id);
        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    @GetMapping("/merchant/{merchantId}/customer/{email}")
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getCustomerSubscriptions(
            @PathVariable UUID merchantId, @PathVariable String email, HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<SubscriptionDto> subscriptions = subscriptionService.getCustomerSubscriptions(merchantId, email);
        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getAllSubscriptions(
            @PathVariable UUID merchantId, HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<SubscriptionDto> subscriptions = subscriptionService.getAllSubscriptions(merchantId);
        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> cancelSubscription(@PathVariable UUID id, HttpServletRequest httpRequest) {
        CurrentMerchant.requireOwner(httpRequest, subscriptionService.getSubscription(id).getMerchantId(), "Subscription", id);
        SubscriptionDto subscription = subscriptionService.cancelSubscription(id);
        return ResponseEntity.ok(ApiResponse.success(subscription, "Subscription set to cancel at period end"));
    }
}
```

- [ ] **Step 5: Make `merchantId` optional**

In `SubscriptionCreateRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. Keep the annotations on the other fields.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :modules:invoices:test :modules:subscriptions:test`
Expected: PASS (3 + 4 tests).

- [ ] **Step 7: Commit**

```bash
git add modules/invoices modules/subscriptions
git commit -m "feat(invoices,subscriptions): enforce merchant ownership"
```

---

### Task 6: API keys and customers

**Files:**
- Modify: `modules/api-keys/build.gradle.kts`, `modules/customers/build.gradle.kts`
- Modify: `modules/api-keys/src/main/java/com/fluxpay/apikey/controller/ApiKeyController.java`
- Modify: `modules/api-keys/src/main/java/com/fluxpay/apikey/service/ApiKeyService.java` (`revokeApiKey` gains an owner parameter)
- Modify: `modules/api-keys/src/main/java/com/fluxpay/apikey/dto/ApiKeyCreateRequest.java` (remove `@NotNull` on `merchantId`)
- Modify: `modules/customers/src/main/java/com/fluxpay/customer/controller/CustomerController.java`
- Create: `modules/api-keys/src/test/java/com/fluxpay/apikey/controller/ApiKeyControllerTest.java`
- Create: `modules/api-keys/src/test/java/com/fluxpay/apikey/service/ApiKeyServiceTest.java`
- Create: `modules/customers/src/test/java/com/fluxpay/customer/controller/CustomerControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1)
- Existing:
  - `ApiKeyService.createApiKey(ApiKeyCreateRequest): String`
  - `ApiKeyService.getApiKeysByMerchant(UUID)`
  - `ApiKeyRepository.findById(UUID)`
  - the `ApiKey` entity's `getMerchantId()`
  - `CustomerService.getCustomersByMerchant(UUID)`
- Produces: `ApiKeyService.revokeApiKey(UUID id, UUID callerMerchantId)`. It **replaces** `revokeApiKey(UUID)`; there are no other callers. There's no `getById` DTO for keys, so the check lives in the service, the same way `WebhookService.deactivateEndpoint` does it.

- [ ] **Step 1: Add the dependency**

Add `api(project(":shared:security"))` to `modules/api-keys/build.gradle.kts` and `modules/customers/build.gradle.kts`.

- [ ] **Step 2: Write the failing tests**

`ApiKeyServiceTest.java`:

```java
package com.fluxpay.apikey.service;

import com.fluxpay.apikey.entity.ApiKey;
import com.fluxpay.apikey.repository.ApiKeyRepository;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyServiceTest {

    private final ApiKeyRepository repository = mock(ApiKeyRepository.class);
    private final ApiKeyService service = new ApiKeyService(repository);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void revokingAnotherMerchantsKeyIsNotFoundAndChangesNothing() {
        UUID id = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(id).merchantId(other).active(true).build();
        when(repository.findById(id)).thenReturn(Optional.of(key));

        assertThrows(ResourceNotFoundException.class, () -> service.revokeApiKey(id, me));
        assertTrue(key.isActive());
        verify(repository, never()).save(any());
    }

    @Test
    void revokingOwnKeyDeactivatesIt() {
        UUID id = UUID.randomUUID();
        ApiKey key = ApiKey.builder().id(id).merchantId(me).active(true).build();
        when(repository.findById(id)).thenReturn(Optional.of(key));

        service.revokeApiKey(id, me);

        assertFalse(key.isActive());
        verify(repository).save(key);
    }
}
```

`ApiKeyControllerTest.java`:

```java
package com.fluxpay.apikey.controller;

import com.fluxpay.apikey.dto.ApiKeyCreateRequest;
import com.fluxpay.apikey.service.ApiKeyService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyControllerTest {

    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final ApiKeyController controller = new ApiKeyController(apiKeyService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createApiKeyUsesTokenMerchant() {
        ApiKeyCreateRequest request = new ApiKeyCreateRequest();
        request.setMerchantId(other);
        request.setMode("TEST");
        when(apiKeyService.createApiKey(any())).thenReturn("sk_test_x");

        controller.createApiKey(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void listingAnotherMerchantsKeysIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getApiKeysByMerchant(other, as(me)));
    }

    @Test
    void revokePassesTokenMerchantToService() {
        UUID id = UUID.randomUUID();
        controller.revokeApiKey(id, as(me));
        verify(apiKeyService).revokeApiKey(id, me);
    }
}
```

`CustomerControllerTest.java`:

```java
package com.fluxpay.customer.controller;

import com.fluxpay.customer.service.CustomerService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CustomerControllerTest {

    private final CustomerService customerService = mock(CustomerService.class);
    private final CustomerController controller = new CustomerController(customerService);

    @Test
    void listingAnotherMerchantsCustomersIsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, UUID.randomUUID().toString());

        assertThrows(ForbiddenException.class, () -> controller.getCustomersByMerchant(UUID.randomUUID(), request));
        verify(customerService, never()).getCustomersByMerchant(any());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :modules:api-keys:test :modules:customers:test`
Expected: compilation FAIL.

- [ ] **Step 4: Implement**

In `ApiKeyService.java`, replace the `revokeApiKey` method with:

```java
    @Transactional
    public void revokeApiKey(UUID id, UUID callerMerchantId) {
        ApiKey apiKey = apiKeyRepository.findById(id)
                .filter(key -> key.getMerchantId().equals(callerMerchantId))
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id.toString()));
        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);
    }
```

Replace `ApiKeyController.java`:

```java
package com.fluxpay.apikey.controller;

import com.fluxpay.apikey.dto.ApiKeyCreateRequest;
import com.fluxpay.apikey.dto.ApiKeyDto;
import com.fluxpay.apikey.service.ApiKeyService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> createApiKey(@Valid @RequestBody ApiKeyCreateRequest request,
                                                                         HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        String rawKey = apiKeyService.createApiKey(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("rawKey", rawKey), "API Key created successfully. Store this key, it will not be shown again."));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<ApiKeyDto>>> getApiKeysByMerchant(@PathVariable UUID merchantId,
                                                                             HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<ApiKeyDto> keys = apiKeyService.getApiKeysByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(keys));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(@PathVariable UUID id, HttpServletRequest httpRequest) {
        apiKeyService.revokeApiKey(id, CurrentMerchant.require(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(null, "API Key revoked successfully"));
    }
}
```

Replace `CustomerController.java`:

```java
package com.fluxpay.customer.controller;

import com.fluxpay.customer.dto.CustomerDto;
import com.fluxpay.customer.service.CustomerService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<CustomerDto>>> getCustomersByMerchant(@PathVariable UUID merchantId,
                                                                                 HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<CustomerDto> customers = customerService.getCustomersByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(customers));
    }
}
```

- [ ] **Step 5: Make `merchantId` optional**

In `ApiKeyCreateRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. Keep the annotations on `mode`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :modules:api-keys:test :modules:customers:test`
Expected: PASS (5 + 1 tests).

- [ ] **Step 7: Commit**

```bash
git add modules/api-keys modules/customers
git commit -m "feat(api-keys,customers): enforce merchant ownership"
```

---

### Task 7: Organizations and applications

**Files:**
- Modify: `modules/organization-management/build.gradle.kts`
- Modify: `modules/organization-management/src/main/java/com/fluxpay/organization/controller/OrganizationController.java`
- Modify: `modules/organization-management/src/main/java/com/fluxpay/organization/dto/OrganizationCreateRequest.java` (remove `@NotNull` on `merchantId` **only**; `ApplicationCreateRequest.organizationId` stays `@NotNull`)
- Create: `modules/organization-management/src/test/java/com/fluxpay/organization/controller/OrganizationControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1)
- Existing services:
  - `OrganizationService.createOrganization(OrganizationCreateRequest)`
  - `OrganizationService.getOrganization(UUID): OrganizationDto` (has `getMerchantId()`)
  - `OrganizationService.getOrganizationsByMerchant(UUID)`
  - `OrganizationService.createApplication(ApplicationCreateRequest)` (request has `getOrganizationId()`)
  - `OrganizationService.getApplicationsByOrganization(UUID)`

- [ ] **Step 1: Add the dependency**

Add `api(project(":shared:security"))` to `modules/organization-management/build.gradle.kts`.

- [ ] **Step 2: Write the failing test**

`OrganizationControllerTest.java`:

```java
package com.fluxpay.organization.controller;

import com.fluxpay.organization.dto.ApplicationCreateRequest;
import com.fluxpay.organization.dto.OrganizationCreateRequest;
import com.fluxpay.organization.dto.OrganizationDto;
import com.fluxpay.organization.service.OrganizationService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.exception.ResourceNotFoundException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrganizationControllerTest {

    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final OrganizationController controller = new OrganizationController(organizationService);
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private static MockHttpServletRequest as(UUID merchantId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, merchantId.toString());
        return request;
    }

    @Test
    void createOrganizationUsesTokenMerchant() {
        OrganizationCreateRequest request = new OrganizationCreateRequest();
        request.setMerchantId(other);
        when(organizationService.createOrganization(any())).thenReturn(OrganizationDto.builder().build());

        controller.createOrganization(request, as(me));

        assertEquals(me, request.getMerchantId());
    }

    @Test
    void readingAnotherMerchantsOrganizationIsNotFound() {
        UUID id = UUID.randomUUID();
        when(organizationService.getOrganization(id)).thenReturn(OrganizationDto.builder().id(id).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getOrganization(id, as(me)));
    }

    @Test
    void listingAnotherMerchantsOrganizationsIsForbidden() {
        assertThrows(ForbiddenException.class, () -> controller.getOrganizationsByMerchant(other, as(me)));
    }

    @Test
    void addingApplicationToAnotherMerchantsOrganizationIsNotFoundAndCreatesNothing() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getOrganization(orgId)).thenReturn(OrganizationDto.builder().id(orgId).merchantId(other).build());
        ApplicationCreateRequest request = new ApplicationCreateRequest();
        request.setOrganizationId(orgId);

        assertThrows(ResourceNotFoundException.class, () -> controller.createApplication(request, as(me)));
        verify(organizationService, never()).createApplication(any());
    }

    @Test
    void listingApplicationsOfAnotherMerchantsOrganizationIsNotFound() {
        UUID orgId = UUID.randomUUID();
        when(organizationService.getOrganization(orgId)).thenReturn(OrganizationDto.builder().id(orgId).merchantId(other).build());

        assertThrows(ResourceNotFoundException.class, () -> controller.getApplicationsByOrganization(orgId, as(me)));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :modules:organization-management:test`
Expected: compilation FAIL.

- [ ] **Step 4: Implement the controller**

Replace `OrganizationController.java`:

```java
package com.fluxpay.organization.controller;

import com.fluxpay.organization.dto.ApplicationCreateRequest;
import com.fluxpay.organization.dto.ApplicationDto;
import com.fluxpay.organization.dto.OrganizationCreateRequest;
import com.fluxpay.organization.dto.OrganizationDto;
import com.fluxpay.organization.service.OrganizationService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(@Valid @RequestBody OrganizationCreateRequest request,
                                                                           HttpServletRequest httpRequest) {
        request.setMerchantId(CurrentMerchant.require(httpRequest));
        OrganizationDto organization = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(organization, "Organization created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(@PathVariable UUID id, HttpServletRequest httpRequest) {
        OrganizationDto organization = organizationService.getOrganization(id);
        CurrentMerchant.requireOwner(httpRequest, organization.getMerchantId(), "Organization", id);
        return ResponseEntity.ok(ApiResponse.success(organization));
    }

    @GetMapping("/merchant/{merchantId}")
    public ResponseEntity<ApiResponse<List<OrganizationDto>>> getOrganizationsByMerchant(@PathVariable UUID merchantId,
                                                                                         HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, merchantId);
        List<OrganizationDto> organizations = organizationService.getOrganizationsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success(organizations));
    }

    @PostMapping("/applications")
    public ResponseEntity<ApiResponse<ApplicationDto>> createApplication(@Valid @RequestBody ApplicationCreateRequest request,
                                                                         HttpServletRequest httpRequest) {
        requireOwnOrganization(httpRequest, request.getOrganizationId());
        ApplicationDto application = organizationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(application, "Application created successfully"));
    }

    @GetMapping("/{organizationId}/applications")
    public ResponseEntity<ApiResponse<List<ApplicationDto>>> getApplicationsByOrganization(@PathVariable UUID organizationId,
                                                                                           HttpServletRequest httpRequest) {
        requireOwnOrganization(httpRequest, organizationId);
        List<ApplicationDto> applications = organizationService.getApplicationsByOrganization(organizationId);
        return ResponseEntity.ok(ApiResponse.success(applications));
    }

    private void requireOwnOrganization(HttpServletRequest httpRequest, UUID organizationId) {
        OrganizationDto organization = organizationService.getOrganization(organizationId);
        CurrentMerchant.requireOwner(httpRequest, organization.getMerchantId(), "Organization", organizationId);
    }
}
```

- [ ] **Step 5: Make `merchantId` optional**

In `OrganizationCreateRequest.java`, delete the `@NotNull(...)` line directly above `private UUID merchantId;`. **Do not** touch `ApplicationCreateRequest.java`.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :modules:organization-management:test`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add modules/organization-management
git commit -m "feat(organizations): enforce merchant ownership"
```

---

### Task 8: Merchant profile

`POST /api/v1/merchants` lets any logged-in user create merchant accounts, with a password, outside the signup flow. It duplicates `POST /api/v1/auth/register` and nothing calls it, so it's removed. `GET /api/v1/merchants/{id}` is limited to yourself.

**Files:**
- Modify: `modules/merchant-management/src/main/java/com/fluxpay/merchant/controller/MerchantController.java`
- Create: `modules/merchant-management/src/test/java/com/fluxpay/merchant/controller/MerchantControllerTest.java`

**Interfaces:**
- Consumes: `CurrentMerchant` (Task 1). `merchant-management` already depends on `:shared:security`.
- Existing:
  - `MerchantService.getMerchant(UUID): MerchantDto`
  - `MerchantService.createMerchant(...)`, which stays in the service because `AuthenticationService.register` uses it.

- [ ] **Step 1: Write the failing test**

`MerchantControllerTest.java`:

```java
package com.fluxpay.merchant.controller;

import com.fluxpay.merchant.service.MerchantService;
import com.fluxpay.shared.exception.ForbiddenException;
import com.fluxpay.shared.security.CurrentMerchant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MerchantControllerTest {

    private final MerchantService merchantService = mock(MerchantService.class);
    private final MerchantController controller = new MerchantController(merchantService);

    @Test
    void readingAnotherMerchantsProfileIsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CurrentMerchant.REQUEST_ATTRIBUTE, UUID.randomUUID().toString());

        assertThrows(ForbiddenException.class, () -> controller.getMerchant(UUID.randomUUID(), request));
        verify(merchantService, never()).getMerchant(any());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :modules:merchant-management:test`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

Replace `MerchantController.java`:

```java
package com.fluxpay.merchant.controller;

import com.fluxpay.merchant.dto.MerchantDto;
import com.fluxpay.merchant.service.MerchantService;
import com.fluxpay.shared.dto.ApiResponse;
import com.fluxpay.shared.security.CurrentMerchant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Merchant accounts are created only through POST /api/v1/auth/register.
@RestController
@RequestMapping("/api/v1/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MerchantDto>> getMerchant(@PathVariable UUID id, HttpServletRequest httpRequest) {
        CurrentMerchant.assertIs(httpRequest, id);
        MerchantDto merchant = merchantService.getMerchant(id);
        return ResponseEntity.ok(ApiResponse.success(merchant));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :modules:merchant-management:test`
Expected: PASS (1 test).

- [ ] **Step 5: Commit**

```bash
git add modules/merchant-management
git commit -m "feat(merchants): limit profile reads to self; remove unauthenticated-style merchant creation"
```

---

### Task 9: Full verification and report update

**Files:**
- Modify: `../FLUXPAY_REVIEW_REPORT.md` (workspace root, outside the git repo)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Check that no merchant-scoped controller was missed**

Run:

```bash
grep -rln "PathVariable UUID merchantId\|setMerchantId\|UUID id)" --include='*Controller.java' modules apps \
  | xargs grep -L "CurrentMerchant" \
  | grep -v -e analytics -e audit-logs -e notifications
```

Expected: no output. If any file is listed, it's a merchant-scoped controller without a check; apply the matching rule from the Architecture section.

- [ ] **Step 2: Run the whole build**

Run: `./gradlew compileJava test :apps:fluxpay-api:bootJar --no-daemon`
Expected: `BUILD SUCCESSFUL`. Test counts: about 18 existing tests plus about 39 new ones.

- [ ] **Step 3: Start the app against a local database**

**Never use the production Neon database.**

```bash
cd .. && docker compose up -d postgres redis && cd fluxpay-backend
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fluxpay \
SPRING_DATASOURCE_USERNAME=fluxpay_user SPRING_DATASOURCE_PASSWORD=password \
REDIS_URL=redis://localhost:6379 JWT_SECRET=$(openssl rand -base64 48) \
./gradlew :apps:fluxpay-api:bootRun
```

Expected: the log shows `Started FluxpayApiApplication` and Flyway applies V1–V3.

Use a fresh shell for the next step. The env variables override `.env` because real environment variables beat imported files.

- [ ] **Step 4: Smoke test with two merchants (pins Review Focus #2 and #3)**

In a second terminal:

```bash
API=http://localhost:8080/api/v1
reg() { curl -s -X POST $API/auth/register -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"password123\"}"; }
A=$(reg a@test.dev); B=$(reg b@test.dev)
TA=$(echo $A | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
MA=$(echo $A | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["merchantId"])')
MB=$(echo $B | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["merchantId"])')
TB=$(echo $B | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# B creates a product (body says merchant A - must be ignored)
P=$(curl -s -X POST $API/products -H "Authorization: Bearer $TB" -H 'Content-Type: application/json' \
  -d "{\"merchantId\":\"$MA\",\"name\":\"B item\",\"price\":499,\"currency\":\"INR\",\"productType\":\"ONE_TIME\",\"visibility\":\"PUBLIC\"}")
PID=$(echo $P | python3 -c 'import sys,json;d=json.load(sys.stdin)["data"];print(d["id"]); assert d["merchantId"]=="'$MB'", d')

curl -s -o /dev/null -w "A lists B's orders:        %{http_code} (expect 403)\n" -H "Authorization: Bearer $TA" $API/orders/merchant/$MB
curl -s -o /dev/null -w "A deletes B's product:     %{http_code} (expect 404)\n" -X DELETE -H "Authorization: Bearer $TA" $API/products/$PID
curl -s -o /dev/null -w "Public product page read:  %{http_code} (expect 200)\n" $API/products/$PID
curl -s -o /dev/null -w "Public product list read:  %{http_code} (expect 403)\n" $API/products/merchant/$MB
curl -s -o /dev/null -w "B lists own products:      %{http_code} (expect 200)\n" -H "Authorization: Bearer $TB" $API/products/merchant/$MB
curl -s -o /dev/null -w "A reads B's profile:       %{http_code} (expect 403)\n" -H "Authorization: Bearer $TA" $API/merchants/$MB
```

Expected: every line shows the code in brackets. If the product create fails validation, check `ProductCreateRequest` for any other required field and add it to the JSON. Don't relax that field's validation.

- [ ] **Step 5: Smoke test the dashboard**

Point `fluxpay-frontend/.env.local` at `NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1`, then run `npm run dev` in `fluxpay-frontend`. Log in as `b@test.dev` and check:
- Products, Orders, Payments, Customers, Coupons, Subscriptions, Invoices, Assets, API keys and Webhooks all load without errors.
- Creating a product, a coupon and an API key works.
- Open `/pay/<PID>` in a private window (logged out): the product shows and checkout starts.

- [ ] **Step 6: Update the report**

In `FLUXPAY_REVIEW_REPORT.md`:
- Change summary row 4's status to `**Fixed**`.
- Replace the first P0 item ("Ownership checks on every merchant-scoped endpoint") with: `1. ~~Ownership checks on every merchant-scoped endpoint~~: **done** (see docs/superpowers/plans/2026-10-01-tenant-isolation.md).`

- [ ] **Step 7: Commit**

```bash
git add -A
git status   # .env must NOT appear
git commit -m "chore: tenant isolation verified end-to-end"
```

---

## Out of scope (known, not fixed here)

- **Dashboard invoice dialog calls the wrong endpoint.** It posts to `/invoices/generate`, but the endpoint is `POST /invoices`. This bug predates this plan.
- **API keys are still not accepted** as authentication for server-to-server calls (report P1-7).
- **`OrderCreateRequest.items`** can reference another merchant's product IDs through the authenticated orders API (see the note in Task 3).
- **Coupon codes are globally unique**, not per merchant.
