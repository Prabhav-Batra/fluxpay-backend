# Fluxpay Merchant Integration Guide

How to take payments on your website with Fluxpay. Fluxpay hosts the checkout page, collects the payment through Razorpay, and tells your server with a signed webhook when the payment succeeds.

You need:

- A Fluxpay merchant account (sign up in the Fluxpay dashboard).
- A server that can receive an HTTPS `POST` (for the webhook). Static sites can use a serverless function on Vercel, Netlify or Cloudflare.

In this guide:

- `CHECKOUT_HOST` is the Fluxpay dashboard/checkout domain, for example `https://pay.fluxpay.example`.
- `API_HOST` is the Fluxpay API, for example `https://fluxpay-backend-osqv.onrender.com`. All API paths start with `/api/v1`.

---

## 1. How it works

```
Your site                     Fluxpay                           Razorpay
─────────                     ───────                           ────────
1. Send customer to  ───────▶ 2. Checkout page shows product
   a Fluxpay link                and opens the Razorpay modal ─▶ 3. Customer pays
                              4. Fluxpay checks Razorpay's   ◀──
                                 signature, marks order PAID
5. Webhook to your   ◀─────── 6. Signed POST: payment.succeeded
   server: grant the
   purchase
7. Customer returns to your success URL
```

The price always comes from the product stored in Fluxpay, so a customer can't change the amount they pay.

**Only fulfil an order when the webhook arrives** (step 5). The redirect in step 7 is just for the customer: it can be faked, and it never happens if they close the tab.

---

## 2. One-time setup in the dashboard

1. **Create a product.** Go to Products, then New, set a price, and copy its **Product ID** (a UUID).
   - Razorpay's minimum charge is **100 subunits**, for example ₹1.00. INR is the safe choice. Other currencies only work if international payments are enabled on Fluxpay's Razorpay account.
2. **Register your webhook endpoint.** Go to Developers, then Webhooks, then Add endpoint, and enter your HTTPS URL, for example `https://yoursite.com/webhooks/fluxpay`.
3. **Copy the endpoint's secret** (`whsec_...`) and store it in your server's environment as `FLUXPAY_WEBHOOK_SECRET`. Never put it in browser code.

---

## 3. Send the customer to checkout

Pick **one** option.

### Option A: payment link (no backend code)

Link or redirect to:

```
CHECKOUT_HOST/pay/<PRODUCT_ID>?ref=<YOUR_REFERENCE>&return_url=<URL_ENCODED_RETURN_URL>
```

| Param        | Required | Meaning |
|--------------|----------|---------|
| `ref`        | Recommended | Your own identifier, such as your user ID or cart ID. It comes back in the webhook as `order_reference`. |
| `return_url` | Optional | Where the "Return to App" button sends the customer after paying. Must be `http(s)://`. |

The customer enters their email on the Fluxpay page and pays.

```html
<a href="https://pay.fluxpay.example/pay/3f1c...e9?ref=user_42&return_url=https%3A%2F%2Fyoursite.com%2Fthanks">
  Buy Pro plan
</a>
```

### Option B: create a checkout session from your server

Use this when you already know the customer's email and want to skip the email step.

`POST API_HOST/api/v1/checkout/sessions`

```json
{
  "productId": "3f1c...e9",
  "customerEmail": "buyer@example.com",
  "successUrl": "https://yoursite.com/thanks?session_id={CHECKOUT_SESSION_ID}",
  "cancelUrl": "https://yoursite.com/pricing",
  "merchantReference": "user_42"
}
```

- `successUrl` and `cancelUrl` must start with `http://` or `https://`.
- `{CHECKOUT_SESSION_ID}` in `successUrl` is replaced with the real session ID.

Response:

```json
{
  "success": true,
  "data": {
    "sessionId": "cs_5b0e...",
    "checkoutUrl": "https://pay.fluxpay.example/checkout/cs_5b0e..."
  }
}
```

Redirect the customer to `checkoutUrl`. After a verified payment they are sent to `successUrl`, with `order_id=<fluxpay order id>` added to it.

