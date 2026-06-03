package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;

public record ReserveOrderResponse(
        String eventId,
        Long userId,
        String side,
        String targetTicker,
        BigDecimal amountReserved,
        String currency,
        String status
) {
}
