package com.cryptopulse.wallet.repository;

import com.cryptopulse.wallet.domain.ProcessedExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedExecutionRecordRepository extends JpaRepository<ProcessedExecutionRecord, String> {
}
