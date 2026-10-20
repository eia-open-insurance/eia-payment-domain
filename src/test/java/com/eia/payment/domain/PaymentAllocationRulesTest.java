package com.eia.payment.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentAllocationRulesTest {
    private static final UUID TARGET = UUID.fromString("44444444-4444-7444-8444-444444444444");

    @Test
    void normalizesTheTargetTheComponentAndTheAmount() {
        CreatePaymentAllocationCommand command = PaymentAllocationRules.normalize(
                new CreatePaymentAllocationCommand(" direct_claim ", TARGET, " defense_cost ",
                        new BigDecimal("1250")));

        assertEquals("DIRECT_CLAIM", command.targetType());
        assertEquals("DEFENSE_COST", command.component());
        assertEquals(TARGET, command.targetId());
        assertEquals(new BigDecimal("1250.0000"), command.amount());
    }

    @Test
    void collectsEveryMalformedFieldInFieldOrder() {
        ApiException ex = assertThrows(ApiException.class,
                () -> PaymentAllocationRules.normalize(
                        new CreatePaymentAllocationCommand(null, null, " ", null)));

        assertEquals("VALIDATION_ERROR", ex.code());
        assertEquals(4, ex.details().size());
        assertEquals("targetType", ex.details().get(0).field());
        assertEquals("targetId", ex.details().get(1).field());
        assertEquals("component", ex.details().get(2).field());
        assertEquals("amount", ex.details().get(3).field());
    }

    @Test
    void rejectsUnknownVocabulary() {
        ApiException ex = assertThrows(ApiException.class,
                () -> PaymentAllocationRules.normalize(
                        new CreatePaymentAllocationCommand("BRIBE", TARGET, "BRIBE",
                                new BigDecimal("10.00"))));

        assertEquals(2, ex.details().size());
        assertEquals("Unknown target type: BRIBE", ex.details().get(0).message());
        assertEquals("Unknown component: BRIBE", ex.details().get(1).message());
    }

    @Test
    void rejectsAmountsOutsideTheMoneyShape() {
        assertEquals("amount", assertThrows(ApiException.class,
                () -> PaymentAllocationRules.normalize(new CreatePaymentAllocationCommand(
                        "DIRECT_CLAIM", TARGET, "INDEMNITY", new BigDecimal("10.12345"))))
                .details().get(0).field());

        assertEquals("amount", assertThrows(ApiException.class,
                () -> PaymentAllocationRules.normalize(new CreatePaymentAllocationCommand(
                        "DIRECT_CLAIM", TARGET, "INDEMNITY", new BigDecimal("-1"))))
                .details().get(0).field());

        assertEquals("amount", assertThrows(ApiException.class,
                () -> PaymentAllocationRules.normalize(new CreatePaymentAllocationCommand(
                        "DIRECT_CLAIM", TARGET, "INDEMNITY",
                        new BigDecimal("1000000000000000"))))
                .details().get(0).field());
    }
}
