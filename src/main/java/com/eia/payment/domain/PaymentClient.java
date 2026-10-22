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
    public static final String TENANT_HEADER = "X-EIA-Tenant";

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

    /**
     * Tenant-scoped list, optionally narrowed to the payments linked to one
     * source. The filter is evaluated by the expert so the domain never holds a
     * second copy of the source linkage rules; expert 400 problems are preserved
     * for the caller.
     */
    public List<PaymentDto> findAll(UUID tenantId, String sourceType, String sourceId) {
        try {
            List<PaymentDto> payments = client.get()
                    .uri(uri -> {
                        var builder = uri.path("/internal/v1/payments").queryParam("tenantId", tenantId);
                        if (sourceType != null) {
                            builder.queryParam("sourceType", sourceType);
                        }
                        if (sourceId != null) {
                            builder.queryParam("sourceId", sourceId);
                        }
                        return builder.build();
                    })
                    .header(TENANT_HEADER, tenantId.toString())
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
                command.currency(), command.payeePartyId(), command.paymentDate(),
                command.sourceType(), command.sourceId(), command.sourceReference());
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

    /**
     * Forwards one lifecycle transition with the tenant in both the query and
     * the tenant header. The expert owns transition and version checks; the
     * caller receives its 400/404/409 problems unchanged.
     */
    public PaymentDto changeStatus(UUID tenantId, UUID id, String status, Integer expectedVersion, String reason) {
        try {
            return client.patch()
                    .uri(uri -> uri.path("/internal/v1/payments/{id}/status")
                            .queryParam("tenantId", tenantId).build(id))
                    .header(TENANT_HEADER, tenantId.toString())
                    .body(new StatusChangeRequest(status, expectedVersion, reason))
                    .retrieve()
                    .body(PaymentDto.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    /**
     * The expert owns the allocation rows, their ordering, and the payment
     * totals. The domain forwards the tenant and normalizes a missing row list
     * or missing totals so the public summary is always well formed.
     */
    public PaymentAllocationSummary findAllocations(UUID tenantId, UUID paymentId) {
        try {
            PaymentAllocationSummary summary = client.get()
                    .uri(uri -> uri.path("/internal/v1/payments/{id}/allocations")
                            .queryParam("tenantId", tenantId).build(paymentId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .retrieve()
                    .body(PaymentAllocationSummary.class);
            return normalize(summary);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    public PaymentAllocation createAllocation(UUID tenantId, UUID paymentId,
                                              CreatePaymentAllocationCommand command) {
        try {
            return client.post()
                    .uri(uri -> uri.path("/internal/v1/payments/{id}/allocations")
                            .queryParam("tenantId", tenantId).build(paymentId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .body(command)
                    .retrieve()
                    .body(PaymentAllocation.class);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        }
    }

    private static PaymentAllocationSummary normalize(PaymentAllocationSummary summary) {
        if (summary == null) {
            return new PaymentAllocationSummary(List.of(), null, BigDecimal.ZERO, null, null);
        }
        List<PaymentAllocation> items = summary.items() == null ? List.of() : summary.items();
        BigDecimal totalAllocated = summary.totalAllocated();
        if (totalAllocated == null) {
            totalAllocated = items.stream()
                    .map(PaymentAllocation::amount)
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal unallocated = summary.unallocated();
        if (unallocated == null && summary.paymentAmount() != null) {
            unallocated = summary.paymentAmount().subtract(totalAllocated);
        }
        return new PaymentAllocationSummary(items, summary.paymentAmount(), totalAllocated,
                unallocated, summary.currency());
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
            LocalDate paymentDate,
            String sourceType,
            UUID sourceId,
            String sourceReference
    ) {}

    record StatusChangeRequest(String status, Integer expectedVersion, String reason) {}
}
