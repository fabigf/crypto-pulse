package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PendingOrderResponse(
        String orderId,
        Long userId,
        String targetTicker,
        String side,
        String orderType,
        String currency,
        BigDecimal amountReserved,
        BigDecimal targetPrice,
        Instant createdAt
) {
}
