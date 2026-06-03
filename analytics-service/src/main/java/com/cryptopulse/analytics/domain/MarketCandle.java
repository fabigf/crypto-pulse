package com.cryptopulse.analytics.domain;

import java.math.BigDecimal;

public class MarketCandle {

    private long bucketStart;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;

    public MarketCandle() {
    }

    public MarketCandle(long bucketStart, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        this.bucketStart = bucketStart;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
    }

    public long getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(long bucketStart) {
        this.bucketStart = bucketStart;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }
}
