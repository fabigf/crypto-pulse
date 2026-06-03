package com.cryptopulse.analytics.event;

import java.math.BigDecimal;

public record OrderExecutedEvent(
        String orderId,
        String userId,
        String ticker,
        String side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        BigDecimal totalCost,
        BigDecimal reservedAmount,
        long timestamp
) {
}
