package com.cryptopulse.wallet.dto;

public record WalletUserResponse(
        Long userId,
        String username,
        String email
) {
}
