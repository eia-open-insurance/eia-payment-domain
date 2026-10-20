package com.eia.payment.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * A payment's allocations plus the reconciliation view of its amount: the
 * payment total, the sum already allocated, and the amount still unallocated.
 * The expert owns the persisted totals and the per-payment overallocation guard
 * (PAY-06); the domain proxies and normalizes the read model. All money values
 * are decimal strings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentAllocationSummary(
        List<PaymentAllocation> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paymentAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAllocated,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unallocated,
        String currency
) {}
