package com.cryptopulse.analytics.event;

import java.math.BigDecimal;

public record MarketPriceEvent(
        String ticker,
        BigDecimal price,
        long timestamp
) {
}
