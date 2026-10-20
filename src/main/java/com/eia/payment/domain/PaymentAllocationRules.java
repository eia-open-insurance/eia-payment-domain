package com.eia.payment.domain;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Shape validation and normalization for a payment allocation command.
 *
 * <p>The domain rejects a malformed split with a structured 400 before the
 * expert is called and normalizes the target and component vocabularies the
 * same way it normalizes the payment type. Aggregate rules that need stored
 * state — in particular PAY-06, that the allocations of a payment may not
 * exceed the payment amount, and the payment-status gate — stay in the expert
 * and surface as a 409.</p>
 */
public final class PaymentAllocationRules {
    /**
     * The business records a payment split can target. The vocabulary matches
     * the expert's {@code payment_allocation.target_type} check: an allocation
     * names either a claim (in one of the two books) or a policy.
     */
    public static final Set<String> TARGET_TYPES = Set.of(
            "DIRECT_CLAIM", "ASSUMED_CLAIM", "POLICY");

    /**
     * The financial components a payment split can carry, matching the
     * expert's {@code payment_allocation.component} check.
     */
    public static final Set<String> COMPONENTS = Set.of(
            "INDEMNITY", "DEFENSE_COST", "ALAE", "EXPENSE", "COMMISSION", "OTHER");

    private PaymentAllocationRules() {
    }

    public static CreatePaymentAllocationCommand normalize(CreatePaymentAllocationCommand command) {
        if (command == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Payment allocation validation failed",
                    List.of(new ApiException.FieldIssue("request", "request body is required")));
        }
        List<ApiException.FieldIssue> issues = new ArrayList<>();

        String targetType = token(command.targetType(), "targetType", "target type",
                TARGET_TYPES, issues);
        UUID targetId = command.targetId();
        if (targetId == null) {
            issues.add(new ApiException.FieldIssue("targetId", "targetId is required"));
        }
        String component = token(command.component(), "component", "component", COMPONENTS, issues);
        BigDecimal amount = amount(command.amount(), issues);

        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Payment allocation validation failed", issues);
        }
        return new CreatePaymentAllocationCommand(targetType, targetId, component, amount);
    }

    private static String token(String raw, String field, String label, Set<String> allowed,
                                List<ApiException.FieldIssue> issues) {
        if (raw == null || raw.isBlank()) {
            issues.add(new ApiException.FieldIssue(field, field + " is required"));
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            issues.add(new ApiException.FieldIssue(field, "Unknown " + label + ": " + raw));
            return null;
        }
        return value;
    }

    private static BigDecimal amount(BigDecimal raw, List<ApiException.FieldIssue> issues) {
        if (raw == null) {
            issues.add(new ApiException.FieldIssue("amount", "amount is required"));
            return null;
        }
        if (raw.signum() <= 0) {
            issues.add(new ApiException.FieldIssue("amount", "amount must be greater than zero"));
            return null;
        }
        BigDecimal normalized = raw.stripTrailingZeros();
        if (normalized.scale() > 4) {
            issues.add(new ApiException.FieldIssue("amount",
                    "amount must have at most 4 decimal places"));
            return null;
        }
        if (normalized.precision() - normalized.scale() > 15) {
            issues.add(new ApiException.FieldIssue("amount",
                    "amount is too large for numeric(19,4)"));
            return null;
        }
        return normalized.setScale(4, RoundingMode.UNNECESSARY);
    }
}
