package com.eia.payment.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(payment.sourceType()).isEqualTo("DIRECT_CLAIM");
        assertThat(payment.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(payment.sourceReference()).isEqualTo("CLM-2026-0000001");
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

    private static String paymentJson() {
        return """
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
                """;
    }
}
