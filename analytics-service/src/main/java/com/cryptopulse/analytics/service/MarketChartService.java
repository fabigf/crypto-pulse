package com.cryptopulse.analytics.service;

import com.cryptopulse.analytics.client.BinanceMarketDataClient;
import com.cryptopulse.analytics.domain.MarketCandle;
import com.cryptopulse.analytics.domain.MarketSnapshot;
import com.cryptopulse.analytics.dto.MarketChartResponse;
import com.cryptopulse.analytics.dto.MarketChartTimeframe;
import com.cryptopulse.analytics.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MarketChartService {

    private static final int CHANGE_SCALE = 4;
    private static final int MAX_CHART_LIMIT = 500;

    private final BinanceMarketDataClient binanceMarketDataClient;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final Set<String> supportedTickers;
    private final int defaultChartLimit;

    public MarketChartService(
            BinanceMarketDataClient binanceMarketDataClient,
            MarketSnapshotRepository marketSnapshotRepository,
            @Value("${analytics.market.supported-tickers}") String supportedTickers,
            @Value("${analytics.market.chart-default-limit}") int defaultChartLimit
    ) {
        this.binanceMarketDataClient = binanceMarketDataClient;
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.supportedTickers = parseSupportedTickers(supportedTickers);
        this.defaultChartLimit = clampLimit(defaultChartLimit);
    }

    public MarketChartResponse getMarketChart(String ticker, String timeframe, Integer requestedLimit) {
        String normalizedTicker = normalizeSupportedTicker(ticker);
        MarketChartTimeframe resolvedTimeframe = MarketChartTimeframe.fromRequest(timeframe);
        int limit = clampLimit(requestedLimit == null ? defaultChartLimit : requestedLimit);
        List<MarketCandle> candles = binanceMarketDataClient.fetchUiCandles(
                normalizedTicker,
                resolvedTimeframe.binanceInterval(),
                limit
        );

        MarketSnapshot snapshot = marketSnapshotRepository.findById(normalizedTicker).orElse(null);
        BigDecimal latestPrice = snapshot != null ? snapshot.getLatestPrice() : extractLatestPrice(candles);
        BigDecimal referenceOpen = snapshot != null ? snapshot.getSessionOpenPrice() : extractReferenceOpen(candles);
        BigDecimal absoluteChange = calculateAbsoluteChange(referenceOpen, latestPrice);
        BigDecimal percentageChange = calculatePercentageChange(referenceOpen, absoluteChange);
        Instant updatedAt = snapshot != null
                ? snapshot.getUpdatedAt()
                : candles.isEmpty() ? null : Instant.ofEpochMilli(candles.getLast().getBucketStart());

        return new MarketChartResponse(
                normalizedTicker,
                resolvedTimeframe.requestValue(),
                "binance-uiKlines",
                latestPrice,
                absoluteChange,
                percentageChange,
                updatedAt,
                candles
        );
    }

    private BigDecimal extractLatestPrice(List<MarketCandle> candles) {
        return candles.isEmpty() ? null : candles.getLast().getClose();
    }

    private BigDecimal extractReferenceOpen(List<MarketCandle> candles) {
        return candles.isEmpty() ? null : candles.getFirst().getOpen();
    }

    private BigDecimal calculateAbsoluteChange(BigDecimal referenceOpen, BigDecimal latestPrice) {
        if (referenceOpen == null || latestPrice == null) {
            return null;
        }
        return latestPrice.subtract(referenceOpen);
    }

    private BigDecimal calculatePercentageChange(BigDecimal referenceOpen, BigDecimal absoluteChange) {
        if (referenceOpen == null || absoluteChange == null || referenceOpen.signum() == 0) {
            return null;
        }
        return absoluteChange.multiply(new BigDecimal("100"))
                .divide(referenceOpen, CHANGE_SCALE, RoundingMode.HALF_UP);
    }

    private String normalizeSupportedTicker(String rawTicker) {
        if (rawTicker == null || rawTicker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }

        String normalizedTicker = rawTicker.trim().toUpperCase(Locale.ROOT);
        if (!supportedTickers.contains(normalizedTicker)) {
            throw new IllegalArgumentException("Ticker " + normalizedTicker + " is not enabled for market charts");
        }
        return normalizedTicker;
    }

    private int clampLimit(int requestedLimit) {
        return Math.max(20, Math.min(requestedLimit, MAX_CHART_LIMIT));
    }

    private Set<String> parseSupportedTickers(String rawTickers) {
        return Arrays.stream(rawTickers.split(","))
                .map(ticker -> ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT))
                .filter(ticker -> !ticker.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
