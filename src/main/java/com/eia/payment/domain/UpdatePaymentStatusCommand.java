package com.eia.payment.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * Requested lifecycle transition. {@code expectedVersion} is optional; when
 * supplied, the expert rejects a stale value with a 409 problem.
 */
public record UpdatePaymentStatusCommand(
        @NotBlank String status,
        Integer expectedVersion,
        String reason
) {}
