package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@PersistenceAdapterTest
class InstitutionPersistenceAdapterIT {

    @Autowired
    private InstitutionPersistenceAdapter institutions;

    @Test
    void shouldFindInstitutionByCnpj() {
        var saved = institutions.save(TestData.broker());

        assertThat(institutions.findByCnpj(new Cnpj("11222333000181"))).contains(saved);
    }

    @Test
    void shouldRoundTripInstitutionsWithoutCnpj() {
        var wallet = institutions.save(TestData.wallet());
        var exchange = institutions.save(new Institution(null, "Binance", InstitutionType.EXCHANGE, null));

        assertThat(institutions.findById(wallet.id())).contains(wallet);
        assertThat(institutions.findById(exchange.id())).contains(exchange);
    }

    @Test
    void shouldRejectDuplicateCnpj() {
        institutions.save(TestData.broker());

        assertThatThrownBy(() -> institutions.save(
                new Institution(null, "Other", InstitutionType.BANK, TestData.BROKER_CNPJ)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
