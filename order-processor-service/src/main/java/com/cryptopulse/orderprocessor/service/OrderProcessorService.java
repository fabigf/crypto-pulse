package com.cryptopulse.orderprocessor.service;

import com.cryptopulse.orderprocessor.event.BalanceReservedEvent;
import com.cryptopulse.orderprocessor.event.MarketPriceEvent;
import com.cryptopulse.orderprocessor.event.OrderCancelledEvent;
import com.cryptopulse.orderprocessor.event.OrderExecutedEvent;
import com.cryptopulse.orderprocessor.model.PendingLimitOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessorService.class);
    private static final int QUANTITY_SCALE = 8;
    private static final int MONEY_SCALE = 8;

    private final OrderBookService orderBookService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String orderEventsTopic;

    public OrderProcessorService(
            OrderBookService orderBookService,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${order-processor.kafka.order-events-topic}") String orderEventsTopic
    ) {
        this.orderBookService = orderBookService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.orderEventsTopic = orderEventsTopic;
    }

    @KafkaListener(topics = "${order-processor.kafka.wallet-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onBalanceReserved(String payload) {
        try {
            BalanceReservedEvent event = objectMapper.readValue(payload, BalanceReservedEvent.class);
            acceptReservedOrder(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize BalanceReservedEvent", exception);
        }
    }

    @KafkaListener(topics = "${order-processor.kafka.market-prices-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMarketPrice(String payload) {
        try {
            MarketPriceEvent marketPriceEvent = objectMapper.readValue(payload, MarketPriceEvent.class);
            acceptMarketPrice(marketPriceEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize MarketPriceEvent", exception);
        }
    }

    @KafkaListener(topics = "${order-processor.kafka.order-cancellations-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCancelled(String payload) {
        try {
            OrderCancelledEvent orderCancelledEvent = objectMapper.readValue(payload, OrderCancelledEvent.class);
            acceptCancelledOrder(orderCancelledEvent);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize OrderCancelledEvent", exception);
        }
    }

    public void acceptReservedOrder(BalanceReservedEvent event) {
        if (!"LIMIT".equalsIgnoreCase(event.orderType())) {
            log.debug("Ignoring unsupported order type {}", event.orderType());
            return;
        }
        if (event.targetTicker() == null || event.targetTicker().isBlank()) {
            log.warn("Ignoring order {} because targetTicker is missing", event.eventId());
            return;
        }
        if (event.amountReserved() == null || event.amountReserved().signum() <= 0) {
            log.warn("Ignoring order {} because amountReserved is invalid", event.eventId());
            return;
        }
        if (event.targetPrice() == null || event.targetPrice().signum() <= 0) {
            log.warn("Ignoring order {} because targetPrice is invalid", event.eventId());
            return;
        }
        if (event.side() == null || event.side().isBlank()) {
            log.warn("Ignoring order {} because side is missing", event.eventId());
            return;
        }

        BigDecimal quantity = "SELL".equalsIgnoreCase(event.side())
                ? event.amountReserved().setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
                : event.amountReserved().divide(event.targetPrice(), QUANTITY_SCALE, RoundingMode.HALF_UP);

        PendingLimitOrder order = new PendingLimitOrder(
                event.eventId(),
                event.userId(),
                event.targetTicker(),
                event.side(),
                event.targetPrice(),
                event.amountReserved(),
                quantity
        );
        orderBookService.add(order);
        log.debug("Stored pending {} limit order {} for ticker {}", order.side(), order.orderId(), order.ticker());
    }

    public void acceptCancelledOrder(OrderCancelledEvent event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            return;
        }
        orderBookService.removeById(event.orderId());
        log.debug("Removed cancelled order {}", event.orderId());
    }

    public void acceptMarketPrice(MarketPriceEvent event) {
        List<PendingLimitOrder> executableOrders = orderBookService.drainExecutableOrders(
                event.ticker(),
                event.price()
        );

        for (PendingLimitOrder executableOrder : executableOrders) {
            publishOrderExecuted(executableOrder, event);
        }
    }

    public int pendingOrdersCount() {
        return orderBookService.countAll();
    }

    public void syncPendingOrders(List<PendingLimitOrder> pendingOrders) {
        orderBookService.replaceAll(pendingOrders);
        log.debug("Synced {} pending orders from wallet-service", pendingOrders.size());
    }

    private void publishOrderExecuted(PendingLimitOrder order, MarketPriceEvent marketPriceEvent) {
        BigDecimal totalCost = marketPriceEvent.price()
                .multiply(order.quantity())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        OrderExecutedEvent executedEvent = new OrderExecutedEvent(
                order.orderId(),
                order.userId(),
                order.ticker(),
                order.side(),
                order.quantity(),
                marketPriceEvent.price(),
                totalCost,
                order.reservedAmount(),
                Instant.now().toEpochMilli()
        );
        kafkaTemplate.send(orderEventsTopic, order.ticker(), serialize(executedEvent));
        log.debug("Published order execution {}", order.orderId());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event", exception);
        }
    }
}
