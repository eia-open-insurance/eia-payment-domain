package com.eia.payment.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentService.class)
class PaymentControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 10, 19);
    private static final Instant CREATED = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentClient client;

    @Test
    void listsPaymentsForTenant() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(payment("DRAFT", 0)));

        mvc.perform(get("/api/v1/payments").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentNumber").value("PAY-2026-0044721"))
                .andExpect(jsonPath("$[0].paymentType").value("INDEMNITY"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"))
                .andExpect(jsonPath("$[0].amount").value("125000.0000"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
    }

    @Test
    void requiresTenantHeader() throws Exception {
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));
    }

    @Test
    void createsPayment() throws Exception {
        when(client.create(eq(TENANT), any(CreatePaymentCommand.class))).thenReturn(payment("DRAFT", 0));

        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                command(new BigDecimal("125000.00"), "USD", "INDEMNITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentNumber").value("PAY-2026-0044721"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.payeePartyId").value(PAYEE.toString()));

        verify(client).create(eq(TENANT), any(CreatePaymentCommand.class));
    }

    @Test
    void rejectsZeroAmountBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                command(BigDecimal.ZERO, "USD", "INDEMNITY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("amount"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void rejectsNonIsoCurrencyBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                command(new BigDecimal("10.00"), "ZZZ", "INDEMNITY"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("currency"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void rejectsUnknownPaymentTypeBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                command(new BigDecimal("10.00"), "USD", "BRIBE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("paymentType"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void returnsStructuredNotFoundFromExpert() throws Exception {
        when(client.findById(TENANT, PAYMENT_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + PAYMENT_ID));

        mvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/" + PAYMENT_ID));
    }

    @Test
    void blocksIssueBeforeApproval() throws Exception {
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(payment("DRAFT", 0));

        mvc.perform(post("/api/v1/payments/{id}/issue", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_APPROVED"));

        verify(client, never()).changeStatus(any(), any(), anyString(), anyInt(), anyString());
    }

    @Test
    void issuesApprovedPaymentThroughScheduled() throws Exception {
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(payment("APPROVED", 3));
        when(client.changeStatus(TENANT, PAYMENT_ID, "SCHEDULED", 3, "scheduled for payment run"))
                .thenReturn(payment("SCHEDULED", 4));
        when(client.changeStatus(TENANT, PAYMENT_ID, "SUBMITTED", 4, "issued to SAP/bank"))
                .thenReturn(payment("SUBMITTED", 5));

        mvc.perform(post("/api/v1/payments/{id}/issue", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.version").value(5));

        verify(client).changeStatus(TENANT, PAYMENT_ID, "SCHEDULED", 3, "scheduled for payment run");
        verify(client).changeStatus(TENANT, PAYMENT_ID, "SUBMITTED", 4, "issued to SAP/bank");
    }

    @Test
    void authorizesPendingPayment() throws Exception {
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(payment("PENDING_AUTHORITY", 1));
        when(client.changeStatus(TENANT, PAYMENT_ID, "AUTHORIZED", 1, "authorized by payment approver"))
                .thenReturn(payment("AUTHORIZED", 2));

        mvc.perform(post("/api/v1/payments/{id}/authorize", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void rejectsAuthorizeFromWrongStatus() throws Exception {
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(payment("DRAFT", 0));

        mvc.perform(post("/api/v1/payments/{id}/authorize", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        verify(client, never()).changeStatus(any(), any(), anyString(), anyInt(), anyString());
    }

    private static CreatePaymentCommand command(BigDecimal amount, String currency, String paymentType) {
        return new CreatePaymentCommand(paymentType, null, amount, currency, PAYEE, PAYMENT_DATE);
    }

    private static PaymentDto payment(String status, int version) {
        return new PaymentDto(PAYMENT_ID, TENANT, "PAY-2026-0044721", "INDEMNITY", status,
                new BigDecimal("125000.0000"), "USD", PAYEE, PAYMENT_DATE, version, CREATED, CREATED);
    }
}
