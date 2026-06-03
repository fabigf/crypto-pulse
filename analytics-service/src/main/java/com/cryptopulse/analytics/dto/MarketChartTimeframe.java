package com.cryptopulse.analytics.dto;

import java.util.Arrays;
import java.util.Locale;

public enum MarketChartTimeframe {
    FOUR_HOURS("4h", "4h"),
    DAILY("1d", "1d"),
    WEEKLY("1w", "1w"),
    MONTHLY("1M", "1M");

    private final String requestValue;
    private final String binanceInterval;

    MarketChartTimeframe(String requestValue, String binanceInterval) {
        this.requestValue = requestValue;
        this.binanceInterval = binanceInterval;
    }

    public String requestValue() {
        return requestValue;
    }

    public String binanceInterval() {
        return binanceInterval;
    }

    public static MarketChartTimeframe fromRequest(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DAILY;
        }

        String normalized = rawValue.trim();
        return Arrays.stream(values())
                .filter(candidate -> candidate.requestValue.equalsIgnoreCase(normalized)
                        || candidate.name().equalsIgnoreCase(normalized.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported timeframe " + rawValue));
    }
}
