package com.cryptopulse.marketfeeder.client;

import com.cryptopulse.marketfeeder.model.BinanceTickerPriceResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BinanceClient {

    private final RestClient restClient;

    public BinanceClient(RestClient binanceRestClient) {
        this.restClient = binanceRestClient;
    }

    public BinanceTickerPriceResponse fetchTickerPrice(String symbol) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v3/ticker/price")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(BinanceTickerPriceResponse.class);
    }
}

