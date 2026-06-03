package com.cryptopulse.marketfeeder.service;

import com.cryptopulse.marketfeeder.client.BinanceClient;
import com.cryptopulse.marketfeeder.model.BinanceTickerPriceResponse;
import com.cryptopulse.marketfeeder.model.MarketPriceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class MarketPricePublisher {

    private static final Logger log = LoggerFactory.getLogger(MarketPricePublisher.class);

    private final BinanceClient binanceClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final Set<String> symbols;

    public MarketPricePublisher(
            BinanceClient binanceClient,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${market.kafka.topic}") String topic,
            @Value("${market.binance.symbols}") String symbols
    ) {
        this.binanceClient = binanceClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.symbols = parseSymbols(symbols);
    }

    @Scheduled(fixedRateString = "${market.poll.fixed-rate-ms}")
    public void publishLatestMarketPrice() {
        for (String symbol : symbols) {
            publishSymbol(symbol);
        }
    }

    private void publishSymbol(String symbol) {
        try {
            BinanceTickerPriceResponse response = binanceClient.fetchTickerPrice(symbol);
            if (response == null || response.symbol() == null || response.price() == null) {
                log.warn("No complete market price received for symbol {}", symbol);
                return;
            }
            if (!symbol.equals(response.symbol())) {
                log.warn("Ignoring market price for unexpected symbol {}", response.symbol());
                return;
            }

            MarketPriceEvent event = new MarketPriceEvent(
                    response.symbol(),
                    new BigDecimal(response.price()),
                    Instant.now().toEpochMilli()
            );
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, symbol, payload);
            log.debug("Published market price for {} to topic {}", symbol, topic);
        } catch (RestClientException | NumberFormatException exception) {
            log.warn("Could not fetch market price from Binance for {}: {}", symbol, exception.getMessage());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize market price event", exception);
        }
    }

    private Set<String> parseSymbols(String rawSymbols) {
        return Arrays.stream(rawSymbols.split(","))
                .map(symbol -> symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT))
                .filter(symbol -> !symbol.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }
}
