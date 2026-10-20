package com.eia.payment.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One split of a payment amount to the claim or policy target and financial
 * component it settles (SPEC 11.7.4 {@code payment_allocation}).
 *
 * <p>The projection uses the public {@code allocationId} field and omits the
 * tenant, which is implied by the request context. The expert serves the same
 * allocation contract, so the client reads it unchanged. Money is a decimal
 * string (SPEC 10.3).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentAllocation(
        UUID allocationId,
        UUID paymentId,
        String targetType,
        UUID targetId,
        String component,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        Instant createdAt
) {}
