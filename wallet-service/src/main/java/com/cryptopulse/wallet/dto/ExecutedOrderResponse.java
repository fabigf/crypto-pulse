package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ExecutedOrderResponse(
        String orderId,
        Long userId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        BigDecimal totalCost,
        Instant executedAt
) {
}
