package com.cryptopulse.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_execution_record")
public class ProcessedExecutionRecord {

    @Id
    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedExecutionRecord() {
    }

    public ProcessedExecutionRecord(String orderId, Long userId, Instant processedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.processedAt = processedAt;
    }
}
