package com.cryptopulse.marketfeeder.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BinanceTickerPriceResponse(
        String symbol,
        String price
) {
}

