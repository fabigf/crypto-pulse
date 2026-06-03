package com.cryptopulse.analytics.controller;

import com.cryptopulse.analytics.dto.MarketChartResponse;
import com.cryptopulse.analytics.dto.MarketSnapshotResponse;
import com.cryptopulse.analytics.dto.MarketSummaryResponse;
import com.cryptopulse.analytics.dto.PortfolioSnapshot;
import com.cryptopulse.analytics.service.MarketChartService;
import com.cryptopulse.analytics.service.MarketProjectionService;
import com.cryptopulse.analytics.service.PortfolioProjectionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final PortfolioProjectionService portfolioProjectionService;
    private final MarketProjectionService marketProjectionService;
    private final MarketChartService marketChartService;

    public AnalyticsController(
            PortfolioProjectionService portfolioProjectionService,
            MarketProjectionService marketProjectionService,
            MarketChartService marketChartService
    ) {
        this.portfolioProjectionService = portfolioProjectionService;
        this.marketProjectionService = marketProjectionService;
        this.marketChartService = marketChartService;
    }

    @GetMapping("/portfolios/{userId}")
    public PortfolioSnapshot getPortfolio(@PathVariable String userId) {
        return portfolioProjectionService.toSnapshot(portfolioProjectionService.getPortfolio(userId));
    }

    @GetMapping("/markets")
    public List<MarketSummaryResponse> getMarkets() {
        return marketProjectionService.getTrackedMarkets();
    }

    @GetMapping("/market/{ticker}")
    public MarketSnapshotResponse getMarketSnapshot(@PathVariable String ticker) {
        return marketProjectionService.getMarketSnapshot(ticker);
    }

    @GetMapping("/market/{ticker}/chart")
    public MarketChartResponse getMarketChart(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1d") String timeframe,
            @RequestParam(required = false) Integer limit
    ) {
        return marketChartService.getMarketChart(ticker, timeframe, limit);
    }
}
