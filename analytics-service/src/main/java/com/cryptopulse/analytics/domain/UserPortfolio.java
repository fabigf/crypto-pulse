package com.cryptopulse.analytics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "user_portfolios")
public class UserPortfolio {

    @Id
    private String userId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal usdBalance;
    @Field(targetType = FieldType.DECIMAL128)
    private Map<String, BigDecimal> assetBalances = new LinkedHashMap<>();
    private List<ExecutedTrade> trades = new ArrayList<>();
    private List<String> processedReservationIds = new ArrayList<>();
    private List<String> processedExecutionIds = new ArrayList<>();
    private Instant updatedAt;

    public UserPortfolio() {
    }

    public UserPortfolio(
            String userId,
            BigDecimal usdBalance,
            Map<String, BigDecimal> assetBalances,
            List<ExecutedTrade> trades,
            List<String> processedReservationIds,
            List<String> processedExecutionIds,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.usdBalance = usdBalance;
        this.assetBalances = assetBalances;
        this.trades = trades;
        this.processedReservationIds = processedReservationIds;
        this.processedExecutionIds = processedExecutionIds;
        this.updatedAt = updatedAt;
    }

    public static UserPortfolio bootstrap(String userId, BigDecimal initialUsdBalance) {
        return new UserPortfolio(
                userId,
                initialUsdBalance,
                new LinkedHashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Instant.now()
        );
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getUsdBalance() {
        return usdBalance;
    }

    public void setUsdBalance(BigDecimal usdBalance) {
        this.usdBalance = usdBalance;
    }

    public Map<String, BigDecimal> getAssetBalances() {
        return assetBalances;
    }

    public void setAssetBalances(Map<String, BigDecimal> assetBalances) {
        this.assetBalances = assetBalances;
    }

    public List<ExecutedTrade> getTrades() {
        return trades;
    }

    public void setTrades(List<ExecutedTrade> trades) {
        this.trades = trades;
    }

    public List<String> getProcessedReservationIds() {
        return processedReservationIds;
    }

    public void setProcessedReservationIds(List<String> processedReservationIds) {
        this.processedReservationIds = processedReservationIds;
    }

    public List<String> getProcessedExecutionIds() {
        return processedExecutionIds;
    }

    public void setProcessedExecutionIds(List<String> processedExecutionIds) {
        this.processedExecutionIds = processedExecutionIds;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
