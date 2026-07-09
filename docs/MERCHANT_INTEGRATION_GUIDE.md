# Fluxpay Merchant Integration Guide

Welcome to Fluxpay! This guide will walk you through integrating the Fluxpay checkout experience into your application (e.g., e-commerce store, SaaS platform, etc.).

By the end of this guide, you will be able to:
1. Accept payments via Fluxpay.
2. Securely verify payment success using Webhooks.

---

## 1. Authentication & Setup

To communicate with Fluxpay's APIs, you need a Merchant **API Key**. 
*(If you do not have one, generate it from your Fluxpay Merchant Dashboard).*

All backend API requests to Fluxpay must include your API Key in the headers:
```http
Authorization: Bearer <YOUR_API_KEY>
Content-Type: application/json
```

---

## 2. The Checkout Flow

Fluxpay uses a **Hosted Checkout** model. This means you don't have to worry about handling sensitive credit card data. You simply create an order on your backend, and redirect the user to our secure checkout page.

### Step 2.1: Create a Payment Intent (Your Backend)

When your user clicks "Checkout", your backend should call Fluxpay to initiate the payment.

**POST** `https://api.fluxpay.com/v1/payments/process`
```json
{
  "orderId": "your-internal-order-uuid",
  "preferredGateway": "CASHFREE",
  "returnUrl": "https://your-website.com/checkout/success"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Payment processing initiated",
  "data": {
    "id": "pay_intent_uuid",
    "orderId": "your-internal-order-uuid",
    "amount": 5000,
    "currency": "USD",
    "status": "INITIATED",
    "paymentLink": "https://checkout.fluxpay.com/pay/..."
  }
}
```

### Step 2.2: Redirect the User (Your Frontend)

Take the `paymentLink` from the response above and redirect your user's browser to it. 
The user will enter their payment details on the Fluxpay hosted page. Once completed, they will be redirected back to the `returnUrl` you provided.

---

## 3. Webhooks (Fulfilling the Order)

Users can sometimes close their browser before being redirected back to your `returnUrl`. To reliably fulfill orders, **you must implement Webhooks.**

Fluxpay will send an asynchronous HTTP `POST` request to your server when the payment succeeds or fails.

### Step 3.1: Register your Webhook Endpoint

Call Fluxpay to register where you want to receive webhooks:

**POST** `https://api.fluxpay.com/v1/webhooks/endpoints`
```json
{
  "merchantId": "<YOUR_MERCHANT_UUID>",
  "url": "https://api.your-website.com/webhooks/fluxpay"
}
```

**Response:**
Save the returned `secretKey` securely on your backend (e.g., in your `.env` file). You will need it to verify incoming webhooks.

### Step 3.2: Implement the Webhook Receiver (Your Backend)

When a payment succeeds, Fluxpay will `POST` to your URL. 
You must verify the `X-Fluxpay-Signature` header to ensure the request is authentically from Fluxpay.

#### Example Payload from Fluxpay
```json
{
  "payment_intent_id": "pay_intent_uuid",
  "order_id": "your-internal-order-uuid",
  "amount": 5000,
  "currency": "USD",
  "status": "SUCCESS"
}
```

#### Node.js / Express Implementation Example
```javascript
const express = require('express');
const crypto = require('crypto');
const app = express();

const FLUXPAY_WEBHOOK_SECRET = process.env.FLUXPAY_WEBHOOK_SECRET;

app.post('/webhooks/fluxpay', express.json(), (req, res) => {
    const signature = req.headers['x-fluxpay-signature'];
    
    // Hash the raw JSON body using HMAC SHA-256
    const myHash = crypto.createHmac('sha256', FLUXPAY_WEBHOOK_SECRET)
                         .update(JSON.stringify(req.body))
                         .digest('base64');
                         
    if (myHash !== signature) {
        console.error("Invalid Webhook Signature!");
        return res.status(401).send("Unauthorized");
    }
    
    const event = req.body;
    if (event.status === 'SUCCESS') {
        // TODO: Mark order as PAID in your database!
        // database.markOrderPaid(event.order_id);
    }
    
    // Always return 200 OK so Fluxpay knows you received it
    res.status(200).send("OK");
});
```

#### Java / Spring Boot Implementation Example
```java
import org.springframework.web.bind.annotation.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@RestController
public class FluxpayWebhookController {

    private final String WEBHOOK_SECRET = System.getenv("FLUXPAY_WEBHOOK_SECRET");

    @PostMapping("/webhooks/fluxpay")
    public ResponseEntity<String> handleWebhook(
            @RequestHeader("X-Fluxpay-Signature") String signature,
            @RequestBody String rawPayload) throws Exception {

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(WEBHOOK_SECRET.getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        String calculatedSignature = Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(rawPayload.getBytes()));

        if (!calculatedSignature.equals(signature)) {
            return ResponseEntity.status(401).body("Invalid Signature");
        }

        // Parse rawPayload into JSON and process the success event
        // ...

        return ResponseEntity.ok("OK");
    }
}
```

---

## Need Help?
If you encounter any issues during integration, please contact Fluxpay developer support.
