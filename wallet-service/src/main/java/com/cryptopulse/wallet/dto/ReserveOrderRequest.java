package com.cryptopulse.wallet.dto;

import com.cryptopulse.wallet.domain.OrderType;
import com.cryptopulse.wallet.domain.OrderSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ReserveOrderRequest(
        @NotNull Long userId,
        @NotBlank String targetTicker,
        @NotNull OrderSide side,
        @NotNull OrderType orderType,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal targetPrice,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity
) {
}
