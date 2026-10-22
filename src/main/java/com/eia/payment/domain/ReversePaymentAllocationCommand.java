package com.eia.payment.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reverses one recorded payment allocation, releasing it from the payment's
 * net allocated balance while keeping the original split auditable.
 *
 * <p>{@code reason} explains the reversal and is required. The domain rejects
 * a missing or blank reason before the expert is called; the expert owns the
 * business rules (the allocation must still be active, and the payment and
 * allocation must belong to the caller's tenant) and its 400/404/409 problems
 * are preserved for the caller.</p>
 */
public record ReversePaymentAllocationCommand(
        @NotBlank @Size(max = 200) String reason
) {}
