package com.goledger.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goledger.domain.Tenant;
import com.goledger.service.AdminService;
import com.goledger.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AdminService adminService;
    @Autowired ObjectMapper objectMapper;

    private String tenantKey;

    @BeforeEach
    void setUp() {
        Tenant tenant = adminService.createTenant("mockmvc-tenant-" + UUID.randomUUID());
        UUID tenantId = tenant.getId();
        // A key with read+post but *not* admin, matching how a real
        // application would be provisioned - separate from the bootstrap
        // admin key. See BootstrapRunner for how that one is minted.
        tenantKey = adminService.issueKey(tenantId, "app-key", List.of("read", "post"), null).plaintextKey();
    }

    @Test
    void requestWithoutAuthorizationHeaderIsRejected() throws Exception {
        mockMvc.perform(get("/v1/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithABogusKeyIsRejected() throws Exception {
        mockMvc.perform(get("/v1/accounts").header("Authorization", "Bearer glk_not_a_real_key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keyWithoutAdminScopeCannotHitAdminEndpoints() throws Exception {
        mockMvc.perform(post("/v1/admin/tenants")
                        .header("Authorization", "Bearer " + tenantKey)
                        .contentType("application/json")
                        .content("{\"name\":\"nope\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullHappyPathThroughRealHttpEndpoints() throws Exception {
        String checkingId = createAccount("Checking", "asset");
        String incomeId = createAccount("Income", "income");

        String idemKey = UUID.randomUUID().toString();
        String txnBody = """
                {"currency":"USD","postings":[
                  {"accountId":"%s","amount":1000,"description":"deposit"},
                  {"accountId":"%s","amount":-1000,"description":"deposit"}
                ]}""".formatted(checkingId, incomeId);

        String firstResponse = mockMvc.perform(post("/v1/transactions")
                        .header("Authorization", "Bearer " + tenantKey)
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(txnBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String txnId = objectMapper.readTree(firstResponse).get("id").asText();

        // Replaying with the same Idempotency-Key over real HTTP must not double-post.
        String secondResponse = mockMvc.perform(post("/v1/transactions")
                        .header("Authorization", "Bearer " + tenantKey)
                        .header("Idempotency-Key", idemKey)
                        .contentType("application/json")
                        .content(txnBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(secondResponse).get("id").asText()).isEqualTo(txnId);

        mockMvc.perform(get("/v1/accounts/" + checkingId + "/balance")
                        .header("Authorization", "Bearer " + tenantKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000));

        mockMvc.perform(get("/v1/reports/trial-balance")
                        .header("Authorization", "Bearer " + tenantKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balanced").value(true));
    }

    @Test
    void unbalancedTransactionIsRejectedWith422() throws Exception {
        String checkingId = createAccount("Checking2", "asset");
        String incomeId = createAccount("Income2", "income");

        String badBody = """
                {"currency":"USD","postings":[
                  {"accountId":"%s","amount":1000,"description":"bad"},
                  {"accountId":"%s","amount":-999,"description":"bad"}
                ]}""".formatted(checkingId, incomeId);

        mockMvc.perform(post("/v1/transactions")
                        .header("Authorization", "Bearer " + tenantKey)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content(badBody))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void missingIdempotencyKeyIsRejectedEvenForAnOtherwiseValidTransaction() throws Exception {
        // Postings here are valid and balanced on purpose: this isolates the
        // missing-header check from the unrelated @Size(min = 2) bean
        // validation on the postings list, which would otherwise short-circuit
        // with a 400 before the controller ever looked at the header.
        String checkingId = createAccount("NoKeyChecking", "asset");
        String incomeId = createAccount("NoKeyIncome", "income");
        String body = """
                {"currency":"USD","postings":[
                  {"accountId":"%s","amount":100,"description":"x"},
                  {"accountId":"%s","amount":-100,"description":"x"}
                ]}""".formatted(checkingId, incomeId);

        mockMvc.perform(post("/v1/transactions")
                        .header("Authorization", "Bearer " + tenantKey)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    private String createAccount(String name, String type) throws Exception {
        String response = mockMvc.perform(post("/v1/accounts")
                        .header("Authorization", "Bearer " + tenantKey)
                        .contentType("application/json")
                        .content("{\"name\":\"%s\",\"type\":\"%s\",\"currency\":\"USD\"}".formatted(name, type)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }
}
