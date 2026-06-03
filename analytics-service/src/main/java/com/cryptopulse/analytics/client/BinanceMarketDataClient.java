package com.cryptopulse.analytics.client;

import com.cryptopulse.analytics.domain.MarketCandle;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BinanceMarketDataClient {

    private final RestClient restClient;

    public BinanceMarketDataClient(RestClient binanceAnalyticsRestClient) {
        this.restClient = binanceAnalyticsRestClient;
    }

    public List<MarketCandle> fetchUiCandles(String ticker, String interval, int limit) {
        Object[][] payload = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/uiKlines")
                        .queryParam("symbol", ticker)
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(Object[][].class);

        if (payload == null || payload.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(payload)
                .map(this::toMarketCandle)
                .toList();
    }

    private MarketCandle toMarketCandle(Object[] rawCandle) {
        if (rawCandle == null || rawCandle.length < 5) {
            throw new IllegalStateException("Received malformed kline payload from Binance");
        }

        return new MarketCandle(
                toLong(rawCandle[0]),
                toBigDecimal(rawCandle[1]),
                toBigDecimal(rawCandle[2]),
                toBigDecimal(rawCandle[3]),
                toBigDecimal(rawCandle[4])
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
