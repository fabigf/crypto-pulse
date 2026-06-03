package com.cryptopulse.wallet.controller;

import com.cryptopulse.wallet.dto.CreateUserRequest;
import com.cryptopulse.wallet.dto.CreateUserResponse;
import com.cryptopulse.wallet.dto.CancelOrderResponse;
import com.cryptopulse.wallet.dto.PendingOrderResponse;
import com.cryptopulse.wallet.dto.ReserveOrderRequest;
import com.cryptopulse.wallet.dto.ReserveOrderResponse;
import com.cryptopulse.wallet.dto.WalletSnapshotResponse;
import com.cryptopulse.wallet.dto.WalletUserResponse;
import com.cryptopulse.wallet.service.WalletService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return walletService.registerUser(request);
    }

    @GetMapping("/users/{userId}")
    public WalletUserResponse getUser(@PathVariable Long userId) {
        return walletService.getUser(userId);
    }

    @GetMapping("/users/{userId}/snapshot")
    public WalletSnapshotResponse getUserSnapshot(@PathVariable Long userId) {
        return walletService.getUserSnapshot(userId);
    }

    @GetMapping("/users/{userId}/orders/pending")
    public List<PendingOrderResponse> getPendingOrders(@PathVariable Long userId) {
        return walletService.getPendingOrders(userId);
    }

    @GetMapping("/orders/pending")
    public List<PendingOrderResponse> getAllPendingOrders() {
        return walletService.getAllPendingOrders();
    }

    @PostMapping("/orders/reserve")
    public ReserveOrderResponse reserveOrder(@Valid @RequestBody ReserveOrderRequest request) {
        return walletService.reserveBalance(request);
    }

    @PostMapping("/users/{userId}/orders/{orderId}/cancel")
    public CancelOrderResponse cancelOrder(@PathVariable Long userId, @PathVariable String orderId) {
        return walletService.cancelOrder(userId, orderId);
    }
}
