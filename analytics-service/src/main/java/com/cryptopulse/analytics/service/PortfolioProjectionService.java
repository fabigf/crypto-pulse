package com.cryptopulse.analytics.service;

import com.cryptopulse.analytics.domain.ExecutedTrade;
import com.cryptopulse.analytics.domain.UserPortfolio;
import com.cryptopulse.analytics.dto.PortfolioSnapshot;
import com.cryptopulse.analytics.event.BalanceReservedEvent;
import com.cryptopulse.analytics.event.OrderExecutedEvent;
import com.cryptopulse.analytics.event.UserCreatedEvent;
import com.cryptopulse.analytics.exception.ResourceNotFoundException;
import com.cryptopulse.analytics.repository.UserPortfolioRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PortfolioProjectionService {

    private static final BigDecimal INITIAL_USD_BALANCE = new BigDecimal("10000.00000000");

    private final UserPortfolioRepository userPortfolioRepository;
    private final MongoTemplate mongoTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public PortfolioProjectionService(
            UserPortfolioRepository userPortfolioRepository,
            MongoTemplate mongoTemplate,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.userPortfolioRepository = userPortfolioRepository;
        this.mongoTemplate = mongoTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public UserPortfolio getPortfolio(String userId) {
        validateUserId(userId);
        return userPortfolioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found for user " + userId));
    }

    public PortfolioSnapshot toSnapshot(UserPortfolio portfolio) {
        return new PortfolioSnapshot(
                portfolio.getUserId(),
                portfolio.getUsdBalance(),
                portfolio.getAssetBalances(),
                portfolio.getTrades(),
                portfolio.getUpdatedAt()
        );
    }

    public UserPortfolio createPortfolio(UserCreatedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank()) {
            throw new IllegalArgumentException("UserCreatedEvent userId is required");
        }
        if (event.initialUsdBalance() == null || event.initialUsdBalance().signum() < 0) {
            throw new IllegalArgumentException("UserCreatedEvent initialUsdBalance must not be negative");
        }

        UserPortfolio existingPortfolio = userPortfolioRepository.findById(event.userId()).orElse(null);
        if (existingPortfolio != null) {
            return existingPortfolio;
        }

        UserPortfolio createdPortfolio = saveBootstrapPortfolio(event.userId(), event.initialUsdBalance());
        publishPortfolio(createdPortfolio);
        return createdPortfolio;
    }

    public UserPortfolio applyBalanceReserved(BalanceReservedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank() || event.eventId() == null) {
            throw new IllegalArgumentException("BalanceReservedEvent is incomplete");
        }
        if (event.amountReserved() == null || event.amountReserved().signum() <= 0) {
            throw new IllegalArgumentException("BalanceReservedEvent amountReserved must be positive");
        }

        ensurePortfolioExistsForEvents(event.userId());

        Query query = Query.query(Criteria.where("_id").is(event.userId())
                .and("processedReservationIds").ne(event.eventId()));

        Document updateDocument = new Document()
                .append("$addToSet", new Document("processedReservationIds", event.eventId()))
                .append("$set", new Document("updatedAt", Instant.now()));
        if ("USD".equalsIgnoreCase(event.currency())) {
            updateDocument.append("$inc", new Document("usdBalance", toDecimal128(event.amountReserved().negate())));
        } else {
            updateDocument.append("$inc", new Document(
                    "assetBalances." + event.currency(),
                    toDecimal128(event.amountReserved().negate())
            ));
        }
        Update update = Update.fromDocument(updateDocument);

        UserPortfolio updatedPortfolio = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                UserPortfolio.class
        );

        if (updatedPortfolio != null) {
            publishPortfolio(updatedPortfolio);
            return updatedPortfolio;
        }

        return getPortfolioOrEventBootstrap(event.userId());
    }

    public UserPortfolio applyOrderExecuted(OrderExecutedEvent event) {
        if (event == null || event.userId() == null || event.userId().isBlank() || event.orderId() == null) {
            throw new IllegalArgumentException("OrderExecutedEvent is incomplete");
        }
        if (event.quantity() == null || event.quantity().signum() <= 0) {
            throw new IllegalArgumentException("OrderExecutedEvent quantity must be positive");
        }

        ensurePortfolioExistsForEvents(event.userId());

        String assetCode = extractAssetCode(event.ticker());
        ExecutedTrade trade = new ExecutedTrade(
                event.orderId(),
                event.ticker(),
                event.side(),
                event.quantity(),
                event.executionPrice(),
                event.totalCost(),
                Instant.ofEpochMilli(event.timestamp())
        );

        Query query = Query.query(Criteria.where("_id").is(event.userId())
                .and("processedExecutionIds").ne(event.orderId()));

        Document incDocument = new Document();
        if ("SELL".equalsIgnoreCase(event.side())) {
            incDocument.put("usdBalance", toDecimal128(event.totalCost()));
        } else {
            incDocument.put("assetBalances." + assetCode, toDecimal128(event.quantity()));
            BigDecimal refund = calculateBuyRefund(event);
            if (refund.signum() > 0) {
                incDocument.put("usdBalance", toDecimal128(refund));
            }
        }

        Document updateDocument = new Document()
                .append("$addToSet", new Document("processedExecutionIds", event.orderId()))
                .append("$push", new Document("trades", toMongoTradeDocument(trade)))
                .append("$set", new Document("updatedAt", Instant.now()));
        if (!incDocument.isEmpty()) {
            updateDocument.append("$inc", incDocument);
        }
        Update update = Update.fromDocument(updateDocument);

        UserPortfolio updatedPortfolio = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                UserPortfolio.class
        );

        if (updatedPortfolio != null) {
            publishPortfolio(updatedPortfolio);
            return updatedPortfolio;
        }

        return getPortfolioOrEventBootstrap(event.userId());
    }

    private UserPortfolio ensurePortfolioExistsForEvents(String userId) {
        return userPortfolioRepository.findById(userId)
                .orElseGet(() -> {
                    UserPortfolio bootstrapPortfolio = saveBootstrapPortfolio(userId, INITIAL_USD_BALANCE);
                    publishPortfolio(bootstrapPortfolio);
                    return bootstrapPortfolio;
                });
    }

    private UserPortfolio getPortfolioOrEventBootstrap(String userId) {
        return userPortfolioRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found for user " + userId));
    }

    private UserPortfolio saveBootstrapPortfolio(String userId, BigDecimal initialUsdBalance) {
        UserPortfolio bootstrapPortfolio = UserPortfolio.bootstrap(userId, initialUsdBalance);
        try {
            return userPortfolioRepository.save(bootstrapPortfolio);
        } catch (RuntimeException duplicateInsert) {
            return userPortfolioRepository.findById(userId)
                    .orElseThrow(() -> duplicateInsert);
        }
    }

    private void publishPortfolio(UserPortfolio portfolio) {
        messagingTemplate.convertAndSend("/topic/portfolio/" + portfolio.getUserId(), toSnapshot(portfolio));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
    }

    private String extractAssetCode(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return "UNKNOWN";
        }
        if (ticker.endsWith("USDT") && ticker.length() > 4) {
            return ticker.substring(0, ticker.length() - 4);
        }
        return ticker;
    }

    private BigDecimal calculateBuyRefund(OrderExecutedEvent event) {
        if (event.reservedAmount() == null) {
            return BigDecimal.ZERO;
        }
        return event.reservedAmount().subtract(event.totalCost()).max(BigDecimal.ZERO);
    }

    private Decimal128 toDecimal128(BigDecimal amount) {
        return new Decimal128(amount);
    }

    private Document toMongoTradeDocument(ExecutedTrade trade) {
        return new Document("orderId", trade.getOrderId())
                .append("ticker", trade.getTicker())
                .append("side", trade.getSide())
                .append("quantity", toDecimal128(trade.getQuantity()))
                .append("executionPrice", toDecimal128(trade.getExecutionPrice()))
                .append("totalCost", toDecimal128(trade.getTotalCost()))
                .append("executedAt", trade.getExecutedAt());
    }
}
