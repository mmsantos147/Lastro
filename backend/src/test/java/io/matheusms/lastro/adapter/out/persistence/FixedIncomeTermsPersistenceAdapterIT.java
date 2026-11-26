package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import io.matheusms.lastro.domain.model.FixedIncomeIndexer;
import io.matheusms.lastro.domain.model.FixedIncomeTerms;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.model.QuoteSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class FixedIncomeTermsPersistenceAdapterIT {

    @Autowired
    private FixedIncomeTermsPersistenceAdapter terms;
    @Autowired
    private AssetPersistenceAdapter assets;
    @Autowired
    private QuotePersistenceAdapter quotes;

    @Test
    void shouldStoreAndReplaceTheTermsOfAFixedIncomeAsset() {
        var assetId = assets.save(new Asset(null, "CDB6255F9RH", "CDB Banco (CDB6255F9RH)", AssetClass.FIXED_INCOME,
                null, Currency.BRL, true)).id();
        terms.save(new FixedIncomeTerms(assetId, "Banco", FixedIncomeIndexer.CDI, new BigDecimal("102.5"),
                BigDecimal.ZERO, LocalDate.of(2027, 6, 10), true));

        var replaced = terms.save(new FixedIncomeTerms(assetId, null, FixedIncomeIndexer.IPCA, null,
                new BigDecimal("6.25"), null, false));

        assertThat(terms.findByAsset(assetId)).contains(replaced);
        assertThat(terms.findAll()).extracting(FixedIncomeTerms::assetId).containsOnlyOnce(assetId);
    }

    @Test
    void shouldStoreCurveQuotes() {
        var assetId = assets.save(new Asset(null, "CDB9268A5NL", "CDB Banco (CDB9268A5NL)", AssetClass.FIXED_INCOME,
                null, Currency.BRL, true)).id();
        var quote = new Quote(assetId, LocalDate.of(2026, 9, 16), Money.brl("0.01000551"), QuoteSource.CURVE);

        quotes.upsert(List.of(quote));

        assertThat(quotes.findByAsset(assetId)).containsExactly(quote);
    }
}
