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
        String merchantId =
                TestMerchants.signUp(mockMvc, "Jextter", "owner@jextter.com").merchantId();
        UUID id = PublicId.parse(IdPrefix.MERCHANT, merchantId).orElseThrow();
        return new TenantContext(id, Mode.TEST);
    }

    @Test
    void should_store_event_when_published_inside_transaction() throws Exception {
        TenantContext tenant = tenant();

        EventView event = transactionTemplate.execute(
                status -> eventPublisher.publish(tenant, EventType.CHECKOUT_COMPLETED, Map.of("sale_id", "sale_x")));

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

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isZero();
    }
}
