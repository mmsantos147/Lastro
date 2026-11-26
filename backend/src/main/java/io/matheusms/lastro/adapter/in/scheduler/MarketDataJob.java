package io.matheusms.lastro.adapter.in.scheduler;

import io.matheusms.lastro.application.ConflictException;
import io.matheusms.lastro.application.MarketDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Inbound adapter: refreshes quotes and indicators on a schedule (B3 time zone). */
@Component
class MarketDataJob {

    private static final Logger log = LoggerFactory.getLogger(MarketDataJob.class);

    private final MarketDataService marketData;

    MarketDataJob(MarketDataService marketData) {
        this.marketData = marketData;
    }

    @Scheduled(cron = "${lastro.market.refresh-cron}", zone = "America/Sao_Paulo")
    void refresh() {
        try {
            marketData.refresh();
        } catch (ConflictException exception) {
            log.info("Skipping scheduled market data refresh: one is already running");
        }
    }
}
