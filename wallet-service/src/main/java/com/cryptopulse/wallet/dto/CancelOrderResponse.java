package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;

public record CancelOrderResponse(
        String orderId,
        Long userId,
        String status,
        String currency,
        BigDecimal amountReleased
) {
}
