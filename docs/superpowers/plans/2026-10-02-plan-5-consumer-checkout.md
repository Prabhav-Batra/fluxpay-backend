# Plan 5: Consumer Checkout Pages

**Goal:** A consumer can visit a checkout link (`/c/:id`), view product and merchant branding details, and complete a payment using the Razorpay gateway.

## Features & Implementation

1. **Checkout Public Page (`/c/[id]`)**:
   - The user visits `/c/[id]` (e.g. `/c/cs_12345`).
   - The page calls `GET /api/v1/public/checkout_sessions/[id]` to retrieve the session details.
   - The UI reflects the product details (name, description, image, price in ₹) and merchant branding (logo, brand color).
   - Shows status-specific states:
     - **Active**: Shows the "Pay Now" button.
     - **Expired**: Shows an "Expired" message.
     - **Completed**: Shows an "Already Paid" message or redirects to `successUrl`.

2. **Razorpay Integration**:
   - When the user clicks "Pay Now", call `POST /api/v1/public/checkout_sessions/[id]/pay` to retrieve `PaymentInstructionsResponse` (includes `orderId`, `keyId`, etc.).
   - Load the Razorpay Checkout script dynamically (`https://checkout.razorpay.com/v1/checkout.js`).
   - Initialize Razorpay with the retrieved options (`key`, `order_id`, `amount`, `name`, `description`).
   - On success (`handler` callback), immediately redirect the user to `successUrl` (or a fallback success page).
   - On modal close/dismiss, just let the user stay on the page.

3. **Testing Requirements**:
   - Test rendering of the active checkout page (branding, product info, formatting paise).
   - Test the "Expired" edge case (UI shows expired state, pay button hidden).
   - Test the "Completed" edge case (UI shows already paid, pay button hidden).
   - Test "Pay Now" flow: verifying that `pay` API is called and the Razorpay script is initialized.
   - Test payment failure/modal close logic if applicable.
