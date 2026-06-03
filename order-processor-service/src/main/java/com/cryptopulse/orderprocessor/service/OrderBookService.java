package com.cryptopulse.orderprocessor.service;

import com.cryptopulse.orderprocessor.model.PendingLimitOrder;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class OrderBookService {

    private final ConcurrentHashMap<String, PendingLimitOrder> ordersById = new ConcurrentHashMap<>();

    public void add(PendingLimitOrder order) {
        ordersById.put(order.orderId(), order);
    }

    public List<PendingLimitOrder> matchExecutableOrders(String ticker, BigDecimal marketPrice) {
        return ordersById.values().stream()
                .filter(order -> order.ticker().equalsIgnoreCase(ticker))
                .filter(order -> isExecutable(order, marketPrice))
                .toList();
    }

    public List<PendingLimitOrder> drainExecutableOrders(String ticker, BigDecimal marketPrice) {
        List<PendingLimitOrder> executableOrders = matchExecutableOrders(ticker, marketPrice);
        if (executableOrders.isEmpty()) {
            return List.of();
        }

        executableOrders.forEach(this::remove);
        return executableOrders;
    }

    public void remove(PendingLimitOrder order) {
        removeById(order.orderId());
    }

    public void removeById(String orderId) {
        ordersById.remove(orderId);
    }

    public void replaceAll(Collection<PendingLimitOrder> pendingOrders) {
        List<String> nextIds = pendingOrders.stream()
                .map(PendingLimitOrder::orderId)
                .toList();
        ordersById.keySet().removeIf(existingId -> !nextIds.contains(existingId));
        pendingOrders.forEach(this::add);
    }

    public int countAll() {
        return ordersById.size();
    }

    private boolean isExecutable(PendingLimitOrder order, BigDecimal marketPrice) {
        if ("SELL".equalsIgnoreCase(order.side())) {
            return marketPrice.compareTo(order.targetPrice()) >= 0;
        }
        return marketPrice.compareTo(order.targetPrice()) <= 0;
    }
}
