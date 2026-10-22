package com.eia.payment.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Wire-contract test for the expert request/response used by the domain. */
class PaymentClientTest {
    private static final UUID TENANT = UUID.fromString("11111111-1111-7111-8111-111111111111");
    private static final UUID PAYEE = UUID.fromString("22222222-2222-7222-8222-222222222222");
    private static final UUID PAYMENT_ID = UUID.fromString("33333333-3333-7333-8333-333333333333");
    private static final UUID SOURCE_ID = UUID.fromString("44444444-4444-7444-8444-444444444444");
    private static final UUID ALLOCATION_ID = UUID.fromString("55555555-5555-7555-8555-555555555555");

    @Test
    void sendsAndReadsSourceLinkage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.tenantId").value(TENANT.toString()))
                .andExpect(jsonPath("$.sourceType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.sourceId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.sourceReference").value("CLM-2026-0000001"))
                .andRespond(withSuccess("""
                        {
                          "id": "33333333-3333-7333-8333-333333333333",
                          "tenantId": "11111111-1111-7111-8111-111111111111",
                          "paymentNumber": "PAY-2026-0044721",
                          "paymentType": "INDEMNITY",
                          "status": "DRAFT",
                          "amount": "125000.0000",
                          "currency": "USD",
                          "payeePartyId": "22222222-2222-7222-8222-222222222222",
                          "paymentDate": "2026-10-19",
                          "version": 0,
                          "createdAt": "2026-10-19T12:00:00Z",
                          "updatedAt": "2026-10-19T12:00:00Z",
                          "sourceType": "DIRECT_CLAIM",
                          "sourceId": "44444444-4444-7444-8444-444444444444",
                          "sourceReference": "CLM-2026-0000001"
                        }
                        """, MediaType.APPLICATION_JSON));

        PaymentDto payment = client.create(TENANT, new CreatePaymentCommand(
                "INDEMNITY", null, new BigDecimal("125000.00"), "USD", PAYEE,
                LocalDate.of(2026, 10, 19), "DIRECT_CLAIM", SOURCE_ID, "CLM-2026-0000001"));

        assertThat(payment.id()).isEqualTo(PAYMENT_ID);
        assertThat(payment.statusReason()).isNull();
        assertThat(payment.sourceType()).isEqualTo("DIRECT_CLAIM");
        assertThat(payment.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(payment.sourceReference()).isEqualTo("CLM-2026-0000001");
        server.verify();
    }

