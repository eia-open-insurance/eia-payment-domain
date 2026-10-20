package com.eia.payment.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePaymentCommand(
        @NotBlank String paymentType,
        String status,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotNull UUID payeePartyId,
        @NotNull LocalDate paymentDate,
        String sourceType,
        UUID sourceId,
        String sourceReference
) {}
