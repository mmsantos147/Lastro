package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InstitutionTest {

    private static final Cnpj CNPJ = new Cnpj("11222333000181");

    @ParameterizedTest
    @EnumSource(value = InstitutionType.class, names = {"BROKER", "BANK"})
    void shouldRequireCnpjForBrokersAndBanks(InstitutionType type) {
        assertThatThrownBy(() -> new Institution(null, "Name", type, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptExchangeWithOrWithoutCnpj() {
        assertThat(new Institution(null, "Binance", InstitutionType.EXCHANGE, null).cnpj()).isNull();
        assertThat(new Institution(null, "Binance", InstitutionType.EXCHANGE, CNPJ).cnpj()).isEqualTo(CNPJ);
    }

    @Test
    void shouldRejectCnpjForWallet() {
        assertThatThrownBy(() -> new Institution(null, "Ledger", InstitutionType.WALLET, CNPJ))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
