package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjTest {

    @Test
    void shouldNormalizeFormattedNumericCnpj() {
        assertThat(new Cnpj("11.222.333/0001-81").value()).isEqualTo("11222333000181");
    }

    @Test
    void shouldAcceptAlphanumericCnpj() {
        // Example published by Receita Federal for the alphanumeric format
        assertThat(new Cnpj("12.abc.345/01de-35").value()).isEqualTo("12ABC34501DE35");
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000182", "12ABC34501DE36"})
    void shouldRejectWrongCheckDigits(String value) {
        assertThatThrownBy(() -> new Cnpj(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1122233300018", "112223330001811", "11222333000A81", "11.222.333/0001-8X"})
    void shouldRejectInvalidFormat(String value) {
        assertThatThrownBy(() -> new Cnpj(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectRepeatedCharacters() {
        assertThatThrownBy(() -> new Cnpj("00000000000000")).isInstanceOf(IllegalArgumentException.class);
    }
}
