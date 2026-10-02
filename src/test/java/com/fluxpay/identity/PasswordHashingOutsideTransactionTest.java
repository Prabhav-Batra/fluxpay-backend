package com.fluxpay.identity;

import static com.fluxpay.support.TestMerchants.jsonPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fluxpay.support.AbstractIntegrationTest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** bcrypt is slow; running it inside a transaction pins a pool connection for its whole duration. */
class PasswordHashingOutsideTransactionTest extends AbstractIntegrationTest {

    private static final String SIGNUP =
            "{\"business_name\":\"Jextter\",\"email\":\"owner@jextter.com\",\"password\":\"correct-horse\"}";
    private static final String LOGIN = "{\"email\":\"owner@jextter.com\",\"password\":\"correct-horse\"}";

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    private final List<Boolean> transactionActiveDuringHashing = new CopyOnWriteArrayList<>();

    @BeforeEach
    void recordTransactionState() {
        doAnswer(invocation -> {
                    transactionActiveDuringHashing.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .when(passwordEncoder)
                .encode(any());
        doAnswer(invocation -> {
                    transactionActiveDuringHashing.add(TransactionSynchronizationManager.isActualTransactionActive());
                    return invocation.callRealMethod();
                })
                .when(passwordEncoder)
                .matches(any(), any());
    }

    @Test
    void should_hash_outside_transaction_when_signing_up() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", SIGNUP));

        assertThat(transactionActiveDuringHashing).isNotEmpty().containsOnly(false);
    }

    @Test
    void should_verify_password_outside_transaction_when_logging_in() throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/signup", SIGNUP));
        transactionActiveDuringHashing.clear();

        mockMvc.perform(jsonPost("/api/v1/auth/login", LOGIN));

        assertThat(transactionActiveDuringHashing).isNotEmpty().containsOnly(false);
    }
}
