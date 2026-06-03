package com.cryptopulse.analytics.repository;

import com.cryptopulse.analytics.domain.UserPortfolio;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserPortfolioRepository extends MongoRepository<UserPortfolio, String> {
}
