package com.cryptopulse.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

class GatewayCorsWebFilterConfigurationTest {

    private final GatewayCorsWebFilterConfiguration configuration =
            new GatewayCorsWebFilterConfiguration("http://localhost:5173,http://127.0.0.1:5173");

    private final WebFilter filter = configuration.gatewayCorsWebFilter();

    @Test
    void shouldShortCircuitAllowedPreflightRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/wallet/users")
                        .header(ORIGIN, "http://localhost:5173")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                        .build()
        );

        filter.filter(exchange, passthroughChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo("http://localhost:5173");
        assertThat(exchange.getResponse().getHeaders().getFirst(ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .isEqualTo("true");
        assertThat(exchange.getResponse().getHeaders().getFirst(ACCESS_CONTROL_ALLOW_METHODS))
                .isEqualTo("GET,POST,PUT,PATCH,DELETE,OPTIONS");
        assertThat(exchange.getResponse().getHeaders().getFirst(ACCESS_CONTROL_ALLOW_HEADERS))
                .isEqualTo("content-type");
    }

    @Test
    void shouldIgnoreOriginsOutsideWhitelist() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/wallet/users")
                        .header(ORIGIN, "http://evil.example")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .build()
        );

        filter.filter(exchange, passthroughChain()).block();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN)).isFalse();
    }

    private WebFilterChain passthroughChain() {
        return exchange -> Mono.empty();
    }
}
