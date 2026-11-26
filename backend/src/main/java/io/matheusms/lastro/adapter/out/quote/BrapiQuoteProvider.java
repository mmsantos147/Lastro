package io.matheusms.lastro.adapter.out.quote;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import io.matheusms.lastro.domain.port.QuoteProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** B3-listed stocks, FIIs and ETFs. Tesouro Direto is not covered by brapi: its quotes are entered manually. */
@Component
class BrapiQuoteProvider implements QuoteProvider {

    private static final Set<AssetClass> SUPPORTED = Set.of(AssetClass.STOCK, AssetClass.FII, AssetClass.ETF);

    private final BrapiClient brapi;

    BrapiQuoteProvider(BrapiClient brapi) {
        this.brapi = brapi;
    }

    @Override
    public boolean supports(Asset asset) {
        return SUPPORTED.contains(asset.assetClass()) && asset.quoteCurrency() == Currency.BRL;
    }

    @Override
    public List<Quote> dailyCloses(Asset asset, LocalDate from, LocalDate to) {
        return brapi.dailyCloses(asset.code(), from).stream()
                .filter(close -> !close.date().isBefore(from) && !close.date().isAfter(to))
                .map(close -> new Quote(asset.id(), close.date(), new Money(close.close(), Currency.BRL),
                        QuoteSource.BRAPI))
                .toList();
    }
}
