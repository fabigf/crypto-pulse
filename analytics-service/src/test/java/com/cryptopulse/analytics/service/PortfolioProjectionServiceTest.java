package com.cryptopulse.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.analytics.domain.UserPortfolio;
import com.cryptopulse.analytics.dto.PortfolioSnapshot;
import com.cryptopulse.analytics.event.BalanceReservedEvent;
import com.cryptopulse.analytics.event.OrderExecutedEvent;
import com.cryptopulse.analytics.event.UserCreatedEvent;
import com.cryptopulse.analytics.exception.ResourceNotFoundException;
import com.cryptopulse.analytics.repository.UserPortfolioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class PortfolioProjectionServiceTest {

    @Mock
    private UserPortfolioRepository userPortfolioRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void shouldRejectReadWhenPortfolioDoesNotExistYet() {
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.empty());

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        assertThatThrownBy(() -> service.getPortfolio("42"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Portfolio not found for user 42");
    }

    @Test
    void shouldCreatePortfolioFromUserCreatedEvent() {
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.empty());
        when(userPortfolioRepository.save(any(UserPortfolio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        UserPortfolio portfolio = service.createPortfolio(new UserCreatedEvent(
                "42",
                "alice",
                "alice@example.com",
                new BigDecimal("10000.00000000")
        ));

        assertThat(portfolio.getUserId()).isEqualTo("42");
        assertThat(portfolio.getUsdBalance()).isEqualByComparingTo("10000.00000000");
        assertThat(portfolio.getAssetBalances()).isEmpty();
        assertThat(portfolio.getTrades()).isEmpty();
        verify(messagingTemplate).convertAndSend(eq("/topic/portfolio/42"), any(PortfolioSnapshot.class));
    }

    @Test
    void shouldProjectReservedBalanceAndBroadcastPortfolio() {
        UserPortfolio updatedPortfolio = UserPortfolio.bootstrap("42", new BigDecimal("5000.00000000"));
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.of(UserPortfolio.bootstrap("42", new BigDecimal("10000.00000000"))));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserPortfolio.class)))
                .thenReturn(updatedPortfolio);

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        UserPortfolio result = service.applyBalanceReserved(new BalanceReservedEvent(
                "reserve-1",
                "42",
                new BigDecimal("5000.00000000"),
                "USD",
                "BTCUSDT",
                "BUY",
                "LIMIT",
                new BigDecimal("50000.00")
        ));

        assertThat(result.getUsdBalance()).isEqualByComparingTo("5000.00000000");

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(UserPortfolio.class));
        Document updateDocument = updateCaptor.getValue().getUpdateObject();
        assertThat(updateDocument).containsKeys("$inc", "$addToSet", "$set");
        assertThat(((Document) updateDocument.get("$inc"))).containsKey("usdBalance");
        assertThat(((Document) updateDocument.get("$inc")).get("usdBalance"))
                .isEqualTo(new Decimal128(new BigDecimal("-5000.00000000")));
        assertThat(((Document) updateDocument.get("$addToSet"))).containsKey("processedReservationIds");
        assertThat(((Document) updateDocument.get("$set"))).containsKey("updatedAt");
        verify(messagingTemplate).convertAndSend(eq("/topic/portfolio/42"), any(PortfolioSnapshot.class));
    }

    @Test
    void shouldProjectSellReservationIntoAssetBalance() {
        UserPortfolio updatedPortfolio = UserPortfolio.bootstrap("42", new BigDecimal("10000.00000000"));
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.of(updatedPortfolio));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserPortfolio.class)))
                .thenReturn(updatedPortfolio);

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        service.applyBalanceReserved(new BalanceReservedEvent(
                "reserve-asset-1",
                "42",
                new BigDecimal("1.50000000"),
                "ETH",
                "ETHUSDT",
                "SELL",
                "LIMIT",
                new BigDecimal("3500.00")
        ));

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(UserPortfolio.class));
        Document updateDocument = updateCaptor.getValue().getUpdateObject();
        assertThat(((Document) updateDocument.get("$inc"))).containsEntry(
                "assetBalances.ETH",
                new Decimal128(new BigDecimal("-1.50000000"))
        );
    }

    @Test
    void shouldProjectExecutionIntoAssetBalanceAndHistory() {
        LinkedHashMap<String, BigDecimal> balances = new LinkedHashMap<>();
        balances.put("BTC", new BigDecimal("0.10000000"));
        UserPortfolio updatedPortfolio = new UserPortfolio(
                "42",
                new BigDecimal("5000.00100000"),
                balances,
                List.of(),
                List.of("reserve-1"),
                List.of("order-1"),
                Instant.now()
        );
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.of(UserPortfolio.bootstrap("42", new BigDecimal("5000.00000000"))));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserPortfolio.class)))
                .thenReturn(updatedPortfolio);

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        UserPortfolio result = service.applyOrderExecuted(new OrderExecutedEvent(
                "order-1",
                "42",
                "BTCUSDT",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("49999.99"),
                new BigDecimal("4999.99900000"),
                new BigDecimal("5000.00000000"),
                System.currentTimeMillis()
        ));

        assertThat(result.getAssetBalances()).containsEntry("BTC", new BigDecimal("0.10000000"));

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(UserPortfolio.class));
        Document updateDocument = updateCaptor.getValue().getUpdateObject();
        assertThat(updateDocument).containsKeys("$inc", "$addToSet", "$push", "$set");
        assertThat(((Document) updateDocument.get("$inc"))).containsEntry(
                "assetBalances.BTC",
                new Decimal128(new BigDecimal("0.10000000"))
        );
        assertThat(((Document) updateDocument.get("$inc"))).containsEntry(
                "usdBalance",
                new Decimal128(new BigDecimal("0.00100000"))
        );
        assertThat(((Document) updateDocument.get("$addToSet"))).containsKey("processedExecutionIds");
        assertThat(((Document) updateDocument.get("$push"))).containsKey("trades");
        assertThat(((Document) updateDocument.get("$set"))).containsKey("updatedAt");
        verify(messagingTemplate).convertAndSend(eq("/topic/portfolio/42"), any(PortfolioSnapshot.class));
    }

    @Test
    void shouldProjectSellExecutionIntoUsdBalanceAndHistory() {
        UserPortfolio updatedPortfolio = UserPortfolio.bootstrap("42", new BigDecimal("10400.00000000"));
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.of(UserPortfolio.bootstrap("42", new BigDecimal("5000.00000000"))));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserPortfolio.class)))
                .thenReturn(updatedPortfolio);

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        service.applyOrderExecuted(new OrderExecutedEvent(
                "order-sell-1",
                "42",
                "ETHUSDT",
                "SELL",
                new BigDecimal("1.50000000"),
                new BigDecimal("3600.00"),
                new BigDecimal("5400.00000000"),
                new BigDecimal("1.50000000"),
                System.currentTimeMillis()
        ));

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(), any(FindAndModifyOptions.class), eq(UserPortfolio.class));
        Document updateDocument = updateCaptor.getValue().getUpdateObject();
        assertThat(((Document) updateDocument.get("$inc"))).containsEntry(
                "usdBalance",
                new Decimal128(new BigDecimal("5400.00000000"))
        );
    }

    @Test
    void shouldIgnoreDuplicateExecutionWithoutBroadcasting() {
        when(userPortfolioRepository.findById("42")).thenReturn(Optional.of(UserPortfolio.bootstrap("42", new BigDecimal("5000.00000000"))));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(UserPortfolio.class)))
                .thenReturn(null);

        PortfolioProjectionService service = new PortfolioProjectionService(
                userPortfolioRepository,
                mongoTemplate,
                messagingTemplate
        );

        UserPortfolio result = service.applyOrderExecuted(new OrderExecutedEvent(
                "order-1",
                "42",
                "BTCUSDT",
                "BUY",
                new BigDecimal("0.10000000"),
                new BigDecimal("49999.99"),
                new BigDecimal("4999.99900000"),
                new BigDecimal("5000.00000000"),
                System.currentTimeMillis()
        ));

        assertThat(result.getUserId()).isEqualTo("42");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(PortfolioSnapshot.class));
    }
}
