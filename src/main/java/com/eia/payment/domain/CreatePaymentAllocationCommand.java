package com.eia.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request body for recording one payment allocation. The currency is not part
 * of the command: an allocation is always in the payment's currency, which the
 * expert takes from the payment row.
 */
public record CreatePaymentAllocationCommand(
        String targetType,
        UUID targetId,
        String component,
        BigDecimal amount
) {}
