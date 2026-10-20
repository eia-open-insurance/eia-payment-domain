package com.eia.payment.domain;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Business validation and state-transition rules owned by the Payment domain.
 *
 * <p>The expert repeats the data-shape checks as a persistence guard; the domain
 * is the authority for lifecycle decisions and the approval gate. SPEC 11.7.7
 * PAY-01 requires a positive amount, SPEC 10.3 requires ISO 4217 currency, and
 * the SPEC 11.7.6 lifecycle requires approval before SUBMITTED and later states.</p>
 */
public final class PaymentRules {
    public static final Set<String> PAYMENT_TYPES = Set.of(
            "INDEMNITY", "DEFENSE_COST", "ALAE", "EXPENSE", "MEDICAL",
            "STRUCTURED_SETTLEMENT", "ANNUITY", "LIEN_PAYMENT", "TRUST_DEPOSIT",
            "TRUST_DISTRIBUTION", "SUBROGATION_RECOVERY", "SALVAGE_RECOVERY",
            "ASSUMED_RECOVERY", "DEDUCTIBLE_REIMBURSEMENT", "PREMIUM", "COMMISSION",
            "BROKERAGE", "CASH_CALL", "TECHNICAL_ACCOUNT_SETTLEMENT",
            "SECURITY_COLLATERAL", "REFUND", "OFFSET", "INTERCOMPANY", "TAX", "FEE"
    );

    public static final Set<String> STATUSES = Set.of(
            "DRAFT", "PENDING_AUTHORITY", "AUTHORIZED", "APPROVED", "SCHEDULED",
            "SUBMITTED", "ACCEPTED", "PROCESSING", "PAID", "PARTIALLY_PAID",
            "FAILED", "REJECTED", "CANCELLED", "STOPPED", "RETURNED", "REVERSED",
            "RECONCILED", "ESCHEATED", "VOID"
    );

    public static final Set<String> INITIAL_STATUSES = Set.of("DRAFT", "PENDING_AUTHORITY");
    public static final Set<String> PRE_APPROVAL_STATUSES = Set.of("DRAFT", "PENDING_AUTHORITY", "AUTHORIZED");

    /** Payment source linkage types (claim, policy, or an unclassified source). */
    public static final Set<String> SOURCE_TYPES = Set.of(
            "DIRECT_CLAIM", "ASSUMED_CLAIM", "POLICY", "OTHER"
    );

    /** Human-readable source references (for example CLM-2026-0000001). */
    public static final int SOURCE_REFERENCE_MAX_LENGTH = 64;

    private PaymentRules() {
    }

    public static void validateCreate(CreatePaymentCommand command) {
        List<ApiException.FieldIssue> issues = new ArrayList<>();

        if (command.paymentType() == null || command.paymentType().isBlank()) {
            issues.add(new ApiException.FieldIssue("paymentType", "paymentType is required"));
        } else if (!PAYMENT_TYPES.contains(command.paymentType().trim().toUpperCase(Locale.ROOT))) {
            issues.add(new ApiException.FieldIssue("paymentType",
                    "Unknown payment type: " + command.paymentType()));
        }

        if (command.amount() == null) {
            issues.add(new ApiException.FieldIssue("amount", "amount is required"));
        } else if (command.amount().signum() <= 0) {
            issues.add(new ApiException.FieldIssue("amount", "amount must be greater than zero"));
        } else {
            BigDecimal normalized = command.amount().stripTrailingZeros();
            if (normalized.scale() > 4) {
                issues.add(new ApiException.FieldIssue("amount", "amount must have at most 4 decimal places"));
            } else if (normalized.precision() - normalized.scale() > 15) {
                issues.add(new ApiException.FieldIssue("amount", "amount is too large for numeric(19,4)"));
            }
        }

        if (command.currency() == null || command.currency().isBlank()) {
            issues.add(new ApiException.FieldIssue("currency", "currency is required"));
        } else {
            try {
                normalizeCurrency(command.currency());
            } catch (ApiException ex) {
                issues.add(new ApiException.FieldIssue("currency", ex.getMessage()));
            }
        }

        validateSourceLinkage(command.sourceType(), command.sourceId(), command.sourceReference(), issues);

        if (command.status() != null && !command.status().isBlank()) {
            String status = command.status().trim().toUpperCase(Locale.ROOT);
            if (!STATUSES.contains(status)) {
                issues.add(new ApiException.FieldIssue("status", "Unknown payment status: " + command.status()));
            } else if (!INITIAL_STATUSES.contains(status)) {
                issues.add(new ApiException.FieldIssue("status",
                        "A payment must be created as DRAFT or PENDING_AUTHORITY and advanced through the lifecycle"));
            }
        }

        if (command.payeePartyId() == null) {
            issues.add(new ApiException.FieldIssue("payeePartyId", "payeePartyId is required"));
        }
        if (command.paymentDate() == null) {
            issues.add(new ApiException.FieldIssue("paymentDate", "paymentDate is required"));
        }

        if (!issues.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Payment validation failed", issues);
        }
    }

