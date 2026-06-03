package com.cryptopulse.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name = "wallet_balance", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_balance_user_currency", columnNames = {"user_id", "currency"})
})
public class WalletBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 16)
    private String currency;

    @Column(name = "available_amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal availableAmount;

    protected WalletBalance() {
    }

    public WalletBalance(UserAccount user, String currency, BigDecimal availableAmount) {
        this.user = user;
        this.currency = currency;
        this.availableAmount = availableAmount;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public void debit(BigDecimal amount) {
        this.availableAmount = this.availableAmount.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.availableAmount = this.availableAmount.add(amount);
    }
}
