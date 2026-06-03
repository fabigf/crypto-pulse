package com.cryptopulse.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayCorsWebFilterConfiguration {

    private final List<String> allowedOrigins;

    public GatewayCorsWebFilterConfiguration(@Value("${gateway.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }

    @Bean
    public WebFilter gatewayCorsWebFilter() {
        return new GatewayCorsWebFilter();
    }

    private final class GatewayCorsWebFilter implements WebFilter, Ordered {

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (origin == null || !allowedOrigins.contains(origin)) {
                return chain.filter(exchange);
            }

            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            HttpHeaders responseHeaders = exchange.getResponse().getHeaders();

            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    requestHeaders.getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS) != null
                            ? requestHeaders.getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)
                            : "Authorization,Content-Type,Accept,Origin");
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
            responseHeaders.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
            responseHeaders.set(HttpHeaders.VARY, "Origin,Access-Control-Request-Method,Access-Control-Request-Headers");

            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
            }

            return chain.filter(exchange);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
