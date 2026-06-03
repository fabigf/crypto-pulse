package com.cryptopulse.marketfeeder.model;

import java.math.BigDecimal;

public record MarketPriceEvent(
        String ticker,
        BigDecimal price,
        long timestamp
) {
}

