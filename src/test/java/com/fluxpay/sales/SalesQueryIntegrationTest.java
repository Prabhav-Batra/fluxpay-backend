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
        apiGet("/api/v1/sales?product_id=" + elitePack)
                .andExpect(jsonPath("$.data.length()").value(1));

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
                apiGet("/api/v1/sales?product_id=" + elitePack)
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
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
