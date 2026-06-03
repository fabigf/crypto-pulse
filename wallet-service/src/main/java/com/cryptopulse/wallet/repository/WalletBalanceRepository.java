package com.cryptopulse.wallet.repository;

import com.cryptopulse.wallet.domain.WalletBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wb from WalletBalance wb where wb.user.id = :userId and wb.currency = :currency")
    Optional<WalletBalance> findByUserIdAndCurrencyForUpdate(@Param("userId") Long userId,
                                                             @Param("currency") String currency);

    Optional<WalletBalance> findByUser_IdAndCurrency(Long userId, String currency);

    List<WalletBalance> findAllByUser_Id(Long userId);
}
