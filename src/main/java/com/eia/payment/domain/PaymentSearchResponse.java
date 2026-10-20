package com.eia.payment.domain;

import java.util.List;

/**
 * Common global-search envelope. {@code query} echoes the trimmed term and
 * {@code limit} is the effective limit after the default/cap rules are applied.
 */
public record PaymentSearchResponse(
        String query,
        int limit,
        List<PaymentSearchItem> items
) {}
