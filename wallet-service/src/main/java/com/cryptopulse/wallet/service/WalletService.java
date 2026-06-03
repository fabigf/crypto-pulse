package com.cryptopulse.wallet.service;

import com.cryptopulse.wallet.domain.BalanceAuditRecord;
import com.cryptopulse.wallet.domain.OrderType;
import com.cryptopulse.wallet.domain.OrderSide;
import com.cryptopulse.wallet.domain.OrderReservationStatus;
import com.cryptopulse.wallet.domain.ProcessedExecutionRecord;
import com.cryptopulse.wallet.domain.UserAccount;
import com.cryptopulse.wallet.domain.WalletBalance;
import com.cryptopulse.wallet.dto.CancelOrderResponse;
import com.cryptopulse.wallet.dto.CreateUserRequest;
import com.cryptopulse.wallet.dto.CreateUserResponse;
import com.cryptopulse.wallet.dto.PendingOrderResponse;
import com.cryptopulse.wallet.dto.ReserveOrderRequest;
import com.cryptopulse.wallet.dto.ReserveOrderResponse;
import com.cryptopulse.wallet.dto.WalletSnapshotResponse;
import com.cryptopulse.wallet.dto.WalletUserResponse;
import com.cryptopulse.wallet.event.BalanceReservedEvent;
import com.cryptopulse.wallet.event.OrderCancelledEvent;
import com.cryptopulse.wallet.event.OrderExecutedEvent;
import com.cryptopulse.wallet.event.UserCreatedEvent;
import com.cryptopulse.wallet.exception.DuplicateResourceException;
import com.cryptopulse.wallet.exception.InsufficientFundsException;
import com.cryptopulse.wallet.exception.OrderConflictException;
import com.cryptopulse.wallet.exception.ResourceNotFoundException;
import com.cryptopulse.wallet.repository.BalanceAuditRecordRepository;
import com.cryptopulse.wallet.repository.ProcessedExecutionRecordRepository;
import com.cryptopulse.wallet.repository.UserAccountRepository;
import com.cryptopulse.wallet.repository.WalletBalanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class WalletService {

    private static final BigDecimal INITIAL_USD_BALANCE = new BigDecimal("10000.00000000");
    private static final BigDecimal INITIAL_ASSET_BALANCE = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
    private static final int MONEY_SCALE = 8;
    private static final String USD_CURRENCY = "USD";

    private final UserAccountRepository userAccountRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final BalanceAuditRecordRepository balanceAuditRecordRepository;
    private final ProcessedExecutionRecordRepository processedExecutionRecordRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String walletEventsTopic;
    private final String walletUserEventsTopic;
    private final String walletOrderCancellationsTopic;
    private final Set<String> supportedTickers;

    public WalletService(
            UserAccountRepository userAccountRepository,
            WalletBalanceRepository walletBalanceRepository,
            BalanceAuditRecordRepository balanceAuditRecordRepository,
            ProcessedExecutionRecordRepository processedExecutionRecordRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${wallet.kafka.topic}") String walletEventsTopic,
            @Value("${wallet.kafka.user-topic}") String walletUserEventsTopic,
            @Value("${wallet.kafka.order-cancellations-topic}") String walletOrderCancellationsTopic,
            @Value("${wallet.trading.supported-tickers}") String supportedTickers
    ) {
        this.userAccountRepository = userAccountRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.balanceAuditRecordRepository = balanceAuditRecordRepository;
        this.processedExecutionRecordRepository = processedExecutionRecordRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.walletEventsTopic = walletEventsTopic;
        this.walletUserEventsTopic = walletUserEventsTopic;
        this.walletOrderCancellationsTopic = walletOrderCancellationsTopic;
        this.supportedTickers = parseSupportedTickers(supportedTickers);
    }

    @Transactional
    public CreateUserResponse registerUser(CreateUserRequest request) {
        if (userAccountRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username already exists");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email already exists");
        }

        UserAccount user = userAccountRepository.save(new UserAccount(request.username(), request.email()));
        walletBalanceRepository.save(new WalletBalance(user, USD_CURRENCY, INITIAL_USD_BALANCE));
        for (String assetCode : extractSupportedAssets()) {
            walletBalanceRepository.save(new WalletBalance(user, assetCode, INITIAL_ASSET_BALANCE));
        }

        publishAfterCommit(
                walletUserEventsTopic,
                user.getId().toString(),
                new UserCreatedEvent(user.getId().toString(), user.getUsername(), user.getEmail(), INITIAL_USD_BALANCE)
        );

        return new CreateUserResponse(user.getId(), user.getUsername(), user.getEmail(), INITIAL_USD_BALANCE);
    }

    @Transactional(readOnly = true)
    public WalletUserResponse getUser(Long userId) {
        UserAccount user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return new WalletUserResponse(user.getId(), user.getUsername(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public WalletSnapshotResponse getUserSnapshot(Long userId) {
        ensureUserExists(userId);

        BigDecimal usdBalance = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        Map<String, BigDecimal> assetBalances = new LinkedHashMap<>();
        for (WalletBalance balance : walletBalanceRepository.findAllByUser_Id(userId)) {
            if (USD_CURRENCY.equalsIgnoreCase(balance.getCurrency())) {
                usdBalance = balance.getAvailableAmount();
                continue;
            }
            assetBalances.put(balance.getCurrency(), balance.getAvailableAmount());
        }

        return new WalletSnapshotResponse(
                userId,
                usdBalance,
                assetBalances,
                getPendingOrders(userId)
        );
    }

    @Transactional(readOnly = true)
    public List<PendingOrderResponse> getPendingOrders(Long userId) {
        ensureUserExists(userId);
        return balanceAuditRecordRepository.findAllByUserIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        OrderReservationStatus.RESERVED
                ).stream()
                .map(this::toPendingOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PendingOrderResponse> getAllPendingOrders() {
        return balanceAuditRecordRepository.findAllByStatusOrderByCreatedAtAsc(OrderReservationStatus.RESERVED)
                .stream()
                .map(this::toPendingOrderResponse)
                .toList();
    }

    @Transactional
    public ReserveOrderResponse reserveBalance(ReserveOrderRequest request) {
        if (request.orderType() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Only LIMIT orders are supported in this phase");
        }
        if (!userAccountRepository.existsById(request.userId())) {
            throw new ResourceNotFoundException("User not found");
        }

        String normalizedTicker = normalizeTicker(request.targetTicker());
        if (!supportedTickers.contains(normalizedTicker)) {
            throw new IllegalArgumentException("Unsupported ticker " + normalizedTicker);
        }
        if (request.side() == null) {
            throw new IllegalArgumentException("Order side is required");
        }

        String reservedCurrency = request.side() == OrderSide.BUY
                ? USD_CURRENCY
                : extractBaseAssetCode(normalizedTicker);
        BigDecimal amountReserved = request.side() == OrderSide.BUY
                ? request.targetPrice().multiply(request.quantity()).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : request.quantity().setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        WalletBalance reservedBalance = findOrCreateBalanceForUpdate(request.userId(), reservedCurrency);
        if (reservedBalance.getAvailableAmount().compareTo(amountReserved) < 0) {
            throw new InsufficientFundsException("Insufficient " + reservedCurrency + " balance");
        }

        reservedBalance.debit(amountReserved);

        String eventId = UUID.randomUUID().toString();
        BalanceAuditRecord auditRecord = new BalanceAuditRecord(
                eventId,
                request.userId(),
                reservedCurrency,
                amountReserved,
                normalizedTicker,
                request.orderType(),
                request.side(),
                request.targetPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                Instant.now(),
                OrderReservationStatus.RESERVED
        );
        balanceAuditRecordRepository.save(auditRecord);

        BalanceReservedEvent event = new BalanceReservedEvent(
                eventId,
                request.userId().toString(),
                amountReserved,
                reservedCurrency,
                normalizedTicker,
                request.side(),
                request.orderType(),
                request.targetPrice().setScale(MONEY_SCALE, RoundingMode.HALF_UP)
        );

        publishAfterCommit(walletEventsTopic, event.userId(), event);

        return new ReserveOrderResponse(
                eventId,
                request.userId(),
                request.side().name(),
                normalizedTicker,
                amountReserved,
                reservedCurrency,
                "RESERVED"
        );
    }

    @Transactional
    public CancelOrderResponse cancelOrder(Long userId, String orderId) {
        ensureUserExists(userId);

        BalanceAuditRecord order = balanceAuditRecordRepository.findByEventIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending order not found"));
        if (!userId.equals(order.getUserId())) {
            throw new ResourceNotFoundException("Pending order not found");
        }
        if (order.getStatus() != OrderReservationStatus.RESERVED) {
            throw new OrderConflictException("Order " + orderId + " is no longer pending");
        }

        WalletBalance releasedBalance = findOrCreateBalanceForUpdate(userId, order.getCurrency());
        releasedBalance.credit(order.getAmount());

        Instant cancelledAt = Instant.now();
        order.markCancelled(cancelledAt);

        publishAfterCommit(
                walletOrderCancellationsTopic,
                userId.toString(),
                new OrderCancelledEvent(
                        order.getEventId(),
                        userId.toString(),
                        order.getAmount(),
                        order.getCurrency(),
                        order.getTargetTicker(),
                        order.getSide().name(),
                        cancelledAt.toEpochMilli()
                )
        );

        return new CancelOrderResponse(
                order.getEventId(),
                userId,
                order.getStatus().name(),
                order.getCurrency(),
                order.getAmount()
        );
    }

    @KafkaListener(topics = "${wallet.kafka.order-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onOrderExecuted(String payload) {
        try {
            acceptOrderExecuted(objectMapper.readValue(payload, OrderExecutedEvent.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize OrderExecutedEvent", exception);
        }
    }

    @Transactional
    public void acceptOrderExecuted(OrderExecutedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            throw new IllegalArgumentException("OrderExecutedEvent userId is required");
        }
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("OrderExecutedEvent orderId is required");
        }
        if (event.ticker() == null || event.ticker().isBlank()) {
            throw new IllegalArgumentException("OrderExecutedEvent ticker is required");
        }
        if (event.quantity() == null || event.quantity().signum() <= 0) {
            throw new IllegalArgumentException("OrderExecutedEvent quantity must be positive");
        }

        Long userId = Long.valueOf(event.userId());
        if (processedExecutionRecordRepository.existsById(event.orderId())) {
            return;
        }
        BalanceAuditRecord reservation = balanceAuditRecordRepository.findByEventIdForUpdate(event.orderId())
                .orElse(null);
        if (reservation != null && reservation.getStatus() == OrderReservationStatus.CANCELLED) {
            return;
        }
        String normalizedTicker = normalizeTicker(event.ticker());
        String assetCode = extractBaseAssetCode(normalizedTicker);
        String normalizedSide = event.side() == null ? "" : event.side().trim().toUpperCase(Locale.ROOT);
        Instant processedAt = Instant.now();

        if ("BUY".equals(normalizedSide)) {
            WalletBalance assetBalance = findOrCreateBalanceForUpdate(userId, assetCode);
            assetBalance.credit(event.quantity().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
            BigDecimal refund = calculateBuyRefund(event);
            if (refund.signum() > 0) {
                WalletBalance usdBalance = findOrCreateBalanceForUpdate(userId, USD_CURRENCY);
                usdBalance.credit(refund);
            }
            processedExecutionRecordRepository.save(new ProcessedExecutionRecord(event.orderId(), userId, processedAt));
            markReservationExecuted(reservation, processedAt);
            return;
        }

        if ("SELL".equals(normalizedSide)) {
            WalletBalance usdBalance = findOrCreateBalanceForUpdate(userId, USD_CURRENCY);
            BigDecimal proceeds = event.totalCost().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            usdBalance.credit(proceeds);
            processedExecutionRecordRepository.save(new ProcessedExecutionRecord(event.orderId(), userId, processedAt));
            markReservationExecuted(reservation, processedAt);
            return;
        }

        throw new IllegalArgumentException("Unsupported execution side " + event.side());
    }

    private void publishAfterCommit(String topic, String key, Object event) {
        Runnable publisher = () -> kafkaTemplate.send(topic, key, serializeEvent(event));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.run();
                }
            });
            return;
        }

        publisher.run();
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize event " + event.getClass().getSimpleName(), exception);
        }
    }

    private WalletBalance findOrCreateBalanceForUpdate(Long userId, String currency) {
        return walletBalanceRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseGet(() -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    WalletBalance createdBalance = walletBalanceRepository.save(
                            new WalletBalance(user, currency, INITIAL_ASSET_BALANCE)
                    );
                    return walletBalanceRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                            .orElse(createdBalance);
                });
    }

    private Set<String> parseSupportedTickers(String rawTickers) {
        return Arrays.stream(rawTickers.split(","))
                .map(this::normalizeTicker)
                .filter(ticker -> !ticker.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private Set<String> extractSupportedAssets() {
        LinkedHashSet<String> assets = new LinkedHashSet<>();
        for (String ticker : supportedTickers) {
            assets.add(extractBaseAssetCode(ticker));
        }
        return assets;
    }

    private String extractBaseAssetCode(String ticker) {
        if (ticker.endsWith("USDT") && ticker.length() > 4) {
            return ticker.substring(0, ticker.length() - 4);
        }
        return ticker;
    }

    private String normalizeTicker(String ticker) {
        return ticker == null ? "" : ticker.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal calculateBuyRefund(OrderExecutedEvent event) {
        if (event.reservedAmount() == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return event.reservedAmount()
                .subtract(event.totalCost())
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private void ensureUserExists(Long userId) {
        if (!userAccountRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    private PendingOrderResponse toPendingOrderResponse(BalanceAuditRecord record) {
        return new PendingOrderResponse(
                record.getEventId(),
                record.getUserId(),
                record.getTargetTicker(),
                record.getSide().name(),
                record.getOrderType().name(),
                record.getCurrency(),
                record.getAmount(),
                record.getTargetPrice(),
                record.getCreatedAt()
        );
    }

    private void markReservationExecuted(BalanceAuditRecord reservation, Instant processedAt) {
        if (reservation != null && reservation.getStatus() == OrderReservationStatus.RESERVED) {
            reservation.markExecuted(processedAt);
        }
    }
}
