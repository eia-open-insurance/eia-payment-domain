package com.eia.payment.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID tenantId,
        String paymentNumber,
        String paymentType,
        String status,
        String statusReason,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        UUID payeePartyId,
        LocalDate paymentDate,
        int version,
        Instant createdAt,
        Instant updatedAt,
        String sourceType,
        UUID sourceId,
        String sourceReference
) {}
