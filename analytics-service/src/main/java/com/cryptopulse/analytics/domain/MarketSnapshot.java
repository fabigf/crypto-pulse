package com.cryptopulse.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "market_snapshots")
public class MarketSnapshot {

    @Id
    private String ticker;
    private BigDecimal latestPrice;
    private BigDecimal sessionOpenPrice;
    private List<MarketTick> recentTicks = new ArrayList<>();
    private List<MarketCandle> candles = new ArrayList<>();
    private Instant updatedAt;

    public MarketSnapshot() {
    }

    public MarketSnapshot(
            String ticker,
            BigDecimal latestPrice,
            BigDecimal sessionOpenPrice,
            List<MarketTick> recentTicks,
            List<MarketCandle> candles,
            Instant updatedAt
    ) {
        this.ticker = ticker;
        this.latestPrice = latestPrice;
        this.sessionOpenPrice = sessionOpenPrice;
        this.recentTicks = recentTicks;
        this.candles = candles;
        this.updatedAt = updatedAt;
    }

    public static MarketSnapshot bootstrap(String ticker, BigDecimal initialPrice, long timestamp) {
        long bucketStart = timestamp - (timestamp % 60000L);
        List<MarketTick> ticks = new ArrayList<>();
        ticks.add(new MarketTick(timestamp, initialPrice));
        List<MarketCandle> candles = new ArrayList<>();
        candles.add(new MarketCandle(bucketStart, initialPrice, initialPrice, initialPrice, initialPrice));
        return new MarketSnapshot(
                ticker,
                initialPrice,
                initialPrice,
                ticks,
                candles,
                Instant.ofEpochMilli(timestamp)
        );
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public BigDecimal getSessionOpenPrice() {
        return sessionOpenPrice;
    }

    public void setSessionOpenPrice(BigDecimal sessionOpenPrice) {
        this.sessionOpenPrice = sessionOpenPrice;
    }

    public List<MarketTick> getRecentTicks() {
        return recentTicks;
    }

    public void setRecentTicks(List<MarketTick> recentTicks) {
        this.recentTicks = recentTicks;
    }

    public List<MarketCandle> getCandles() {
        return candles;
    }

    public void setCandles(List<MarketCandle> candles) {
        this.candles = candles;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
