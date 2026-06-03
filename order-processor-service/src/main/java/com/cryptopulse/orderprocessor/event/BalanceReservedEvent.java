package com.cryptopulse.orderprocessor.event;

import java.math.BigDecimal;

public record BalanceReservedEvent(
        String eventId,
        String userId,
        BigDecimal amountReserved,
        String currency,
        String targetTicker,
        String side,
        String orderType,
        BigDecimal targetPrice
) {
}
