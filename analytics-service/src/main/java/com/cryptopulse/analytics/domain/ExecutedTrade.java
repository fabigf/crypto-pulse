package com.cryptopulse.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

public class ExecutedTrade {

    private String orderId;
    private String ticker;
    private String side;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal quantity;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal executionPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalCost;
    private Instant executedAt;

    public ExecutedTrade() {
    }

    public ExecutedTrade(
            String orderId,
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal totalCost,
            Instant executedAt
    ) {
        this.orderId = orderId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.totalCost = totalCost;
        this.executedAt = executedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }
}
