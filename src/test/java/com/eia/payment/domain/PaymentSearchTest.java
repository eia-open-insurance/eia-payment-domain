package com.eia.payment.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentService.class)
class PaymentSearchTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID OTHER_TENANT = UUID.fromString("99999999-9999-7999-8999-999999999999");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID SOURCE_ID = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 10, 19);
    private static final Instant BASE = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PaymentClient client;

    @Test
    void matchesByPaymentNumber() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.randomUUID(), BASE.plusSeconds(5))));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "pay-2026-0000001")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void matchesByPaymentTypeCaseInsensitively() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.randomUUID(), BASE)));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "indemnity")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void matchesByPayeePartyIdContains() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.fromString("44444444-4444-7444-8444-444444444444"), BASE)));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "2222-7222")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void matchesBySourceIdAsText() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE,
                        "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001"),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.randomUUID(), BASE.plusSeconds(5))));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "44444444-4444-7444-8444-444444444444")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void matchesByPartialSourceId() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE,
                        "POLICY", SOURCE_ID, "POL-2026-0000001"),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.randomUUID(), BASE.plusSeconds(5))));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "4444-7444")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void matchesBySourceReferenceCaseInsensitively() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE,
                        "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001"),
                payment(UUID.randomUUID(), TENANT, "PAY-2026-0000002", "EXPENSE", "DRAFT",
                        UUID.randomUUID(), BASE.plusSeconds(5))));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "clm-2026-0000001")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));
    }

    @Test
    void appendsSourceReferenceToSecondaryLine() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(
                payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE,
                        "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001")));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "CLM-2026-0000001")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].secondary")
                        .value("INDEMNITY · DRAFT · CLM-2026-0000001"));
    }

    @Test
    void blankQueryReturnsLatestTenByUpdatedAtDesc() throws Exception {
        when(client.findAll(TENANT)).thenReturn(payments(12));

        mvc.perform(get("/api/v1/payments/search").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value(""))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].label").value("PAY-2026-0000012"))
                .andExpect(jsonPath("$.items[9].label").value("PAY-2026-0000003"));
    }

    @Test
    void appliesRequestedLimit() throws Exception {
        when(client.findAll(TENANT)).thenReturn(payments(5));

        mvc.perform(get("/api/v1/payments/search")
                        .param("limit", "2")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].label").value("PAY-2026-0000005"))
                .andExpect(jsonPath("$.items[1].label").value("PAY-2026-0000004"));
    }

    @Test
    void capsLimitAtTwentyFive() throws Exception {
        when(client.findAll(TENANT)).thenReturn(payments(30));

        mvc.perform(get("/api/v1/payments/search")
                        .param("limit", "100")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(25))
                .andExpect(jsonPath("$.items.length()").value(25));
    }

    @Test
    void clampsNonPositiveLimit() throws Exception {
        when(client.findAll(TENANT)).thenReturn(payments(3));

        mvc.perform(get("/api/v1/payments/search")
                        .param("limit", "0")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void doesNotReturnAnotherTenantsPayments() throws Exception {
        PaymentDto mine = payment(PAYMENT_ID, TENANT, "PAY-2026-0000001", "INDEMNITY", "DRAFT",
                PAYEE, BASE);
        PaymentDto foreign = payment(UUID.randomUUID(), OTHER_TENANT, "PAY-2026-0000099",
                "INDEMNITY", "DRAFT", PAYEE, BASE.plusSeconds(30));
        when(client.findAll(TENANT)).thenReturn(List.of(mine, foreign));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "indemnity")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()));

        verify(client).findAll(TENANT);
    }

    @Test
    void returnsCommonProjectionAndHref() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(payment(PAYMENT_ID, TENANT,
                "PAY-2026-0000001", "INDEMNITY", "DRAFT", PAYEE, BASE)));

        mvc.perform(get("/api/v1/payments/search")
                        .param("q", "pay-2026-0000001")
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("pay-2026-0000001"))
                .andExpect(jsonPath("$.limit").value(10))
                .andExpect(jsonPath("$.items[0].id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.items[0].label").value("PAY-2026-0000001"))
                .andExpect(jsonPath("$.items[0].secondary").value("INDEMNITY · DRAFT"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].href").value("/payment?record=" + PAYMENT_ID));
    }

    @Test
    void rejectsRequestsWithoutTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/payments/search").param("q", "indemnity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));
    }

    private static List<PaymentDto> payments(int count) {
        List<PaymentDto> payments = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            payments.add(payment(UUID.randomUUID(), TENANT, "PAY-2026-%07d".formatted(i),
                    "INDEMNITY", "DRAFT", PAYEE, BASE.plusSeconds(i)));
        }
        return payments;
    }

    private static PaymentDto payment(UUID id, UUID tenantId, String number, String type, String status,
                                      UUID payee, Instant updatedAt) {
        return payment(id, tenantId, number, type, status, payee, updatedAt, null, null, null);
    }

    private static PaymentDto payment(UUID id, UUID tenantId, String number, String type, String status,
                                      UUID payee, Instant updatedAt,
                                      String sourceType, UUID sourceId, String sourceReference) {
        return new PaymentDto(id, tenantId, number, type, status, null, new BigDecimal("125000.0000"),
                "USD", payee, PAYMENT_DATE, 0, BASE, updatedAt,
                sourceType, sourceId, sourceReference);
    }
}
