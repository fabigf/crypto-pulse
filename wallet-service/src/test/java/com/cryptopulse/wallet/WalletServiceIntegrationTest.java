package com.cryptopulse.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.wallet.domain.OrderSide;
import com.cryptopulse.wallet.domain.OrderType;
import com.cryptopulse.wallet.dto.CreateUserRequest;
import com.cryptopulse.wallet.dto.CreateUserResponse;
import com.cryptopulse.wallet.dto.ExecutedOrderResponse;
import com.cryptopulse.wallet.dto.PendingOrderResponse;
import com.cryptopulse.wallet.dto.ReserveOrderRequest;
import com.cryptopulse.wallet.dto.ReserveOrderResponse;
import com.cryptopulse.wallet.dto.WalletSnapshotResponse;
import com.cryptopulse.wallet.dto.WalletUserResponse;
import com.cryptopulse.wallet.event.OrderExecutedEvent;
import com.cryptopulse.wallet.exception.InsufficientFundsException;
import com.cryptopulse.wallet.repository.BalanceAuditRecordRepository;
import com.cryptopulse.wallet.repository.ProcessedExecutionRecordRepository;
import com.cryptopulse.wallet.repository.UserAccountRepository;
import com.cryptopulse.wallet.repository.WalletBalanceRepository;
import com.cryptopulse.wallet.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:walletdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.admin.auto-create=false",
        "wallet.kafka.topic=wallet-events-test",
        "wallet.kafka.user-topic=user-events-test",
        "wallet.kafka.order-cancellations-topic=order-cancellations-test"
})
class WalletServiceIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletBalanceRepository walletBalanceRepository;

    @Autowired
    private BalanceAuditRecordRepository balanceAuditRecordRepository;

    @Autowired
    private ProcessedExecutionRecordRepository processedExecutionRecordRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void cleanDatabase() {
        processedExecutionRecordRepository.deleteAll();
        balanceAuditRecordRepository.deleteAll();
        walletBalanceRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void shouldRegisterUserWithInitialUsdBalance() {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        CreateUserResponse response = walletService.registerUser(new CreateUserRequest("alice", "alice@example.com"));

        assertThat(response.userId()).isNotNull();
        assertThat(response.initialUsdBalance()).isEqualByComparingTo("10000.00000000");
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(response.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10000.00000000"));
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(response.userId(), "BTC")).isPresent();
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(response.userId(), "ETH")).isPresent();
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(response.userId(), "SOL")).isPresent();
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(response.userId(), "ADA")).isPresent();
        verify(kafkaTemplate).send(eq("user-events-test"), eq(response.userId().toString()), anyString());
    }

    @Test
    void shouldReturnRegisteredUserSummary() {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse response = walletService.registerUser(new CreateUserRequest("gina", "gina@example.com"));

        WalletUserResponse user = walletService.getUser(response.userId());

        assertThat(user.userId()).isEqualTo(response.userId());
        assertThat(user.username()).isEqualTo("gina");
        assertThat(user.email()).isEqualTo("gina@example.com");
    }

    @Test
    void shouldReserveUsdBalanceAndPublishWalletEvent() {
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("bob", "bob@example.com"));

        ReserveOrderResponse response = walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000.00"),
                new BigDecimal("0.10")
        ));

        assertThat(response.status()).isEqualTo("RESERVED");
        assertThat(response.amountReserved()).isEqualByComparingTo("5000.00000000");
        assertThat(response.side()).isEqualTo("BUY");
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("5000.00000000"));
        assertThat(balanceAuditRecordRepository.count()).isEqualTo(1L);
        verify(kafkaTemplate).send(eq("wallet-events-test"), eq(user.userId().toString()), anyString());
    }

    @Test
    void shouldExposeWalletSnapshotWithPendingOrders() {
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("nina", "nina@example.com"));

        walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000.00"),
                new BigDecimal("0.10")
        ));

        WalletSnapshotResponse snapshot = walletService.getUserSnapshot(user.userId());

        assertThat(snapshot.usdBalance()).isEqualByComparingTo("5000.00000000");
        assertThat(snapshot.pendingOrders()).hasSize(1);
        PendingOrderResponse pendingOrder = snapshot.pendingOrders().get(0);
        assertThat(pendingOrder.targetTicker()).isEqualTo("BTCUSDT");
        assertThat(pendingOrder.amountReserved()).isEqualByComparingTo("5000.00000000");
    }

    @Test
    void shouldReserveAssetBalanceForSellOrders() {
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("dave", "dave@example.com"));
        walletService.acceptOrderExecuted(new OrderExecutedEvent(
                "buy-fill-1",
                user.userId().toString(),
                "ETHUSDT",
                "BUY",
                new BigDecimal("2.00000000"),
                new BigDecimal("3500.00"),
                new BigDecimal("7000.00000000"),
                new BigDecimal("7000.00000000"),
                System.currentTimeMillis()
        ));

        ReserveOrderResponse response = walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "ETHUSDT",
                OrderSide.SELL,
                OrderType.LIMIT,
                new BigDecimal("3600.00"),
                new BigDecimal("1.50000000")
        ));

        assertThat(response.currency()).isEqualTo("ETH");
        assertThat(response.amountReserved()).isEqualByComparingTo("1.50000000");
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "ETH"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("0.50000000"));
    }

    @Test
    void shouldRejectReservationWhenBalanceIsInsufficient() {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("carol", "carol@example.com"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("25000.00"),
                new BigDecimal("1.00")
        )))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient USD balance");

        assertThat(balanceAuditRecordRepository.count()).isEqualTo(0L);
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10000.00000000"));
        verify(kafkaTemplate, never()).send(eq("wallet-events-test"), anyString(), anyString());
    }

    @Test
    void shouldCreditUsdBalanceWhenSellExecutionArrives() {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("erin", "erin@example.com"));

        walletService.acceptOrderExecuted(new OrderExecutedEvent(
                "sell-fill-1",
                user.userId().toString(),
                "SOLUSDT",
                "SELL",
                new BigDecimal("3.00000000"),
                new BigDecimal("150.00"),
                new BigDecimal("450.00000000"),
                new BigDecimal("3.00000000"),
                System.currentTimeMillis()
        ));

        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10450.00000000"));

        walletService.acceptOrderExecuted(new OrderExecutedEvent(
                "sell-fill-1",
                user.userId().toString(),
                "SOLUSDT",
                "SELL",
                new BigDecimal("3.00000000"),
                new BigDecimal("150.00"),
                new BigDecimal("450.00000000"),
                new BigDecimal("3.00000000"),
                System.currentTimeMillis()
        ));

        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10450.00000000"));
    }

    @Test
    void shouldExposeExecutionHistoryFromWalletService() {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("ivy", "ivy@example.com"));

        walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000.00"),
                new BigDecimal("0.10")
        ));

        walletService.acceptOrderExecuted(new OrderExecutedEvent(
                "history-fill-1",
                user.userId().toString(),
                "BTCUSDT",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("49999.99"),
                new BigDecimal("4999.99900000"),
                new BigDecimal("5000.00000000"),
                System.currentTimeMillis()
        ));

        java.util.List<ExecutedOrderResponse> history = walletService.getExecutionHistory(user.userId());

        assertThat(history).hasSize(1);
        ExecutedOrderResponse execution = history.getFirst();
        assertThat(execution.ticker()).isEqualTo("BTCUSDT");
        assertThat(execution.side()).isEqualTo("BUY");
        assertThat(execution.quantity()).isEqualByComparingTo("0.10000000");
        assertThat(execution.executionPrice()).isEqualByComparingTo("49999.99000000");
        assertThat(execution.totalCost()).isEqualByComparingTo("4999.99900000");
    }

    @Test
    void shouldRefundUnusedUsdWhenBuyExecutesBelowLimit() {
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("fran", "fran@example.com"));

        walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000.00"),
                new BigDecimal("0.10")
        ));

        walletService.acceptOrderExecuted(new OrderExecutedEvent(
                "buy-limit-1",
                user.userId().toString(),
                "BTCUSDT",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("49999.99"),
                new BigDecimal("4999.99900000"),
                new BigDecimal("5000.00000000"),
                System.currentTimeMillis()
        ));

        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("5000.00100000"));
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "BTC"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("0.10000000"));
    }

    @Test
    void shouldCancelPendingOrderAndReleaseReservedUsd() {
        when(kafkaTemplate.send(eq("wallet-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("order-cancellations-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("olga", "olga@example.com"));

        ReserveOrderResponse response = walletService.reserveBalance(new ReserveOrderRequest(
                user.userId(),
                "BTCUSDT",
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("50000.00"),
                new BigDecimal("0.10")
        ));

        walletService.cancelOrder(user.userId(), response.eventId());

        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10000.00000000"));
        assertThat(walletService.getPendingOrders(user.userId())).isEmpty();
        verify(kafkaTemplate).send(eq("order-cancellations-test"), eq(user.userId().toString()), anyString());
    }

    @Test
    void shouldSettleExecutionWhenConsumedThroughKafkaListener() throws Exception {
        when(kafkaTemplate.send(eq("user-events-test"), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        CreateUserResponse user = walletService.registerUser(new CreateUserRequest("helen", "helen@example.com"));

        String payload = objectMapper.writeValueAsString(new OrderExecutedEvent(
                "listener-fill-1",
                user.userId().toString(),
                "BTCUSDT",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("49999.99"),
                new BigDecimal("4999.99900000"),
                new BigDecimal("5000.00000000"),
                System.currentTimeMillis()
        ));

        walletService.onOrderExecuted(payload);

        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "USD"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("10000.00100000"));
        assertThat(walletBalanceRepository.findByUser_IdAndCurrency(user.userId(), "BTC"))
                .hasValueSatisfying(balance ->
                        assertThat(balance.getAvailableAmount()).isEqualByComparingTo("0.10000000"));
    }
}