```js
// Node.js (Express): your "Buy" button posts here
app.post("/buy", async (req, res) => {
  const r = await fetch(`${process.env.FLUXPAY_API_HOST}/api/v1/checkout/sessions`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      productId: process.env.FLUXPAY_PRODUCT_ID,
      customerEmail: req.user.email,
      successUrl: "https://yoursite.com/thanks?session_id={CHECKOUT_SESSION_ID}",
      cancelUrl: "https://yoursite.com/pricing",
      merchantReference: String(req.user.id),
    }),
  });
  const body = await r.json();
  if (!body.success) return res.status(502).send("Could not start checkout");
  res.redirect(303, body.data.checkoutUrl);
});
```

> Checkout sessions are currently kept in server memory and are lost if Fluxpay restarts. Create the session just before redirecting; don't store the URL to reuse later.

---

## 4. Receive the webhook (fulfil the order)

Fluxpay sends a `POST` to your endpoint with these headers:

| Header               | Value |
|----------------------|-------|
| `Content-Type`       | `application/json` |
| `X-Fluxpay-Event`    | `payment.succeeded` or `payment.failed` |
| `X-Fluxpay-Signature`| Base64 HMAC-SHA256 of the **raw request body**, keyed with your `whsec_...` secret |
| `X-Fluxpay-Event-Id` | Unique ID of the event. It stays the same on every retry, so use it to deduplicate. |
| `X-Fluxpay-Delivery-Attempt` | `1` for the first try, then `2`, `3`, … on retries |

### `payment.succeeded` payload

```json
{
  "payment_intent_id": "8d2b0c8e-...",
  "order_id": "a71f3d0c-...",
  "order_reference": "user_42_1a2b3c4d",
  "customer_email": "buyer@example.com",
  "amount": 499.00,
  "currency": "INR",
  "gateway": "RAZORPAY",
  "gateway_payment_id": "pay_Q8x...",
  "status": "SUCCESS"
}
```

- `order_reference` is your `ref` / `merchantReference` plus `_` and 8 random characters, so it's unique per attempt. To get your original value back, cut at the **last** underscore. If you sent no reference, it starts with `ORD_`.
- `amount` is in major units (rupees, not paise).

### `payment.failed` payload

```json
{ "payment_intent_id": "8d2b0c8e-...", "order_id": "a71f3d0c-...", "status": "FAILED" }
```

The customer can retry on the same checkout page, so a later `payment.succeeded` for the same `order_id` is normal.

### Rules your handler must follow

1. **Verify the signature against the raw body bytes.** Don't parse the JSON and re-serialize it before hashing: key order and spacing change, and the check will fail.
2. **Compare in constant time** (`crypto.timingSafeEqual`, `hmac.compare_digest`, `MessageDigest.isEqual`).
3. **Be idempotent.** Failed deliveries are retried, so you can receive the same event more than once. Record `X-Fluxpay-Event-Id` (or `payment_intent_id`) and ignore repeats.
4. **Respond 2xx within 10 seconds.** Grant the purchase, or queue it, then return `200`. Anything else, including a timeout, counts as a failure and will be retried.

### Node.js (Express)

```js
const crypto = require("crypto");
const express = require("express");
const app = express();

// express.raw keeps the exact bytes Fluxpay signed
app.post("/webhooks/fluxpay", express.raw({ type: "application/json" }), async (req, res) => {
  const expected = crypto
    .createHmac("sha256", process.env.FLUXPAY_WEBHOOK_SECRET)
    .update(req.body) // Buffer
    .digest("base64");
  const received = req.get("X-Fluxpay-Signature") || "";

  const ok =
    expected.length === received.length &&
    crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(received));
  if (!ok) return res.status(401).send("Invalid signature");

  const event = req.get("X-Fluxpay-Event");
  const data = JSON.parse(req.body.toString("utf8"));

  if (event === "payment.succeeded") {
    if (await alreadyProcessed(data.payment_intent_id)) return res.sendStatus(200);
    const yourRef = data.order_reference.slice(0, data.order_reference.lastIndexOf("_"));
    await grantPurchase(yourRef, data); // your logic
    await markProcessed(data.payment_intent_id);
  }
  res.sendStatus(200);
});
```

### Next.js (App Router route handler)

