package com.cryptopulse.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "processed_execution_record")
public class ProcessedExecutionRecord {

    @Id
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 16)
    private String ticker;

    @Column(length = 16)
    private String side;

    @Column(precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "execution_price", precision = 19, scale = 8)
    private BigDecimal executionPrice;

    @Column(name = "total_cost", precision = 19, scale = 8)
    private BigDecimal totalCost;

    @Column(name = "reserved_amount", precision = 19, scale = 8)
    private BigDecimal reservedAmount;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedExecutionRecord() {
    }

    public ProcessedExecutionRecord(
            String orderId,
            Long userId,
            String ticker,
            String side,
            BigDecimal quantity,
            BigDecimal executionPrice,
            BigDecimal totalCost,
            BigDecimal reservedAmount,
            Instant processedAt
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.ticker = ticker;
        this.side = side;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.totalCost = totalCost;
        this.reservedAmount = reservedAmount;
        this.processedAt = processedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTicker() {
        return ticker;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public BigDecimal getReservedAmount() {
        return reservedAmount;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
