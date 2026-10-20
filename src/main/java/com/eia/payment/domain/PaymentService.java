package com.eia.payment.domain;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {
    private static final int MAX_SEARCH_LIMIT = 25;
    private static final String SEARCH_TYPE = "PAYMENT";
    private static final String FIELD_SEPARATOR = " · ";

    private final PaymentClient client;

    public PaymentService(PaymentClient client) {
        this.client = client;
    }

    public List<PaymentDto> list(UUID tenantId) {
        return client.findAll(tenantId);
    }

    /**
     * Common global search. The tenant-scoped expert list is the only read path
     * available to the domain, so matching, ordering, and limiting happen here
     * and produce the shared search shape used by the shell.
     */
    public PaymentSearchResponse search(UUID tenantId, String q, int limit) {
        String query = q == null ? "" : q.trim();
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        List<PaymentDto> payments = client.findAll(tenantId);
        if (payments == null) {
            payments = List.of();
        }
        List<PaymentSearchItem> items = payments.stream()
                .filter(payment -> payment.tenantId() == null || tenantId.equals(payment.tenantId()))
                .filter(payment -> matches(payment, query))
                .sorted(Comparator.comparing(PaymentDto::updatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(effectiveLimit)
                .map(PaymentService::toSearchItem)
                .toList();
        return new PaymentSearchResponse(query, effectiveLimit, items);
    }

    public PaymentDto get(UUID tenantId, UUID id) {
        return client.findById(tenantId, id);
    }

    public PaymentDto create(UUID tenantId, CreatePaymentCommand command) {
        PaymentRules.validateCreate(command);
        CreatePaymentCommand normalized = new CreatePaymentCommand(
                PaymentRules.normalizePaymentType(command.paymentType()),
                PaymentRules.normalizeInitialStatus(command.status()),
                PaymentRules.normalizeAmount(command.amount()),
                PaymentRules.normalizeCurrency(command.currency()),
                command.payeePartyId(),
                command.paymentDate());
        return client.create(tenantId, normalized);
    }

    /** DRAFT -> PENDING_AUTHORITY. */
    public PaymentDto submit(UUID tenantId, UUID id) {
        PaymentDto payment = client.findById(tenantId, id);
        PaymentRules.requireStatus(payment.status(), "DRAFT", "submit");
        return client.changeStatus(tenantId, id, "PENDING_AUTHORITY", payment.version(),
                "submitted for approval");
    }

    /** PENDING_AUTHORITY -> AUTHORIZED. */
    public PaymentDto authorize(UUID tenantId, UUID id) {
        PaymentDto payment = client.findById(tenantId, id);
        PaymentRules.requireStatus(payment.status(), "PENDING_AUTHORITY", "authorize");
        return client.changeStatus(tenantId, id, "AUTHORIZED", payment.version(),
                "authorized by payment approver");
    }

    /** AUTHORIZED -> APPROVED. */
    public PaymentDto approve(UUID tenantId, UUID id) {
        PaymentDto payment = client.findById(tenantId, id);
        PaymentRules.requireStatus(payment.status(), "AUTHORIZED", "approve");
        return client.changeStatus(tenantId, id, "APPROVED", payment.version(),
                "approved for issuance");
    }

    /**
     * APPROVED -> SCHEDULED -> SUBMITTED, or SCHEDULED -> SUBMITTED for a retry.
     * The approval gate is checked before any execution transition is attempted.
     */
    public PaymentDto issue(UUID tenantId, UUID id) {
        PaymentDto payment = client.findById(tenantId, id);
        PaymentRules.requireReadyForExecution(payment.status());
        if ("APPROVED".equals(payment.status())) {
            payment = client.changeStatus(tenantId, id, "SCHEDULED", payment.version(),
                    "scheduled for payment run");
        }
        return client.changeStatus(tenantId, id, "SUBMITTED", payment.version(),
                "issued to SAP/bank");
    }

    private static boolean matches(PaymentDto payment, String query) {
        if (query.isEmpty()) {
            return true;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return contains(payment.paymentNumber(), needle)
                || contains(payment.paymentType(), needle)
                || contains(payment.payeePartyId() == null ? null : payment.payeePartyId().toString(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static PaymentSearchItem toSearchItem(PaymentDto payment) {
        String primary = isBlank(payment.paymentType()) ? payment.currency() : payment.paymentType();
        return new PaymentSearchItem(
                payment.id(),
                SEARCH_TYPE,
                payment.paymentNumber(),
                secondary(primary, payment.status()),
                payment.status(),
                "/payment?record=" + payment.id());
    }

    private static String secondary(String primary, String status) {
        if (isBlank(primary)) {
            return status;
        }
        if (isBlank(status)) {
            return primary;
        }
        return primary + FIELD_SEPARATOR + status;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
