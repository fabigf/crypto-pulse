package com.cryptopulse.wallet.exception;

import java.time.Instant;

public record ApiErrorResponse(
        String message,
        int status,
        Instant timestamp
) {
}

