package com.cryptopulse.analytics.dto;

import com.cryptopulse.analytics.domain.ExecutedTrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PortfolioSnapshot(
        String userId,
        BigDecimal usdBalance,
        Map<String, BigDecimal> assetBalances,
        List<ExecutedTrade> trades,
        Instant updatedAt
) {
}
