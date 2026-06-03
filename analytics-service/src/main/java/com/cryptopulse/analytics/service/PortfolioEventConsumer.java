package com.cryptopulse.analytics.service;

import com.cryptopulse.analytics.event.BalanceReservedEvent;
import com.cryptopulse.analytics.event.MarketPriceEvent;
import com.cryptopulse.analytics.event.OrderExecutedEvent;
import com.cryptopulse.analytics.event.UserCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PortfolioEventConsumer {

    private final ObjectMapper objectMapper;
    private final PortfolioProjectionService portfolioProjectionService;
    private final MarketProjectionService marketProjectionService;

    public PortfolioEventConsumer(
            ObjectMapper objectMapper,
            PortfolioProjectionService portfolioProjectionService,
            MarketProjectionService marketProjectionService
    ) {
        this.objectMapper = objectMapper;
        this.portfolioProjectionService = portfolioProjectionService;
        this.marketProjectionService = marketProjectionService;
    }

    @KafkaListener(
            topics = "${analytics.kafka.user-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserCreated(String payload) {
        try {
            portfolioProjectionService.createPortfolio(
                    objectMapper.readValue(payload, UserCreatedEvent.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize UserCreatedEvent", exception);
        }
    }

    @KafkaListener(
            topics = "${analytics.kafka.wallet-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onBalanceReserved(String payload) {
        try {
            portfolioProjectionService.applyBalanceReserved(
                    objectMapper.readValue(payload, BalanceReservedEvent.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize BalanceReservedEvent", exception);
        }
    }

    @KafkaListener(
            topics = "${analytics.kafka.order-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onOrderExecuted(String payload) {
        try {
            portfolioProjectionService.applyOrderExecuted(
                    objectMapper.readValue(payload, OrderExecutedEvent.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize OrderExecutedEvent", exception);
        }
    }

    @KafkaListener(
            topics = "${analytics.kafka.market-prices-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMarketPrice(String payload) {
        try {
            marketProjectionService.applyMarketPrice(
                    objectMapper.readValue(payload, MarketPriceEvent.class)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize MarketPriceEvent", exception);
        }
    }
}
