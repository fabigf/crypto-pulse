package com.cryptopulse.wallet.event;

import com.cryptopulse.wallet.domain.OrderType;
import com.cryptopulse.wallet.domain.OrderSide;
import java.math.BigDecimal;

public record BalanceReservedEvent(
        String eventId,
        String userId,
        BigDecimal amountReserved,
        String currency,
        String targetTicker,
        OrderSide side,
        OrderType orderType,
        BigDecimal targetPrice
) {
}
