package com.cryptopulse.analytics.dto;

import com.cryptopulse.analytics.domain.MarketCandle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record MarketChartResponse(
        String ticker,
        String timeframe,
        String source,
        BigDecimal latestPrice,
        BigDecimal absoluteChange,
        BigDecimal percentageChange,
        Instant updatedAt,
        List<MarketCandle> candles
) {
}
