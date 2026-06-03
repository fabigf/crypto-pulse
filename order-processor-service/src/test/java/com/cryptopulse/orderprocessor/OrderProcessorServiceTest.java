package com.cryptopulse.orderprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.orderprocessor.event.BalanceReservedEvent;
import com.cryptopulse.orderprocessor.event.MarketPriceEvent;
import com.cryptopulse.orderprocessor.event.OrderCancelledEvent;
import com.cryptopulse.orderprocessor.service.OrderBookService;
import com.cryptopulse.orderprocessor.service.OrderProcessorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OrderProcessorServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStoreOrderAndExecuteWhenMarketPriceIntersects() throws Exception {
        OrderBookService orderBookService = new OrderBookService();
        when(kafkaTemplate.send(eq("order-events"), eq("BTCUSDT"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "order-1",
                "42",
                new BigDecimal("5000.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        assertThat(orderBookService.countAll()).isEqualTo(1);

        service.acceptMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("49999.99"),
                System.currentTimeMillis()
        ));

        assertThat(orderBookService.countAll()).isEqualTo(0);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("BTCUSDT"), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("orderId").asText()).isEqualTo("order-1");
        assertThat(payload.get("userId").asText()).isEqualTo("42");
        assertThat(payload.get("ticker").asText()).isEqualTo("BTCUSDT");
        assertThat(payload.get("side").asText()).isEqualTo("BUY");
        assertThat(payload.get("quantity").decimalValue()).isEqualByComparingTo("0.10000000");
        assertThat(payload.get("executionPrice").decimalValue()).isEqualByComparingTo("49999.99");
        assertThat(payload.get("totalCost").decimalValue()).isEqualByComparingTo("4999.99900000");
        assertThat(payload.get("reservedAmount").decimalValue()).isEqualByComparingTo("5000.00000000");
    }

    @Test
    void shouldKeepOrderPendingWhenMarketHasNotReachedTargetPrice() {
        OrderBookService orderBookService = new OrderBookService();
        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "order-2",
                "84",
                new BigDecimal("5000.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        service.acceptMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("51000.00"),
                System.currentTimeMillis()
        ));

        assertThat(orderBookService.countAll()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(eq("order-events"), anyString(), anyString());
    }

    @Test
    void shouldNotPublishOrderTwiceAfterItHasAlreadyBeenDrained() {
        OrderBookService orderBookService = new OrderBookService();
        when(kafkaTemplate.send(eq("order-events"), eq("BTCUSDT"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "order-3",
                "52",
                new BigDecimal("2500.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        service.acceptMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("50000.00"),
                System.currentTimeMillis()
        ));

        service.acceptMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("49999.00"),
                System.currentTimeMillis()
        ));

        verify(kafkaTemplate, times(1)).send(eq("order-events"), eq("BTCUSDT"), anyString());
        assertThat(orderBookService.countAll()).isZero();
    }

    @Test
    void shouldRemoveCancelledOrderFromBook() {
        OrderBookService orderBookService = new OrderBookService();
        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "order-3b",
                "52",
                new BigDecimal("2500.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        service.acceptCancelledOrder(new OrderCancelledEvent(
                "order-3b",
                "52",
                new BigDecimal("2500.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                System.currentTimeMillis()
        ));

        assertThat(orderBookService.countAll()).isZero();
    }

    @Test
    void shouldReplacePendingOrdersDuringSync() {
        OrderBookService orderBookService = new OrderBookService();
        when(kafkaTemplate.send(eq("order-events"), eq("ETHUSDT"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "stale-order",
                "77",
                new BigDecimal("1000.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        service.syncPendingOrders(List.of(
                new com.cryptopulse.orderprocessor.model.PendingLimitOrder(
                        "fresh-order",
                        "88",
                        "ETHUSDT",
                        "SELL",
                        new BigDecimal("3500.00"),
                        new BigDecimal("1.50000000"),
                        new BigDecimal("1.50000000")
                )
        ));

        assertThat(orderBookService.countAll()).isEqualTo(1);
        service.acceptMarketPrice(new MarketPriceEvent(
                "ETHUSDT",
                new BigDecimal("3600.00"),
                System.currentTimeMillis()
        ));

        verify(kafkaTemplate).send(eq("order-events"), eq("ETHUSDT"), anyString());
        verify(kafkaTemplate, never()).send(eq("order-events"), eq("BTCUSDT"), anyString());
    }

    @Test
    void shouldExecuteSellOrderWhenMarketTradesAboveTargetPrice() throws Exception {
        OrderBookService orderBookService = new OrderBookService();
        when(kafkaTemplate.send(eq("order-events"), eq("ETHUSDT"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        OrderProcessorService service = new OrderProcessorService(
                orderBookService,
                kafkaTemplate,
                objectMapper,
                "order-events"
        );

        service.acceptReservedOrder(new BalanceReservedEvent(
                "order-4",
                "99",
                new BigDecimal("1.50000000"),
                "ETH",
                "ETHUSDT",
                "SELL",
                "LIMIT",
                new BigDecimal("3500.00")
        ));

        service.acceptMarketPrice(new MarketPriceEvent(
                "ETHUSDT",
                new BigDecimal("3600.00"),
                System.currentTimeMillis()
        ));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("order-events"), eq("ETHUSDT"), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("side").asText()).isEqualTo("SELL");
        assertThat(payload.get("quantity").decimalValue()).isEqualByComparingTo("1.50000000");
        assertThat(payload.get("totalCost").decimalValue()).isEqualByComparingTo("5400.00000000");
        assertThat(payload.get("reservedAmount").decimalValue()).isEqualByComparingTo("1.50000000");
        assertThat(orderBookService.countAll()).isZero();
    }
}
