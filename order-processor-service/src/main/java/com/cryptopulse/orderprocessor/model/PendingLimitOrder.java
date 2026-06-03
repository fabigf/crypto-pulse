package com.cryptopulse.orderprocessor.model;

import java.math.BigDecimal;

public record PendingLimitOrder(
        String orderId,
        String userId,
        String ticker,
        String side,
        BigDecimal targetPrice,
        BigDecimal reservedAmount,
        BigDecimal quantity
) {
}
