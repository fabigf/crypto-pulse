package com.cryptopulse.wallet.event;

import java.math.BigDecimal;

public record UserCreatedEvent(
        String userId,
        String username,
        String email,
        BigDecimal initialUsdBalance
) {
}
