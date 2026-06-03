package com.cryptopulse.analytics.service;

import com.cryptopulse.analytics.domain.MarketCandle;
import com.cryptopulse.analytics.domain.MarketSnapshot;
import com.cryptopulse.analytics.domain.MarketTick;
import com.cryptopulse.analytics.dto.MarketSnapshotResponse;
import com.cryptopulse.analytics.dto.MarketSummaryResponse;
import com.cryptopulse.analytics.event.MarketPriceEvent;
import com.cryptopulse.analytics.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketProjectionService {

    private static final int MAX_RECENT_TICKS = 60;
    private static final int MAX_CANDLES = 30;
    private static final long CANDLE_BUCKET_MS = 60000L;
    private static final int CHANGE_SCALE = 4;

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Set<String> supportedTickers;

    public MarketProjectionService(
            MarketSnapshotRepository marketSnapshotRepository,
            SimpMessagingTemplate messagingTemplate,
            @Value("${analytics.market.supported-tickers}") String supportedTickers
    ) {
        this.marketSnapshotRepository = marketSnapshotRepository;
        this.messagingTemplate = messagingTemplate;
        this.supportedTickers = parseSupportedTickers(supportedTickers);
    }

    public List<MarketSummaryResponse> getTrackedMarkets() {
        List<MarketSummaryResponse> summaries = new ArrayList<>();
        for (String ticker : supportedTickers) {
            MarketSnapshot snapshot = marketSnapshotRepository.findById(ticker).orElse(null);
            if (snapshot == null) {
                summaries.add(new MarketSummaryResponse(ticker, null, null, null, false));
                continue;
            }

            MarketSnapshotResponse response = toResponse(snapshot);
            summaries.add(new MarketSummaryResponse(
                    response.ticker(),
                    response.latestPrice(),
                    response.percentageChange(),
                    response.updatedAt(),
                    true
            ));
        }
        return summaries;
    }

    public MarketSnapshotResponse getMarketSnapshot(String ticker) {
        validateTicker(ticker);
        MarketSnapshot snapshot = marketSnapshotRepository.findById(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Market snapshot not found for ticker " + ticker));
        return toResponse(snapshot);
    }

    public MarketSnapshotResponse applyMarketPrice(MarketPriceEvent event) {
        if (event == null || event.ticker() == null || event.ticker().isBlank()) {
            throw new IllegalArgumentException("MarketPriceEvent ticker is required");
        }
        if (event.price() == null || event.price().signum() <= 0) {
            throw new IllegalArgumentException("MarketPriceEvent price must be positive");
        }

        long eventTimestamp = event.timestamp() > 0 ? event.timestamp() : System.currentTimeMillis();
        MarketSnapshot existingSnapshot = marketSnapshotRepository.findById(event.ticker()).orElse(null);
        if (existingSnapshot == null) {
            MarketSnapshot bootstrappedSnapshot = MarketSnapshot.bootstrap(event.ticker(), event.price(), eventTimestamp);
            MarketSnapshot savedSnapshot = marketSnapshotRepository.save(bootstrappedSnapshot);
            MarketSnapshotResponse response = toResponse(savedSnapshot);
            messagingTemplate.convertAndSend("/topic/market/" + savedSnapshot.getTicker(), response);
            return response;
        }

        MarketSnapshot snapshot = existingSnapshot;
        snapshot.setLatestPrice(event.price());
        snapshot.setUpdatedAt(Instant.ofEpochMilli(eventTimestamp));
        if (snapshot.getSessionOpenPrice() == null) {
            snapshot.setSessionOpenPrice(event.price());
        }

        List<MarketTick> recentTicks = new ArrayList<>(snapshot.getRecentTicks());
        recentTicks.add(new MarketTick(eventTimestamp, event.price()));
        snapshot.setRecentTicks(trimToLast(recentTicks, MAX_RECENT_TICKS, Comparator.comparingLong(MarketTick::getTimestamp)));

        List<MarketCandle> candles = new ArrayList<>(snapshot.getCandles());
        long bucketStart = eventTimestamp - (eventTimestamp % CANDLE_BUCKET_MS);
        if (candles.isEmpty()) {
            candles.add(new MarketCandle(bucketStart, event.price(), event.price(), event.price(), event.price()));
        } else {
            MarketCandle lastCandle = candles.getLast();
            if (lastCandle.getBucketStart() == bucketStart) {
                lastCandle.setClose(event.price());
                if (lastCandle.getHigh() == null || lastCandle.getHigh().compareTo(event.price()) < 0) {
                    lastCandle.setHigh(event.price());
                }
                if (lastCandle.getLow() == null || lastCandle.getLow().compareTo(event.price()) > 0) {
                    lastCandle.setLow(event.price());
                }
                if (lastCandle.getOpen() == null) {
                    lastCandle.setOpen(event.price());
                }
            } else {
                candles.add(new MarketCandle(bucketStart, event.price(), event.price(), event.price(), event.price()));
            }
        }
        snapshot.setCandles(trimToLast(candles, MAX_CANDLES, Comparator.comparingLong(MarketCandle::getBucketStart)));

        MarketSnapshot savedSnapshot = marketSnapshotRepository.save(snapshot);
        MarketSnapshotResponse response = toResponse(savedSnapshot);
        messagingTemplate.convertAndSend("/topic/market/" + savedSnapshot.getTicker(), response);
        return response;
    }

    public MarketSnapshotResponse toResponse(MarketSnapshot snapshot) {
        BigDecimal sessionOpenPrice = snapshot.getSessionOpenPrice();
        BigDecimal latestPrice = snapshot.getLatestPrice();
        BigDecimal absoluteChange = latestPrice.subtract(sessionOpenPrice);
        BigDecimal percentageChange = sessionOpenPrice.signum() == 0
                ? BigDecimal.ZERO
                : absoluteChange.multiply(new BigDecimal("100"))
                        .divide(sessionOpenPrice, CHANGE_SCALE, RoundingMode.HALF_UP);

        return new MarketSnapshotResponse(
                snapshot.getTicker(),
                latestPrice,
                sessionOpenPrice,
                absoluteChange,
                percentageChange,
                snapshot.getRecentTicks(),
                snapshot.getCandles(),
                snapshot.getUpdatedAt()
        );
    }

    private void validateTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new IllegalArgumentException("ticker must not be blank");
        }
    }

    private <T> List<T> trimToLast(List<T> items, int maxSize, Comparator<T> comparator) {
        if (items.size() <= maxSize) {
            return items;
        }
        items.sort(comparator);
        return new ArrayList<>(items.subList(items.size() - maxSize, items.size()));
    }

    private Set<String> parseSupportedTickers(String rawTickers) {
        return Arrays.stream(rawTickers.split(","))
                .map(ticker -> ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT))
                .filter(ticker -> !ticker.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
