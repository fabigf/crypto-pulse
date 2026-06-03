package com.cryptopulse.wallet.repository;

import com.cryptopulse.wallet.domain.ProcessedExecutionRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedExecutionRecordRepository extends JpaRepository<ProcessedExecutionRecord, String> {

    List<ProcessedExecutionRecord> findAllByUserIdOrderByProcessedAtDesc(Long userId);
}
