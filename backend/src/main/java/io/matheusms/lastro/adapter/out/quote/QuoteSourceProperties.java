package io.matheusms.lastro.adapter.out.quote;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param brapiBaseUrl brapi.dev API
 * @param brapiToken optional; without it brapi answers only a few demo tickers and no indices
 * @param brapiMaxRange longest history range the brapi plan accepts (free plan: {@code 3mo})
 * @param binanceBaseUrl Binance public market data (no key needed)
 */
@ConfigurationProperties("lastro.market")
record QuoteSourceProperties(String brapiBaseUrl, String brapiToken, String brapiMaxRange, String binanceBaseUrl) {

    boolean hasBrapiToken() {
        return brapiToken != null && !brapiToken.isBlank();
    }
}
