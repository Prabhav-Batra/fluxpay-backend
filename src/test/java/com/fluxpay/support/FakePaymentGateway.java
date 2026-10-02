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
