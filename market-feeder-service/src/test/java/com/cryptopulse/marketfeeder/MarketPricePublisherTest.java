package com.cryptopulse.marketfeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptopulse.marketfeeder.client.BinanceClient;
import com.cryptopulse.marketfeeder.model.BinanceTickerPriceResponse;
import com.cryptopulse.marketfeeder.service.MarketPricePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class MarketPricePublisherTest {

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishesMappedBinancePayloadToKafka() throws Exception {
        when(binanceClient.fetchTickerPrice("BTCUSDT"))
                .thenReturn(new BinanceTickerPriceResponse("BTCUSDT", "68500.42"));
        when(binanceClient.fetchTickerPrice("ETHUSDT"))
                .thenReturn(new BinanceTickerPriceResponse("ETHUSDT", "3500.42"));
        when(kafkaTemplate.send(eq("market-prices"), eq("BTCUSDT"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send(eq("market-prices"), eq("ETHUSDT"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        MarketPricePublisher publisher = new MarketPricePublisher(
                binanceClient,
                kafkaTemplate,
                objectMapper,
                "market-prices",
                "BTCUSDT,ETHUSDT"
        );

        publisher.publishLatestMarketPrice();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("market-prices"), eq("BTCUSDT"), payloadCaptor.capture());
        verify(kafkaTemplate).send(eq("market-prices"), eq("ETHUSDT"), org.mockito.ArgumentMatchers.anyString());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("ticker").asText()).isEqualTo("BTCUSDT");
        assertThat(payload.get("price").decimalValue()).isEqualByComparingTo("68500.42");
        assertThat(payload.get("timestamp").asLong()).isPositive();
    }

    @Test
    void shouldNotPublishWhenBinanceFails() {
        when(binanceClient.fetchTickerPrice("BTCUSDT"))
                .thenThrow(new RestClientException("timeout"));

        MarketPricePublisher publisher = new MarketPricePublisher(
                binanceClient,
                kafkaTemplate,
                objectMapper,
                "market-prices",
                "BTCUSDT"
        );

        publisher.publishLatestMarketPrice();

        org.mockito.Mockito.verifyNoInteractions(kafkaTemplate);
    }
}
