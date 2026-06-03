package com.cryptopulse.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "balance_audit_record")
public class BalanceAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal amount;

    @Column(name = "target_ticker", nullable = false, length = 16)
    private String targetTicker;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 16)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_side", nullable = false, length = 16)
    private OrderSide side;

    @Column(name = "target_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal targetPrice;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderReservationStatus status;

    @Column
    private Instant closedAt;

    protected BalanceAuditRecord() {
    }

    public BalanceAuditRecord(
            String eventId,
            Long userId,
            String currency,
            BigDecimal amount,
            String targetTicker,
            OrderType orderType,
            OrderSide side,
            BigDecimal targetPrice,
            Instant createdAt,
            OrderReservationStatus status
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.currency = currency;
        this.amount = amount;
        this.targetTicker = targetTicker;
        this.orderType = orderType;
        this.side = side;
        this.targetPrice = targetPrice;
        this.createdAt = createdAt;
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getTargetTicker() {
        return targetTicker;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getTargetPrice() {
        return targetPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderReservationStatus getStatus() {
        return status;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void markExecuted(Instant processedAt) {
        this.status = OrderReservationStatus.EXECUTED;
        this.closedAt = processedAt;
    }

    public void markCancelled(Instant processedAt) {
        this.status = OrderReservationStatus.CANCELLED;
        this.closedAt = processedAt;
    }
}
