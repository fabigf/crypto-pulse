package com.cryptopulse.wallet.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record WalletSnapshotResponse(
        Long userId,
        BigDecimal usdBalance,
        Map<String, BigDecimal> assetBalances,
        List<PendingOrderResponse> pendingOrders
) {
}