    @Test
    void readsTheStatusReasonFromTheExpert() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(paymentJson("CANCELLED", 4, "duplicate claim"),
                        MediaType.APPLICATION_JSON));

        PaymentDto payment = client.findById(TENANT, PAYMENT_ID);

        assertThat(payment.status()).isEqualTo("CANCELLED");
        assertThat(payment.statusReason()).isEqualTo("duplicate claim");
        server.verify();
    }

    @Test
    void forwardsSourceFilterToExpertAndReadsPayments() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments?tenantId=" + TENANT
                        + "&sourceType=DIRECT_CLAIM&sourceId=" + SOURCE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withSuccess("[" + paymentJson() + "]", MediaType.APPLICATION_JSON));

        List<PaymentDto> payments = client.findAll(TENANT, "DIRECT_CLAIM", SOURCE_ID.toString());

        assertThat(payments).singleElement().satisfies(payment -> {
            assertThat(payment.id()).isEqualTo(PAYMENT_ID);
            assertThat(payment.sourceType()).isEqualTo("DIRECT_CLAIM");
            assertThat(payment.sourceId()).isEqualTo(SOURCE_ID);
        });
        server.verify();
    }

    @Test
    void forwardsAPartialFilterSoTheExpertOwnsThePairRule() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments?tenantId=" + TENANT
                        + "&sourceType=DIRECT_CLAIM"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"code":"INVALID_SOURCE_FILTER",
                                 "message":"sourceId is required when sourceType is supplied",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, "DIRECT_CLAIM", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_SOURCE_FILTER");
                    assertThat(api.getMessage())
                            .isEqualTo("sourceId is required when sourceType is supplied");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertUnknownSourceTypeProblem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments?tenantId=" + TENANT
                        + "&sourceType=BRIBE&sourceId=" + SOURCE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"code":"INVALID_SOURCE_TYPE",
                                 "message":"Unknown source type: BRIBE",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, "BRIBE", SOURCE_ID.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_SOURCE_TYPE");
                    assertThat(api.getMessage()).isEqualTo("Unknown source type: BRIBE");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertSourceIdWithoutTypeProblem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments?tenantId=" + TENANT
                        + "&sourceId=" + SOURCE_ID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"code":"INVALID_SOURCE_FILTER",
                                 "message":"sourceType is required when sourceId is supplied",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.findAll(TENANT, null, SOURCE_ID.toString()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_SOURCE_FILTER");
                    assertThat(api.getMessage())
                            .isEqualTo("sourceType is required when sourceId is supplied");
                });
        server.verify();
    }

    @Test
    void forwardsStatusChangeWithTenantAndExactPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/status?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(content().json("""
                        {"status":"CANCELLED","expectedVersion":3,"reason":"duplicate claim"}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(paymentJson("CANCELLED", 4, "duplicate claim")));

        PaymentDto payment = client.changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 3,
                "duplicate claim");

        assertThat(payment.status()).isEqualTo("CANCELLED");
        assertThat(payment.statusReason()).isEqualTo("duplicate claim");
        assertThat(payment.version()).isEqualTo(4);
        server.verify();
    }

    @Test
    void forwardsAStatusChangeWithoutOptionalVersionOrReason() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/status?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(content().json("""
                        {"status":"PENDING_AUTHORITY","expectedVersion":null,"reason":null}
                        """, JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(paymentJson("PENDING_AUTHORITY", 1)));

        PaymentDto payment = client.changeStatus(TENANT, PAYMENT_ID, "PENDING_AUTHORITY", null, null);

        assertThat(payment.status()).isEqualTo("PENDING_AUTHORITY");
        assertThat(payment.statusReason()).isNull();
        assertThat(payment.version()).isEqualTo(1);
        server.verify();
    }

    @Test
    void preservesTheExpertStatusBadRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/status?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"code":"INVALID_STATUS_TRANSITION",
                                 "message":"Cannot transition payment from DRAFT to SUBMITTED",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.changeStatus(TENANT, PAYMENT_ID, "SUBMITTED", 0, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("INVALID_STATUS_TRANSITION");
                    assertThat(api.getMessage())
                            .isEqualTo("Cannot transition payment from DRAFT to SUBMITTED");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertStatusNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/status?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":404,"code":"PAYMENT_NOT_FOUND",
                                 "message":"Payment not found: 33333333-3333-7333-8333-333333333333",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 0, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("PAYMENT_NOT_FOUND");
                    assertThat(api.getMessage()).contains("Payment not found");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertStatusConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/status?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":409,"code":"CONCURRENT_MODIFICATION",
                                 "message":"Expected version 2 but payment is at version 3",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.changeStatus(TENANT, PAYMENT_ID, "CANCELLED", 2,
                "duplicate claim"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("CONCURRENT_MODIFICATION");
                    assertThat(api.getMessage())
                            .isEqualTo("Expected version 2 but payment is at version 3");
                });
        server.verify();
    }

    @Test
    void readsTheAllocationSummaryWithTenantForwarded() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/allocations?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withSuccess(allocationSummaryJson(), MediaType.APPLICATION_JSON));

        PaymentAllocationSummary summary = client.findAllocations(TENANT, PAYMENT_ID);

        assertThat(summary.paymentAmount()).isEqualByComparingTo("125000.0000");
        assertThat(summary.totalAllocated()).isEqualByComparingTo("1250.0000");
        assertThat(summary.unallocated()).isEqualByComparingTo("123750.0000");
        assertThat(summary.currency()).isEqualTo("USD");
        assertThat(summary.items()).singleElement().satisfies(allocation -> {
            assertThat(allocation.allocationId()).isEqualTo(ALLOCATION_ID);
            assertThat(allocation.paymentId()).isEqualTo(PAYMENT_ID);
            assertThat(allocation.targetType()).isEqualTo("DIRECT_CLAIM");
            assertThat(allocation.targetId()).isEqualTo(SOURCE_ID);
            assertThat(allocation.component()).isEqualTo("INDEMNITY");
            assertThat(allocation.amount()).isEqualByComparingTo("1250.0000");
            assertThat(allocation.currency()).isEqualTo("USD");
            assertThat(allocation.createdAt()).isEqualTo(Instant.parse("2026-10-20T09:15:00Z"));
        });
        server.verify();
    }

    @Test
    void createsAnAllocationWithTenantForwarded() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/allocations?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andExpect(jsonPath("$.targetType").value("DIRECT_CLAIM"))
                .andExpect(jsonPath("$.targetId").value(SOURCE_ID.toString()))
                .andExpect(jsonPath("$.component").value("INDEMNITY"))
                .andExpect(jsonPath("$.amount").value(1250.0000))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(allocationJson()));

        PaymentAllocation allocation = client.createAllocation(TENANT, PAYMENT_ID,
                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID, "INDEMNITY",
                        new BigDecimal("1250.00")));

        assertThat(allocation.allocationId()).isEqualTo(ALLOCATION_ID);
        assertThat(allocation.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(allocation.targetType()).isEqualTo("DIRECT_CLAIM");
        assertThat(allocation.targetId()).isEqualTo(SOURCE_ID);
        assertThat(allocation.component()).isEqualTo("INDEMNITY");
        assertThat(allocation.amount()).isEqualByComparingTo("1250.0000");
        assertThat(allocation.currency()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void preservesTheExpertAllocationBadRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/allocations?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":400,"code":"VALIDATION_ERROR",
                                 "message":"Payment allocation validation failed",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.createAllocation(TENANT, PAYMENT_ID,
                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID, "BRIBE",
                        new BigDecimal("10.00"))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.code()).isEqualTo("VALIDATION_ERROR");
                    assertThat(api.getMessage()).isEqualTo("Payment allocation validation failed");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertAllocationNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/allocations?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":404,"code":"PAYMENT_NOT_FOUND",
                                 "message":"Payment not found: 33333333-3333-7333-8333-333333333333",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.findAllocations(TENANT, PAYMENT_ID))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("PAYMENT_NOT_FOUND");
                    assertThat(api.getMessage()).contains("Payment not found");
                });
        server.verify();
    }

    @Test
    void preservesTheExpertAllocationConflict() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentClient client = new PaymentClient(builder, new ObjectMapper(), "http://localhost:8097");

        server.expect(requestTo("http://localhost:8097/internal/v1/payments/" + PAYMENT_ID
                        + "/allocations?tenantId=" + TENANT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PaymentClient.TENANT_HEADER, TENANT.toString()))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":409,"code":"ALLOCATION_EXCEEDS_PAYMENT",
                                 "message":"Allocations would total 126000.0000 for payment amount 125000.0000",
                                 "path":"/internal/v1/payments","details":[]}
                                """));

        assertThatThrownBy(() -> client.createAllocation(TENANT, PAYMENT_ID,
                new CreatePaymentAllocationCommand("DIRECT_CLAIM", SOURCE_ID, "INDEMNITY",
                        new BigDecimal("126000.00"))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("ALLOCATION_EXCEEDS_PAYMENT");
                    assertThat(api.getMessage()).contains("126000.0000");
                });
        server.verify();
    }

    private static String allocationSummaryJson() {
        return """
                {
                  "items": [%s],
                  "paymentAmount": "125000.0000",
                  "totalAllocated": "1250.0000",
                  "unallocated": "123750.0000",
                  "currency": "USD"
                }
                """.formatted(allocationJson());
    }

    /** The expert's public allocation item already uses the public field names. */
    private static String allocationJson() {
        return """
                {
                  "allocationId": "55555555-5555-7555-8555-555555555555",
                  "paymentId": "33333333-3333-7333-8333-333333333333",
                  "targetType": "DIRECT_CLAIM",
                  "targetId": "44444444-4444-7444-8444-444444444444",
                  "component": "INDEMNITY",
                  "amount": "1250.0000",
                  "currency": "USD",
                  "createdAt": "2026-10-20T09:15:00Z"
                }
                """;
    }

    private static String paymentJson() {
        return paymentJson("DRAFT", 0);
    }

    private static String paymentJson(String status, int version) {
        return paymentJson(status, version, null);
    }

    private static String paymentJson(String status, int version, String statusReason) {
        return """
                {
                  "id": "33333333-3333-7333-8333-333333333333",
                  "tenantId": "11111111-1111-7111-8111-111111111111",
                  "paymentNumber": "PAY-2026-0044721",
                  "paymentType": "INDEMNITY",
                  "status": "%s",
                  "statusReason": %s,
                  "amount": "125000.0000",
                  "currency": "USD",
                  "payeePartyId": "22222222-2222-7222-8222-222222222222",
                  "paymentDate": "2026-10-19",
                  "version": %d,
                  "createdAt": "2026-10-19T12:00:00Z",
                  "updatedAt": "2026-10-19T12:00:00Z",
                  "sourceType": "DIRECT_CLAIM",
                  "sourceId": "44444444-4444-7444-8444-444444444444",
                  "sourceReference": "CLM-2026-0000001"
                }
                """.formatted(status, statusReason == null ? "null" : "\"" + statusReason + "\"", version);
    }
}
