package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;

public record CreateUserResponse(
        Long userId,
        String username,
        String email,
        BigDecimal initialUsdBalance
) {
}

