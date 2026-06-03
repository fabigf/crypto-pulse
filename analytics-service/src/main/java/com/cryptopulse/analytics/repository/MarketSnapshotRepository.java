package com.cryptopulse.analytics.repository;

import com.cryptopulse.analytics.domain.MarketSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MarketSnapshotRepository extends MongoRepository<MarketSnapshot, String> {
}
