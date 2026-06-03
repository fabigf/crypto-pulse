package com.cryptopulse.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketSummaryResponse(
        String ticker,
        BigDecimal latestPrice,
        BigDecimal percentageChange,
        Instant updatedAt,
        boolean live
) {
}
