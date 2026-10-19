package com.eia.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRulesTest {
    private static final UUID PAYEE = UUID.fromString("22222222-2222-7222-8222-222222222222");

    @Test
    void acceptsSpecPaymentTypes() {
        assertTrue(PaymentRules.PAYMENT_TYPES.contains("INDEMNITY"));
        assertTrue(PaymentRules.PAYMENT_TYPES.contains("TRUST_DISTRIBUTION"));
        assertTrue(PaymentRules.PAYMENT_TYPES.contains("TECHNICAL_ACCOUNT_SETTLEMENT"));
        assertEquals(25, PaymentRules.PAYMENT_TYPES.size());
    }

    @Test
    void acceptsSpecPaymentStatuses() {
        assertEquals(19, PaymentRules.STATUSES.size());
        assertTrue(PaymentRules.STATUSES.contains("PENDING_AUTHORITY"));
        assertTrue(PaymentRules.STATUSES.contains("RECONCILED"));
        assertTrue(PaymentRules.STATUSES.contains("ESCHEATED"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        ApiException ex = assertThrows(ApiException.class,
                () -> PaymentRules.normalizeAmount(new BigDecimal("0.0000")));
        assertEquals("INVALID_AMOUNT", ex.code());
        assertThrows(ApiException.class, () -> PaymentRules.normalizeAmount(new BigDecimal("-5")));
    }

    @Test
    void rejectsMoreThanFourDecimalPlaces() {
        assertThrows(ApiException.class,
                () -> PaymentRules.normalizeAmount(new BigDecimal("10.12345")));
        assertEquals(new BigDecimal("10.1235"), PaymentRules.normalizeAmount(new BigDecimal("10.1235")));
    }

    @Test
    void acceptsOnlyIso4217Currency() {
        assertEquals("USD", PaymentRules.normalizeCurrency("usd"));
        assertEquals("GBP", PaymentRules.normalizeCurrency("GBP"));
        assertEquals("INVALID_CURRENCY",
                assertThrows(ApiException.class, () -> PaymentRules.normalizeCurrency("ZZZ")).code());
        assertEquals("INVALID_CURRENCY",
                assertThrows(ApiException.class, () -> PaymentRules.normalizeCurrency("US")).code());
    }

    @Test
    void blocksExecutionBeforeApproval() {
        assertEquals("PAYMENT_NOT_APPROVED",
                assertThrows(ApiException.class,
                        () -> PaymentRules.requireReadyForExecution("DRAFT")).code());
        assertEquals("PAYMENT_NOT_APPROVED",
                assertThrows(ApiException.class,
                        () -> PaymentRules.requireReadyForExecution("PENDING_AUTHORITY")).code());
        assertEquals("PAYMENT_NOT_APPROVED",
                assertThrows(ApiException.class,
                        () -> PaymentRules.requireReadyForExecution("AUTHORIZED")).code());
    }

    @Test
    void allowsExecutionOnlyFromApprovedOrScheduled() {
        PaymentRules.requireReadyForExecution("APPROVED");
        PaymentRules.requireReadyForExecution("SCHEDULED");
        assertEquals("INVALID_STATUS_TRANSITION",
                assertThrows(ApiException.class,
                        () -> PaymentRules.requireReadyForExecution("PAID")).code());
    }

    @Test
    void requiresExpectedStatusForEachAction() {
        PaymentRules.requireStatus("DRAFT", "DRAFT", "submit");
        assertEquals("INVALID_STATUS_TRANSITION",
                assertThrows(ApiException.class,
                        () -> PaymentRules.requireStatus("APPROVED", "PENDING_AUTHORITY", "authorize")).code());
    }

    @Test
    void rejectsCreateWithUnknownStatusOrType() {
        CreatePaymentCommand badStatus = new CreatePaymentCommand("INDEMNITY", "PAID",
                new BigDecimal("10.00"), "USD", PAYEE, LocalDate.of(2026, 10, 19));
        assertEquals("VALIDATION_ERROR",
                assertThrows(ApiException.class, () -> PaymentRules.validateCreate(badStatus)).code());

        CreatePaymentCommand badType = new CreatePaymentCommand("BRIBE", null,
                new BigDecimal("10.00"), "USD", PAYEE, LocalDate.of(2026, 10, 19));
        assertEquals("VALIDATION_ERROR",
                assertThrows(ApiException.class, () -> PaymentRules.validateCreate(badType)).code());
    }
}
