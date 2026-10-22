package com.eia.payment.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * A payment's allocations plus the reconciliation view of its amount.
 *
 * <p>{@code totalAllocated} is the gross amount of every recorded split,
 * including the rows that were later reversed. {@code reversedTotal} is the
 * part of that total released by reversals, and {@code net} is the amount still
 * allocated: {@code totalAllocated - reversedTotal}. {@code unallocated} is the
 * payment amount still available for a new split.</p>
 *
 * <p>The expert's summary reports the net allocated total and the reversed
 * total, because reversed rows no longer consume the payment amount. The
 * domain presents the public shape above by adding the reversed total back to
 * the expert's net figure. When an older expert projection omits the reversal
 * figures entirely, they are derived from the rows. All money values are
 * decimal strings.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentAllocationSummary(
        List<PaymentAllocation> items,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal paymentAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalAllocated,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal reversedTotal,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal net,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unallocated,
        String currency
) {
    /** Compatibility constructor for summaries that predate allocation reversal. */
    public PaymentAllocationSummary(List<PaymentAllocation> items, BigDecimal paymentAmount,
                                    BigDecimal totalAllocated, BigDecimal unallocated,
                                    String currency) {
        this(items, paymentAmount, totalAllocated, null, null, unallocated, currency);
    }
}
