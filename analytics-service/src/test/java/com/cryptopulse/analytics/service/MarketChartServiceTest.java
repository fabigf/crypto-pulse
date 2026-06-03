package com.cryptopulse.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.analytics.client.BinanceMarketDataClient;
import com.cryptopulse.analytics.domain.MarketCandle;
import com.cryptopulse.analytics.domain.MarketSnapshot;
import com.cryptopulse.analytics.dto.MarketChartResponse;
import com.cryptopulse.analytics.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketChartServiceTest {

    @Mock
    private BinanceMarketDataClient binanceMarketDataClient;

    @Mock
    private MarketSnapshotRepository marketSnapshotRepository;

    @Test
    void shouldReturnChartForSupportedTickerAndTimeframe() {
        List<MarketCandle> candles = List.of(
                new MarketCandle(1_717_000_000_000L, new BigDecimal("70000.00"), new BigDecimal("71000.00"), new BigDecimal("69500.00"), new BigDecimal("70500.00")),
                new MarketCandle(1_717_086_400_000L, new BigDecimal("70500.00"), new BigDecimal("71500.00"), new BigDecimal("70000.00"), new BigDecimal("71250.00"))
        );
        MarketSnapshot snapshot = MarketSnapshot.bootstrap("BTCUSDT", new BigDecimal("69900.00"), 1_717_000_000_000L);
        snapshot.setLatestPrice(new BigDecimal("71260.00"));
        snapshot.setSessionOpenPrice(new BigDecimal("69900.00"));
        snapshot.setUpdatedAt(Instant.parse("2026-05-29T18:00:00Z"));

        when(binanceMarketDataClient.fetchUiCandles("BTCUSDT", "1d", 120)).thenReturn(candles);
        when(marketSnapshotRepository.findById("BTCUSDT")).thenReturn(Optional.of(snapshot));

        MarketChartService service = new MarketChartService(
                binanceMarketDataClient,
                marketSnapshotRepository,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT",
                120
        );

        MarketChartResponse response = service.getMarketChart("btcusdt", "1d", null);

        assertThat(response.ticker()).isEqualTo("BTCUSDT");
        assertThat(response.timeframe()).isEqualTo("1d");
        assertThat(response.latestPrice()).isEqualByComparingTo("71260.00");
        assertThat(response.absoluteChange()).isEqualByComparingTo("1360.00");
        assertThat(response.percentageChange()).isEqualByComparingTo("1.9456");
        assertThat(response.candles()).hasSize(2);
    }

    @Test
    void shouldClampLimitAndAcceptMonthlyTimeframe() {
        when(binanceMarketDataClient.fetchUiCandles("ETHUSDT", "1M", 500)).thenReturn(List.of());
        when(marketSnapshotRepository.findById("ETHUSDT")).thenReturn(Optional.empty());

        MarketChartService service = new MarketChartService(
                binanceMarketDataClient,
                marketSnapshotRepository,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT",
                120
        );

        service.getMarketChart("ETHUSDT", "1M", 5_000);

        verify(binanceMarketDataClient).fetchUiCandles("ETHUSDT", "1M", 500);
    }

    @Test
    void shouldRejectUnsupportedTicker() {
        MarketChartService service = new MarketChartService(
                binanceMarketDataClient,
                marketSnapshotRepository,
                "BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT",
                120
        );

        assertThatThrownBy(() -> service.getMarketChart("DOGEUSDT", "4h", 120))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DOGEUSDT");
    }
}
