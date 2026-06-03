package com.cryptopulse.marketfeeder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarketFeederServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketFeederServiceApplication.class, args);
    }
}

