package com.cryptopulse.orderprocessor.service;

import com.cryptopulse.orderprocessor.dto.PendingOrderResponse;
import com.cryptopulse.orderprocessor.model.PendingLimitOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PendingOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderSyncService.class);
    private static final TypeReference<List<PendingOrderResponse>> PENDING_ORDER_LIST = new TypeReference<>() {
    };

    private final OrderProcessorService orderProcessorService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String pendingOrdersUrl;

    public PendingOrderSyncService(
            OrderProcessorService orderProcessorService,
            ObjectMapper objectMapper,
            @Value("${wallet-service.url}") String walletServiceUrl
    ) {
        this.orderProcessorService = orderProcessorService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.pendingOrdersUrl = walletServiceUrl + "/api/v1/wallet/orders/pending";
    }

    @Scheduled(
            initialDelayString = "${order-processor.sync.initial-delay-ms:1000}",
            fixedDelayString = "${order-processor.sync.pending-orders-interval-ms:5000}"
    )
    public void syncPendingOrders() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(pendingOrdersUrl))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Pending-order sync returned status {}", response.statusCode());
                return;
            }

            List<PendingOrderResponse> orders = objectMapper.readValue(response.body(), PENDING_ORDER_LIST);
            orderProcessorService.syncPendingOrders(orders.stream()
                    .map(this::toPendingLimitOrder)
                    .toList());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Could not sync pending orders from wallet-service: {}", exception.getMessage());
        }
    }

    private PendingLimitOrder toPendingLimitOrder(PendingOrderResponse order) {
        return new PendingLimitOrder(
                order.orderId(),
                String.valueOf(order.userId()),
                order.targetTicker(),
                order.side(),
                order.targetPrice(),
                order.amountReserved(),
                "SELL".equalsIgnoreCase(order.side())
                        ? order.amountReserved().setScale(8, RoundingMode.HALF_UP)
                        : order.amountReserved().divide(order.targetPrice(), 8, RoundingMode.HALF_UP)
        );
    }
}
