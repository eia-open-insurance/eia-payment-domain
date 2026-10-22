package com.eia.payment.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(PaymentService.class)
class PaymentControllerTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID SOURCE_ID = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final UUID ALLOCATION_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");
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
    void exposesTheStatusReasonOnListAndGet() throws Exception {
        PaymentDto cancelled = payment("CANCELLED", 3, "duplicate claim");
        when(client.findAll(TENANT)).thenReturn(List.of(cancelled));
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(cancelled);

        mvc.perform(get("/api/v1/payments").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].statusReason").value("duplicate claim"));

        mvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusReason").value("duplicate claim"));
    }

    @Test
    void forwardsSourceFilterToTheExpert() throws Exception {
        when(client.findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString()))
                .thenReturn(List.of(paymentWithSource("DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001")));

        mvc.perform(get("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("sourceType", "DIRECT_CLAIM")
                        .param("sourceId", SOURCE_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$[0].sourceId").value(SOURCE_ID.toString()));

        verify(client).findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString());
        verify(client, never()).findAll(TENANT);
    }

    @Test
    void keepsUnfilteredListWhenNoSourceFilterIsSupplied() throws Exception {
        when(client.findAll(TENANT)).thenReturn(List.of(payment("DRAFT", 0)));

        mvc.perform(get("/api/v1/payments").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].paymentNumber").value("PAY-2026-0044721"));

        verify(client).findAll(TENANT);
        verify(client, never()).findAll(any(), any(), any());
    }

    @Test
    void preservesTheExpertSourceTypeWithoutSourceIdProblem() throws Exception {
        when(client.findAll(TENANT, "DIRECT_CLAIM", null)).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_SOURCE_FILTER",
                "sourceId is required when sourceType is supplied"));

        mvc.perform(get("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("sourceType", "DIRECT_CLAIM"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE_FILTER"))
                .andExpect(jsonPath("$.message")
                        .value("sourceId is required when sourceType is supplied"));

        verify(client).findAll(TENANT, "DIRECT_CLAIM", null);
    }

    @Test
    void preservesTheExpertSourceIdWithoutSourceTypeProblem() throws Exception {
        when(client.findAll(TENANT, null, SOURCE_ID.toString())).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_SOURCE_FILTER",
                "sourceType is required when sourceId is supplied"));

        mvc.perform(get("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("sourceId", SOURCE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE_FILTER"))
                .andExpect(jsonPath("$.message")
                        .value("sourceType is required when sourceId is supplied"));

        verify(client).findAll(TENANT, null, SOURCE_ID.toString());
    }

    @Test
    void preservesTheExpertUnknownSourceTypeProblem() throws Exception {
        when(client.findAll(TENANT, "BRIBE", SOURCE_ID.toString())).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_SOURCE_TYPE", "Unknown source type: BRIBE"));

        mvc.perform(get("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .param("sourceType", "BRIBE")
                        .param("sourceId", SOURCE_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE_TYPE"))
                .andExpect(jsonPath("$.message").value("Unknown source type: BRIBE"));

        verify(client).findAll(TENANT, "BRIBE", SOURCE_ID.toString());
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
    void createsPaymentWithSourceLinkage() throws Exception {
        when(client.create(eq(TENANT), any(CreatePaymentCommand.class)))
                .thenReturn(paymentWithSource("DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001"));

        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                "direct_claim", SOURCE_ID, "  CLM-2026-0000001  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sourceReference").value("CLM-2026-0000001"));

        ArgumentCaptor<CreatePaymentCommand> captor =
                ArgumentCaptor.forClass(CreatePaymentCommand.class);
        verify(client).create(eq(TENANT), captor.capture());
        assertThat(captor.getValue().sourceType()).isEqualTo("DIRECT_CLAIM");
        assertThat(captor.getValue().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(captor.getValue().sourceReference()).isEqualTo("CLM-2026-0000001");
    }

    @Test
    void createsPaymentWithoutSourceLinkage() throws Exception {
        when(client.create(eq(TENANT), any(CreatePaymentCommand.class))).thenReturn(payment("DRAFT", 0));

        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                command(new BigDecimal("125000.00"), "USD", "INDEMNITY"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value(nullValue()))
                .andExpect(jsonPath("$.sourceId").value(nullValue()))
                .andExpect(jsonPath("$.sourceReference").value(nullValue()));
    }

    @Test
    void readsSourceLinkageOnListAndGet() throws Exception {
        PaymentDto linked = paymentWithSource("POLICY", SOURCE_ID, "POL-2026-0000001");
        when(client.findAll(TENANT)).thenReturn(List.of(linked));
        when(client.findById(TENANT, PAYMENT_ID)).thenReturn(linked);

        mvc.perform(get("/api/v1/payments").header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("POLICY"))
                .andExpect(jsonPath("$[0].sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$[0].sourceReference").value("POL-2026-0000001"));

        mvc.perform(get("/api/v1/payments/{id}", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("POLICY"))
                .andExpect(jsonPath("$.sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sourceReference").value("POL-2026-0000001"));
    }

    @Test
    void rejectsSourceTypeWithoutSourceIdBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                "DIRECT_CLAIM", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("sourceId"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void rejectsSourceIdWithoutSourceTypeBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                null, SOURCE_ID, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("sourceType"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void rejectsUnknownSourceTypeBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                "BRIBE", SOURCE_ID, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("sourceType"))
                .andExpect(jsonPath("$.details[0].message").value("Unknown source type: BRIBE"));

        verify(client, never()).create(any(), any());
    }

    @Test
    void allowsOtherSourceReferenceWithoutSourceId() throws Exception {
        when(client.create(eq(TENANT), any(CreatePaymentCommand.class)))
                .thenReturn(paymentWithSource("OTHER", null, "CLM-2026-0000009"));

        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                "OTHER", null, "CLM-2026-0000009"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("OTHER"))
                .andExpect(jsonPath("$.sourceId").value(nullValue()))
                .andExpect(jsonPath("$.sourceReference").value("CLM-2026-0000009"));

        ArgumentCaptor<CreatePaymentCommand> captor =
                ArgumentCaptor.forClass(CreatePaymentCommand.class);
        verify(client).create(eq(TENANT), captor.capture());
        assertThat(captor.getValue().sourceId()).isNull();
        assertThat(captor.getValue().sourceReference()).isEqualTo("CLM-2026-0000009");
    }

    @Test
    void rejectsOverlongSourceReferenceBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments")
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                new BigDecimal("125000.00"), "USD", "INDEMNITY",
                                "POLICY", SOURCE_ID, "C".repeat(65)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("sourceReference"));

        verify(client, never()).create(any(), any());
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
                .thenReturn(payment("AUTHORIZED", 2, "authorized by payment approver"));

        mvc.perform(post("/api/v1/payments/{id}/authorize", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.statusReason").value("authorized by payment approver"))
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

    @Test
    void updatesStatusThroughTheExpert() throws Exception {
        when(client.changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 2, "duplicate claim"))
                .thenReturn(payment("CANCELLED", 3, "duplicate claim"));

        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED","expectedVersion":2,"reason":"duplicate claim"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.statusReason").value("duplicate claim"))
                .andExpect(jsonPath("$.version").value(3));

        verify(client).changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 2, "duplicate claim");
    }

    @Test
    void allowsStatusChangeWithoutExpectedVersion() throws Exception {
        when(client.changeStatus(TENANT, PAYMENT_ID, "PENDING_AUTHORITY", null, null))
                .thenReturn(payment("PENDING_AUTHORITY", 1));

        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"PENDING_AUTHORITY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_AUTHORITY"))
                .andExpect(jsonPath("$.statusReason").value(nullValue()))
                .andExpect(jsonPath("$.version").value(1));

        verify(client).changeStatus(TENANT, PAYMENT_ID, "PENDING_AUTHORITY", null, null);
    }

    @Test
    void rejectsBlankStatusBeforeCallingExpert() throws Exception {
        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"   ","expectedVersion":0,"reason":"blank"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("status"));

        verify(client, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    void requiresTenantHeaderForStatusChange() throws Exception {
        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));

        verify(client, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    void preservesTheExpertStatusBadRequest() throws Exception {
        when(client.changeStatus(TENANT, PAYMENT_ID, "SUBMITTED", 0, null))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_APPROVED",
                        "Payment must be approved before execution; current status is DRAFT"));

        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUBMITTED","expectedVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_APPROVED"))
                .andExpect(jsonPath("$.message").value(
                        "Payment must be approved before execution; current status is DRAFT"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/" + PAYMENT_ID + "/status"));
    }

    @Test
    void preservesTheExpertStatusConflict() throws Exception {
        when(client.changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 2, "duplicate claim"))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                        "Expected version 2 but payment is at version 3"));

        mvc.perform(patch("/api/v1/payments/{id}/status", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED","expectedVersion":2,"reason":"duplicate claim"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message")
                        .value("Expected version 2 but payment is at version 3"))
                .andExpect(jsonPath("$.path").value("/api/v1/payments/" + PAYMENT_ID + "/status"));
    }

    @Test
    void returnsTheAllocationSummary() throws Exception {
        when(client.findAllocations(TENANT, PAYMENT_ID)).thenReturn(allocationSummary());

        mvc.perform(get("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].allocationId").value(ALLOCATION_ID.toString()))
                .andExpect(jsonPath("$.items[0].paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].targetType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.items[0].targetId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.items[0].component").value("INDEMNITY"))
                .andExpect(jsonPath("$.items[0].amount").isString())
                .andExpect(jsonPath("$.items[0].amount").value("1250.0000"))
                .andExpect(jsonPath("$.items[0].currency").value("USD"))
                .andExpect(jsonPath("$.paymentAmount").value("125000.0000"))
                .andExpect(jsonPath("$.totalAllocated").value("1250.0000"))
                .andExpect(jsonPath("$.unallocated").value("123750.0000"))
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(client).findAllocations(TENANT, PAYMENT_ID);
    }

    @Test
    void createsAnAllocation() throws Exception {
        when(client.createAllocation(eq(TENANT), eq(PAYMENT_ID),
                any(CreatePaymentAllocationCommand.class))).thenReturn(allocation());

        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentAllocationCommand("direct_claim", SOURCE_ID,
                                        "indemnity", new BigDecimal("1250.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocationId").value(ALLOCATION_ID.toString()))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.targetType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.targetId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.component").value("INDEMNITY"))
                .andExpect(jsonPath("$.amount").value("1250.0000"))
                .andExpect(jsonPath("$.currency").value("USD"));

        ArgumentCaptor<CreatePaymentAllocationCommand> captor =
                ArgumentCaptor.forClass(CreatePaymentAllocationCommand.class);
        verify(client).createAllocation(eq(TENANT), eq(PAYMENT_ID), captor.capture());
        assertThat(captor.getValue().targetType()).isEqualTo("DIRECT_CLAIM");
        assertThat(captor.getValue().component()).isEqualTo("INDEMNITY");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("1250.0000");
        assertThat(captor.getValue().amount().scale()).isEqualTo(4);
    }

    @Test
    void rejectsAnIncompleteAllocationBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"DIRECT_CLAIM","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("targetId"))
                .andExpect(jsonPath("$.details[1].field").value("component"));

        verify(client, never()).createAllocation(any(), any(), any());
    }

    @Test
    void rejectsUnknownAllocationVocabularyBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentAllocationCommand("BRIBE", SOURCE_ID,
                                        "BRIBE", new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("targetType"))
                .andExpect(jsonPath("$.details[0].message").value("Unknown target type: BRIBE"))
                .andExpect(jsonPath("$.details[1].field").value("component"))
                .andExpect(jsonPath("$.details[1].message").value("Unknown component: BRIBE"));

        verify(client, never()).createAllocation(any(), any(), any());
    }

    @Test
    void rejectsANonPositiveAllocationAmountBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID,
                                        "INDEMNITY", BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("amount"));

        verify(client, never()).createAllocation(any(), any(), any());
    }

    @Test
    void preservesTheExpertAllocationNotFound() throws Exception {
        when(client.findAllocations(TENANT, PAYMENT_ID)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + PAYMENT_ID));

        mvc.perform(get("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Payment not found: " + PAYMENT_ID))
                .andExpect(jsonPath("$.path")
                        .value("/api/v1/payments/" + PAYMENT_ID + "/allocations"));
    }

    @Test
    void preservesTheExpertAllocationConflict() throws Exception {
        when(client.createAllocation(eq(TENANT), eq(PAYMENT_ID),
                any(CreatePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "ALLOCATION_EXCEEDS_PAYMENT",
                "Allocations would total 126000.0000 for payment amount 125000.0000"));

        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID,
                                        "INDEMNITY", new BigDecimal("126000.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_EXCEEDS_PAYMENT"))
                .andExpect(jsonPath("$.message")
                        .value("Allocations would total 126000.0000 for payment amount 125000.0000"));
    }

    @Test
    void preservesTheExpertNotAllocatableConflict() throws Exception {
        when(client.createAllocation(eq(TENANT), eq(PAYMENT_ID),
                any(CreatePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "PAYMENT_NOT_ALLOCATABLE",
                "Payment PAY-2026-0044721 is CANCELLED and cannot be allocated"));

        mvc.perform(post("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID,
                                        "INDEMNITY", new BigDecimal("1250.00")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOCATABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Payment PAY-2026-0044721 is CANCELLED and cannot be allocated"));
    }

    @Test
    void requiresTenantForAllocationRoutes() throws Exception {
        mvc.perform(get("/api/v1/payments/{id}/allocations", PAYMENT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));

        verify(client, never()).findAllocations(any(), any());
    }

    @Test
    void returnsReversalTotalsInTheAllocationSummary() throws Exception {
        when(client.findAllocations(TENANT, PAYMENT_ID)).thenReturn(reversedAllocationSummary());

        mvc.perform(get("/api/v1/payments/{id}/allocations", PAYMENT_ID)
                        .header("X-EIA-Tenant", TENANT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reversedAt").value(nullValue()))
                .andExpect(jsonPath("$.items[0].reversalReason").value(nullValue()))
                .andExpect(jsonPath("$.items[1].reversedAt").value("2026-10-22T08:00:00Z"))
                .andExpect(jsonPath("$.items[1].reversalReason").value("duplicate payment"))
                .andExpect(jsonPath("$.paymentAmount").value("125000.0000"))
                .andExpect(jsonPath("$.totalAllocated").value("2250.0000"))
                .andExpect(jsonPath("$.reversedTotal").value("1000.0000"))
                .andExpect(jsonPath("$.net").value("1250.0000"))
                .andExpect(jsonPath("$.unallocated").value("123750.0000"))
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(client).findAllocations(TENANT, PAYMENT_ID);
    }

    @Test
    void reversesAnAllocation() throws Exception {
        when(client.reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                any(ReversePaymentAllocationCommand.class))).thenReturn(reversedAllocation());

        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"  duplicate payment  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationId").value(ALLOCATION_ID.toString()))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.targetType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.component").value("INDEMNITY"))
                .andExpect(jsonPath("$.amount").value("1000.0000"))
                .andExpect(jsonPath("$.reversedAt").value("2026-10-22T08:00:00Z"))
                .andExpect(jsonPath("$.reversalReason").value("duplicate payment"));

        ArgumentCaptor<ReversePaymentAllocationCommand> captor =
                ArgumentCaptor.forClass(ReversePaymentAllocationCommand.class);
        verify(client).reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("  duplicate payment  ");
    }

    @Test
    void rejectsAReversalWithoutAReasonBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("reason"));

        verify(client, never()).reverseAllocation(any(), any(), any(),
                any(ReversePaymentAllocationCommand.class));
    }

    @Test
    void rejectsAReversalReasonLongerThanTwoHundredCharactersBeforeCallingExpert() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"%s"}
                                """.formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("reason"));

        verify(client, never()).reverseAllocation(any(), any(), any(),
                any(ReversePaymentAllocationCommand.class));
    }

    @Test
    void requiresTenantForAllocationReversal() throws Exception {
        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"duplicate payment"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_TENANT_HEADER"));

        verify(client, never()).reverseAllocation(any(), any(), any(),
                any(ReversePaymentAllocationCommand.class));
    }

    @Test
    void preservesTheExpertReversalBadRequest() throws Exception {
        when(client.reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                any(ReversePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, "INVALID_REVERSAL_REASON",
                "A reversal reason is required"));

        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"duplicate payment"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REVERSAL_REASON"))
                .andExpect(jsonPath("$.message").value("A reversal reason is required"))
                .andExpect(jsonPath("$.path").value(
                        "/api/v1/payments/" + PAYMENT_ID + "/allocations/" + ALLOCATION_ID
                                + "/reverse"));
    }

    @Test
    void preservesTheExpertReversalNotFound() throws Exception {
        when(client.reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                any(ReversePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, "ALLOCATION_NOT_FOUND",
                "Allocation not found: " + ALLOCATION_ID));

        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"duplicate payment"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ALLOCATION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Allocation not found: " + ALLOCATION_ID));
    }

    @Test
    void preservesTheExpertReversalConflict() throws Exception {
        when(client.reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                any(ReversePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "ALLOCATION_ALREADY_REVERSED",
                "Allocation " + ALLOCATION_ID + " is already reversed"));

        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"duplicate payment"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_REVERSED"))
                .andExpect(jsonPath("$.message").value(
                        "Allocation " + ALLOCATION_ID + " is already reversed"));
    }

    @Test
    void preservesTheExpertReversalWhenThePaymentIsNotAllocatable() throws Exception {
        when(client.reverseAllocation(eq(TENANT), eq(PAYMENT_ID), eq(ALLOCATION_ID),
                any(ReversePaymentAllocationCommand.class))).thenThrow(new ApiException(
                HttpStatus.CONFLICT, "PAYMENT_NOT_ALLOCATABLE",
                "Payment PAY-2026-0044721 is CANCELLED and cannot be allocated"));

        mvc.perform(post("/api/v1/payments/{id}/allocations/{allocationId}/reverse",
                        PAYMENT_ID, ALLOCATION_ID)
                        .header("X-EIA-Tenant", TENANT.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"duplicate payment"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_ALLOCATABLE"))
                .andExpect(jsonPath("$.message").value(
                        "Payment PAY-2026-0044721 is CANCELLED and cannot be allocated"));
    }

    private static CreatePaymentCommand command(BigDecimal amount, String currency, String paymentType) {
        return command(amount, currency, paymentType, null, null, null);
    }

    private static CreatePaymentCommand command(BigDecimal amount, String currency, String paymentType,
                                                String sourceType, UUID sourceId, String sourceReference) {
        return new CreatePaymentCommand(paymentType, null, amount, currency, PAYEE, PAYMENT_DATE,
                sourceType, sourceId, sourceReference);
    }

    private static PaymentDto payment(String status, int version) {
        return payment(status, version, null);
    }

    private static PaymentDto payment(String status, int version, String statusReason) {
        return new PaymentDto(PAYMENT_ID, TENANT, "PAY-2026-0044721", "INDEMNITY", status,
                statusReason, new BigDecimal("125000.0000"), "USD", PAYEE, PAYMENT_DATE, version,
                CREATED, CREATED, null, null, null);
    }

    private static PaymentDto paymentWithSource(String sourceType, UUID sourceId, String sourceReference) {
        return new PaymentDto(PAYMENT_ID, TENANT, "PAY-2026-0044721", "INDEMNITY", "DRAFT", null,
                new BigDecimal("125000.0000"), "USD", PAYEE, PAYMENT_DATE, 0, CREATED, CREATED,
                sourceType, sourceId, sourceReference);
    }

    private static PaymentAllocation allocation() {
        return new PaymentAllocation(ALLOCATION_ID, PAYMENT_ID, "DIRECT_CLAIM", SOURCE_ID, "INDEMNITY",
                new BigDecimal("1250.0000"), "USD", CREATED);
    }

    private static PaymentAllocation reversedAllocation() {
        return new PaymentAllocation(ALLOCATION_ID, PAYMENT_ID, "DIRECT_CLAIM", SOURCE_ID, "INDEMNITY",
                new BigDecimal("1000.0000"), "USD", CREATED,
                Instant.parse("2026-10-22T08:00:00Z"), "duplicate payment");
    }

    private static PaymentAllocationSummary allocationSummary() {
        return new PaymentAllocationSummary(List.of(allocation()), new BigDecimal("125000.0000"),
                new BigDecimal("1250.0000"), new BigDecimal("123750.0000"), "USD");
    }

    private static PaymentAllocationSummary reversedAllocationSummary() {
        return new PaymentAllocationSummary(List.of(allocation(), reversedAllocation()),
                new BigDecimal("125000.0000"), new BigDecimal("2250.0000"),
                new BigDecimal("1000.0000"), new BigDecimal("1250.0000"),
                new BigDecimal("123750.0000"), "USD");
    }
}
