package com.fluxpay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fluxpay.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class HealthEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void should_report_up_when_liveness_probe_is_called() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void should_report_up_when_readiness_probe_is_called_with_database_available() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
