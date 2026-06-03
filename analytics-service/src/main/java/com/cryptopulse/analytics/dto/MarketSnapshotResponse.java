package com.cryptopulse.analytics.dto;

import com.cryptopulse.analytics.domain.MarketCandle;
import com.cryptopulse.analytics.domain.MarketTick;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketSnapshotResponse(
        String ticker,
        BigDecimal latestPrice,
        BigDecimal sessionOpenPrice,
        BigDecimal absoluteChange,
        BigDecimal percentageChange,
        List<MarketTick> recentTicks,
        List<MarketCandle> candles,
        Instant updatedAt
) {
}
