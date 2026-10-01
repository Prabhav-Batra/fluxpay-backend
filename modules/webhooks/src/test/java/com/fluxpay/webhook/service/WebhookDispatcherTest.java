package com.fluxpay.webhook.service;

import com.fluxpay.webhook.entity.WebhookDelivery;
import com.fluxpay.webhook.entity.WebhookDeliveryStatus;
import com.fluxpay.webhook.entity.WebhookEndpoint;
import com.fluxpay.webhook.repository.WebhookDeliveryRepository;
import com.fluxpay.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookDispatcherTest {

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookEndpointRepository endpoints = mock(WebhookEndpointRepository.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final WebhookDispatcher dispatcher = new WebhookDispatcher(deliveries, endpoints, scheduler, restTemplate);

    private WebhookDelivery delivery;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatcher, "directSecret", "whsec_direct");
        delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID()).eventId(UUID.randomUUID()).merchantId(UUID.randomUUID())
                .url("https://merchant.example/hook").eventType("payment.succeeded")
                .payload("{\"status\":\"SUCCESS\"}").status(WebhookDeliveryStatus.PENDING).attempts(0)
                .build();
        when(deliveries.findById(delivery.getId())).thenReturn(Optional.of(delivery));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulDeliveryIsSignedAndMarkedDelivered() throws Exception {
        when(restTemplate.postForEntity(eq(delivery.getUrl()), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        dispatcher.attempt(delivery.getId());

        ArgumentCaptor<HttpEntity<String>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(delivery.getUrl()), request.capture(), eq(String.class));
        var headers = request.getValue().getHeaders();
        // Same signature scheme merchants already verify: base64 HMAC-SHA256 of the raw body
        assertEquals(WebhookDispatcher.sign(delivery.getPayload(), "whsec_direct"), headers.getFirst("X-Fluxpay-Signature"));
        assertEquals("payment.succeeded", headers.getFirst("X-Fluxpay-Event"));
        assertEquals(delivery.getEventId().toString(), headers.getFirst("X-Fluxpay-Event-Id"));
        assertEquals("1", headers.getFirst("X-Fluxpay-Delivery-Attempt"));

        assertEquals(WebhookDeliveryStatus.DELIVERED, delivery.getStatus());
        assertEquals(200, delivery.getLastResponseStatus());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void failedDeliveryIsRetriedWithBackoff() {
        when(restTemplate.postForEntity(eq(delivery.getUrl()), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE));

        Instant before = Instant.now();
        dispatcher.attempt(delivery.getId());

        assertEquals(WebhookDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(1, delivery.getAttempts());
        assertEquals(503, delivery.getLastResponseStatus());
        assertTrue(!delivery.getNextAttemptAt().isBefore(before.plus(Duration.ofSeconds(30))));
        verify(scheduler).schedule(any(Runnable.class), eq(delivery.getNextAttemptAt()));
    }

    @Test
    void givesUpAfterLastAttempt() {
        delivery.setAttempts(5);
        when(restTemplate.postForEntity(eq(delivery.getUrl()), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));

        dispatcher.attempt(delivery.getId());

        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(6, delivery.getAttempts());
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void deactivatedEndpointFailsWithoutSending() {
        UUID endpointId = UUID.randomUUID();
        delivery.setEndpointId(endpointId);
        WebhookEndpoint inactive = WebhookEndpoint.builder().id(endpointId).secretKey("whsec_x").active(false).build();
        when(endpoints.findById(endpointId)).thenReturn(Optional.of(inactive));

        dispatcher.attempt(delivery.getId());

        assertEquals(WebhookDeliveryStatus.FAILED, delivery.getStatus());
        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void alreadyDeliveredIsNotResent() {
        delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
        dispatcher.attempt(delivery.getId());
        verify(restTemplate, never()).postForEntity(any(String.class), any(), eq(String.class));
    }

    @Test
    void dispatchWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatchAfterCommit(List.of(delivery.getId()));
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void pendingDeliveriesAreResumedOnStartup() {
        delivery.setNextAttemptAt(Instant.now().minusSeconds(60));
        when(deliveries.findByStatus(WebhookDeliveryStatus.PENDING)).thenReturn(List.of(delivery));

        dispatcher.resumePendingDeliveries();

        ArgumentCaptor<Instant> when = ArgumentCaptor.forClass(Instant.class);
        verify(scheduler).schedule(any(Runnable.class), when.capture());
        assertNotNull(when.getValue());
        assertTrue(!when.getValue().isBefore(delivery.getNextAttemptAt()));
    }
}
