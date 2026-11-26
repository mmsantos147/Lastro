package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void shouldBeEqualRegardlessOfInputScale() {
        assertThat(Money.brl("10")).isEqualTo(Money.brl("10.00000"));
        assertThat(Money.brl("10").hashCode()).isEqualTo(Money.brl("10.00").hashCode());
    }

    @Test
    void shouldNotBeEqualInDifferentCurrencies() {
        assertThat(Money.brl("10")).isNotEqualTo(Money.of("10", Currency.USD));
    }

    @Test
    void shouldRoundToEightDecimalPlacesHalfEven() {
        assertThat(Money.brl("0.123456785").amount()).isEqualByComparingTo("0.12345678");
        assertThat(Money.brl("0.123456775").amount()).isEqualByComparingTo("0.12345678");
    }

    @Test
    void shouldKeepPrecisionWhenDividing() {
        assertThat(Money.brl("31").dividedBy(Quantity.of(3))).isEqualTo(Money.brl("10.33333333"));
    }

    @Test
    void shouldRoundToCentsHalfEven() {
        assertThat(Money.brl("10.125").roundedToCents()).isEqualTo(Money.brl("10.12"));
        assertThat(Money.brl("10.135").roundedToCents()).isEqualTo(Money.brl("10.14"));
    }

    @Test
    void shouldAddSubtractAndMultiplyKeepingCurrency() {
        var usd = Money.of("10.50", Currency.USD);

        assertThat(usd.plus(Money.of("0.25", Currency.USD))).isEqualTo(Money.of("10.75", Currency.USD));
        assertThat(usd.minus(Money.of("11", Currency.USD))).isEqualTo(Money.of("-0.50", Currency.USD));
        assertThat(usd.times(Quantity.of("2.5"))).isEqualTo(Money.of("26.25", Currency.USD));
        assertThat(usd.times(new BigDecimal("3"))).isEqualTo(Money.of("31.50", Currency.USD));
    }

    @Test
    void shouldRejectArithmeticBetweenDifferentCurrencies() {
        var usd = Money.of("1", Currency.USD);

        assertThatThrownBy(() -> Money.brl("1").plus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.brl("1").minus(usd)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.brl("1").compareTo(usd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDivisionByZero() {
        assertThatThrownBy(() -> Money.brl("10").dividedBy(Quantity.ZERO))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void shouldReportSignAndCurrency() {
        assertThat(Money.zero(Currency.BRL).isZero()).isTrue();
        assertThat(Money.brl("-0.01").isNegative()).isTrue();
        assertThat(Money.brl("0.01").isNegative()).isFalse();
        assertThat(Money.brl("1").isIn(Currency.BRL)).isTrue();
    }
}
