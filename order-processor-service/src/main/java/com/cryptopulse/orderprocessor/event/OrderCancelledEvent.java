package com.cryptopulse.orderprocessor.event;

import java.math.BigDecimal;

public record OrderCancelledEvent(
        String orderId,
        String userId,
        BigDecimal amountReleased,
        String currency,
        String targetTicker,
        String side,
        long timestamp
) {
}
