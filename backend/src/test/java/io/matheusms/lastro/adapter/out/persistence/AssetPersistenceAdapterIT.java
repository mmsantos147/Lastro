package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class AssetPersistenceAdapterIT {

    @Autowired
    private AssetPersistenceAdapter assets;

    @Test
    void shouldRoundTripAllFields() {
        var fii = new Asset(null, "HGLG11", "CSHG Logística", AssetClass.FII, new Cnpj("11.728.688/0001-47"),
                Currency.BRL, true);

        var saved = assets.save(fii);

        assertThat(assets.findById(saved.id())).contains(saved);
        assertThat(saved.cnpj()).isEqualTo(fii.cnpj());
    }

    @Test
    void shouldRoundTripCryptoQuotedInUsd() {
        var saved = assets.save(TestData.crypto("BTC"));

        assertThat(assets.findByCode("btc")).contains(saved);
        assertThat(saved.quoteCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldFindByCodeIgnoringCase() {
        var saved = assets.save(TestData.stock("PETR4"));

        assertThat(assets.findByCode(" petr4 ")).contains(saved);
    }

    @Test
    void shouldRejectDuplicateCode() {
        assets.save(TestData.stock("PETR4"));

        assertThatThrownBy(() -> assets.save(TestData.stock("petr4")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
