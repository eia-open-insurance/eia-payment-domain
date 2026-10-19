package com.eia.payment.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentClient client;

    public PaymentService(PaymentClient client) {
        this.client = client;
    }

    public List<PaymentDto> list(UUID tenantId) {
        return client.findAll(tenantId);
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
}
