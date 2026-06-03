package com.cryptopulse.analytics.domain;

import java.math.BigDecimal;

public class MarketTick {

    private long timestamp;
    private BigDecimal price;

    public MarketTick() {
    }

    public MarketTick(long timestamp, BigDecimal price) {
        this.timestamp = timestamp;
        this.price = price;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