    /**
     * Source linkage is all-or-nothing for classified sources: a claim or policy
     * source always carries both a type and an id. OTHER is the escape hatch for
     * a source identified only by its human reference. sourceReference is
     * optional metadata and never replaces sourceId for a classified source.
     */
    private static void validateSourceLinkage(String rawType, UUID sourceId, String rawReference,
                                              List<ApiException.FieldIssue> issues) {
        if (rawType == null || rawType.isBlank()) {
            if (sourceId != null) {
                issues.add(new ApiException.FieldIssue("sourceType",
                        "sourceType is required when sourceId is supplied"));
            }
        } else {
            String sourceType = rawType.trim().toUpperCase(Locale.ROOT);
            if (!SOURCE_TYPES.contains(sourceType)) {
                issues.add(new ApiException.FieldIssue("sourceType", "Unknown source type: " + rawType));
            } else if (!"OTHER".equals(sourceType) && sourceId == null) {
                issues.add(new ApiException.FieldIssue("sourceId",
                        "sourceId is required when sourceType is " + sourceType));
            }
        }

        if (rawReference != null && rawReference.trim().length() > SOURCE_REFERENCE_MAX_LENGTH) {
            issues.add(new ApiException.FieldIssue("sourceReference",
                    "sourceReference must be at most " + SOURCE_REFERENCE_MAX_LENGTH + " characters"));
        }
    }

    public static String normalizePaymentType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_TYPE", "paymentType is required");
        }
        String type = rawType.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_TYPES.contains(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_TYPE",
                    "Unknown payment type: " + rawType);
        }
        return type;
    }

    public static String normalizeInitialStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "DRAFT";
        }
        String status = normalizeStatus(rawStatus);
        if (!INITIAL_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INITIAL_STATUS",
                    "A payment must be created as DRAFT or PENDING_AUTHORITY and advanced through the lifecycle");
        }
        return status;
    }

    public static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "status is required");
        }
        String status = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown payment status: " + rawStatus);
        }
        return status;
    }

    public static String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY", "currency is required");
        }
        String currency = rawCurrency.trim().toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY",
                    "currency must be a 3-letter ISO 4217 code: " + rawCurrency);
        }
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY",
                    "currency is not an ISO 4217 code: " + rawCurrency);
        }
        return currency;
    }

    public static BigDecimal normalizeAmount(BigDecimal rawAmount) {
        if (rawAmount == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "amount is required");
        }
        if (rawAmount.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "amount must be greater than zero");
        }
        BigDecimal normalized = rawAmount.stripTrailingZeros();
        if (normalized.scale() > 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                    "amount must have at most 4 decimal places");
        }
        if (normalized.precision() - normalized.scale() > 15) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT",
                    "amount is too large for numeric(19,4)");
        }
        return normalized.setScale(4, RoundingMode.UNNECESSARY);
    }

    public static String normalizeSourceType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        String sourceType = rawType.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_TYPES.contains(sourceType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_TYPE",
                    "Unknown source type: " + rawType);
        }
        return sourceType;
    }

    public static String normalizeSourceReference(String rawReference) {
        if (rawReference == null || rawReference.isBlank()) {
            return null;
        }
        String reference = rawReference.trim();
        if (reference.length() > SOURCE_REFERENCE_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_REFERENCE",
                    "sourceReference must be at most " + SOURCE_REFERENCE_MAX_LENGTH + " characters");
        }
        return reference;
    }

    public static void requireStatus(String currentStatus, String expectedStatus, String action) {
        if (!expectedStatus.equals(currentStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Cannot " + action + " a payment in status " + currentStatus
                            + "; expected " + expectedStatus);
        }
    }

    /**
     * Approval gate: SUBMITTED and later execution states are reachable only after
     * the payment has passed APPROVED (or is already SCHEDULED by a payment run).
     */
    public static void requireReadyForExecution(String currentStatus) {
        if (PRE_APPROVAL_STATUSES.contains(currentStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_APPROVED",
                    "Payment must be approved before execution; current status is " + currentStatus);
        }
        if (!"APPROVED".equals(currentStatus) && !"SCHEDULED".equals(currentStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Cannot issue a payment in status " + currentStatus);
        }
    }
}
