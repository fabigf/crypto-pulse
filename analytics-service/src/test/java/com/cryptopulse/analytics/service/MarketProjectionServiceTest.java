package com.cryptopulse.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.analytics.domain.MarketSnapshot;
import com.cryptopulse.analytics.dto.MarketSnapshotResponse;
import com.cryptopulse.analytics.event.MarketPriceEvent;
import com.cryptopulse.analytics.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class MarketProjectionServiceTest {

    @Mock
    private MarketSnapshotRepository marketSnapshotRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void shouldBootstrapMarketSnapshotOnFirstTick() {
        when(marketSnapshotRepository.findById("BTCUSDT")).thenReturn(Optional.empty());
        when(marketSnapshotRepository.save(any(MarketSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketProjectionService service = new MarketProjectionService(
                marketSnapshotRepository,
                messagingTemplate,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT"
        );

        MarketSnapshotResponse snapshot = service.applyMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("50000.00"),
                1_717_000_000_000L
        ));

        assertThat(snapshot.ticker()).isEqualTo("BTCUSDT");
        assertThat(snapshot.latestPrice()).isEqualByComparingTo("50000.00");
        assertThat(snapshot.candles()).hasSize(1);
        assertThat(snapshot.recentTicks()).hasSize(1);
        verify(messagingTemplate).convertAndSend(eq("/topic/market/BTCUSDT"), any(MarketSnapshotResponse.class));
    }

    @Test
    void shouldUpdateCurrentCandleWithinSameMinute() {
        long firstTickTimestamp = 1_717_000_000_000L;
        MarketSnapshot existing = MarketSnapshot.bootstrap("BTCUSDT", new BigDecimal("50000.00"), firstTickTimestamp);
        existing.setUpdatedAt(Instant.ofEpochMilli(firstTickTimestamp));
        long sameBucketTimestamp = existing.getCandles().getFirst().getBucketStart() + 30_000L;
        when(marketSnapshotRepository.findById("BTCUSDT")).thenReturn(Optional.of(existing));
        when(marketSnapshotRepository.save(any(MarketSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketProjectionService service = new MarketProjectionService(
                marketSnapshotRepository,
                messagingTemplate,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT"
        );

        MarketSnapshotResponse snapshot = service.applyMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("50120.00"),
                sameBucketTimestamp
        ));

        assertThat(snapshot.candles()).hasSize(1);
        assertThat(snapshot.candles().getFirst().getHigh()).isEqualByComparingTo("50120.00");
        assertThat(snapshot.candles().getFirst().getClose()).isEqualByComparingTo("50120.00");
        assertThat(snapshot.absoluteChange()).isEqualByComparingTo("120.00");
    }

    @Test
    void shouldCreateNewCandleForNewMinute() {
        MarketSnapshot existing = MarketSnapshot.bootstrap("BTCUSDT", new BigDecimal("50000.00"), 1_717_000_000_000L);
        when(marketSnapshotRepository.findById("BTCUSDT")).thenReturn(Optional.of(existing));
        when(marketSnapshotRepository.save(any(MarketSnapshot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketProjectionService service = new MarketProjectionService(
                marketSnapshotRepository,
                messagingTemplate,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT"
        );

        MarketSnapshotResponse snapshot = service.applyMarketPrice(new MarketPriceEvent(
                "BTCUSDT",
                new BigDecimal("49880.00"),
                1_717_000_120_000L
        ));

        assertThat(snapshot.candles()).hasSize(2);
        assertThat(snapshot.candles().getLast().getOpen()).isEqualByComparingTo("49880.00");
        assertThat(snapshot.percentageChange()).isEqualByComparingTo("-0.2400");
    }
}