```ts
// app/api/webhooks/fluxpay/route.ts
import crypto from "crypto";

export async function POST(req: Request) {
  const raw = await req.text();
  const expected = crypto.createHmac("sha256", process.env.FLUXPAY_WEBHOOK_SECRET!).update(raw).digest("base64");
  const received = req.headers.get("x-fluxpay-signature") ?? "";
  if (expected.length !== received.length ||
      !crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(received))) {
    return new Response("Invalid signature", { status: 401 });
  }
  const data = JSON.parse(raw);
  if (req.headers.get("x-fluxpay-event") === "payment.succeeded") {
    // idempotency check + grant purchase
  }
  return new Response("OK");
}
```

### Python (Flask)

```python
import base64, hashlib, hmac, os
from flask import Flask, request, abort

app = Flask(__name__)
SECRET = os.environ["FLUXPAY_WEBHOOK_SECRET"].encode()

@app.post("/webhooks/fluxpay")
def fluxpay_webhook():
    raw = request.get_data()  # raw bytes, before any JSON parsing
    expected = base64.b64encode(hmac.new(SECRET, raw, hashlib.sha256).digest()).decode()
    if not hmac.compare_digest(expected, request.headers.get("X-Fluxpay-Signature", "")):
        abort(401)
    data = request.get_json()
    if request.headers.get("X-Fluxpay-Event") == "payment.succeeded":
        ...  # idempotency check + grant purchase
    return "OK", 200
```

### PHP

```php
<?php
$raw = file_get_contents('php://input');
$expected = base64_encode(hash_hmac('sha256', $raw, getenv('FLUXPAY_WEBHOOK_SECRET'), true));
if (!hash_equals($expected, $_SERVER['HTTP_X_FLUXPAY_SIGNATURE'] ?? '')) {
    http_response_code(401); exit('Invalid signature');
}
$data = json_decode($raw, true);
if (($_SERVER['HTTP_X_FLUXPAY_EVENT'] ?? '') === 'payment.succeeded') {
    // idempotency check + grant purchase
}
http_response_code(200); echo 'OK';
```

### Java (Spring Boot)

```java
@PostMapping("/webhooks/fluxpay")
public ResponseEntity<String> fluxpay(@RequestHeader("X-Fluxpay-Signature") String signature,
                                      @RequestHeader("X-Fluxpay-Event") String event,
                                      @RequestBody String rawBody) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(System.getenv("FLUXPAY_WEBHOOK_SECRET").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] expected = Base64.getEncoder().encode(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
        return ResponseEntity.status(401).body("Invalid signature");
    }
    if ("payment.succeeded".equals(event)) {
        // idempotency check + grant purchase
    }
    return ResponseEntity.ok("OK");
}
```

---

## 5. Testing (Razorpay test mode)

While Fluxpay runs with `rzp_test_` keys, no real money moves.

1. Open your payment link, or create a session, and click **Pay**.
2. In the Razorpay modal, use any test method from Razorpay's docs: https://razorpay.com/docs/payments/payments/test-card-details/
   - **Netbanking:** pick any bank, then click **Success** or **Failure** on the mock bank page.
   - **UPI:** use the test VPA `success@razorpay` (or `failure@razorpay`).
3. Check that:
   - your webhook endpoint received `payment.succeeded`;
   - the signature check passed;
   - you were redirected to your success URL.
4. Test the failure path, and close the modal without paying. You should see "Payment was cancelled", and no webhook is sent.

---

## 6. Go-live checklist

- [ ] Webhook handler verifies the signature on the raw body, in constant time.
- [ ] Handler is idempotent on `payment_intent_id`.
- [ ] You fulfil on the webhook, never on the redirect.
- [ ] Webhook secret is stored server-side only.
- [ ] Product prices and currency are correct (Fluxpay charges exactly the product price).
- [ ] You've tested success, failure and cancel end-to-end in test mode.

## Known limitations (current version)

- **Retry schedule.** If your endpoint doesn't return 2xx, Fluxpay retries after about 30 seconds, 2 minutes, 10 minutes, 30 minutes and 2 hours: 6 attempts over roughly 3 hours. After that the event is marked failed; contact Fluxpay support to have it resent.
- **Sessions live in memory.** A session created right before a Fluxpay restart can disappear (see Option B).
- **API keys are not enforced yet.** Keys generated in the dashboard aren't required by the API today. The integration above doesn't need them.

## Need help?

Contact Fluxpay developer support with the `order_id`, and the `gateway_payment_id` if you have one.
