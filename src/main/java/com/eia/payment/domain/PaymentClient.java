package com.eia.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class PaymentClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public PaymentClient(RestClient.Builder builder,
                         ObjectMapper objectMapper,
                         @Value("${eia.payment.expert-url:http://localhost:8097}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public List<PaymentDto> findAll(UUID tenantId) {
        try {
            List<PaymentDto> payments = client.get()
                    .uri(uri -> uri.path("/internal/v1/payments").queryParam("tenantId", tenantId).build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return payments == null ? List.of() : payments;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public PaymentDto findById(UUID tenantId, UUID id) {
        try {
            PaymentDto payment = client.get()
                    .uri(uri -> uri.path("/internal/v1/payments/{id}")
                            .queryParam("tenantId", tenantId).build(id))
                    .retrieve()
                    .body(PaymentDto.class);
            if (payment == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found: " + id);
            }
            return payment;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public PaymentDto create(UUID tenantId, CreatePaymentCommand command) {
        ExpertCreatePaymentRequest request = new ExpertCreatePaymentRequest(
                tenantId, null, command.paymentType(), command.status(), command.amount(),
                command.currency(), command.payeePartyId(), command.paymentDate());
        try {
            return client.post()
                    .uri("/internal/v1/payments")
                    .body(request)
                    .retrieve()
                    .body(PaymentDto.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public PaymentDto changeStatus(UUID tenantId, UUID id, String status, Integer expectedVersion, String reason) {
        try {
            return client.patch()
                    .uri(uri -> uri.path("/internal/v1/payments/{id}/status")
                            .queryParam("tenantId", tenantId).build(id))
                    .body(new StatusChangeRequest(status, expectedVersion, reason))
                    .retrieve()
                    .body(PaymentDto.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    private ApiException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String code = null;
        String message = null;
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            code = body.path("code").asText(null);
            message = body.path("message").asText(null);
        } catch (Exception ignored) {
            // Fall back to status-based information when the expert body is not JSON.
        }
        if (code == null || code.isBlank()) {
            code = status == 404 ? "PAYMENT_NOT_FOUND" : "EXPERT_ERROR";
        }
        if (message == null || message.isBlank()) {
            message = "Payment expert request failed with status " + status;
        }
        if (status == 404) {
            return new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", message);
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved != null && resolved.is4xxClientError()) {
            return new ApiException(resolved, code, message);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, "EXPERT_UNAVAILABLE",
                "Payment expert is unavailable (status " + status + ")");
    }

    record ExpertCreatePaymentRequest(
            UUID tenantId,
            String paymentNumber,
            String paymentType,
            String status,
            BigDecimal amount,
            String currency,
            UUID payeePartyId,
            LocalDate paymentDate
    ) {}

    record StatusChangeRequest(String status, Integer expectedVersion, String reason) {}
}
