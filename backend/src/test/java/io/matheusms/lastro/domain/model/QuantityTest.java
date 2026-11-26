package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QuantityTest {

    @Test
    void shouldBeEqualRegardlessOfInputScale() {
        assertThat(Quantity.of("100")).isEqualTo(Quantity.of(100));
        assertThat(Quantity.of("0.50")).isEqualTo(Quantity.of("0.5"));
    }

    @Test
    void shouldRejectNegativeValues() {
        assertThatThrownBy(() -> Quantity.of("-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectSubtractionBelowZero() {
        assertThatThrownBy(() -> Quantity.of(10).minus(Quantity.of(11)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSupportFractionalArithmetic() {
        assertThat(Quantity.of("0.53").minus(Quantity.of("0.21"))).isEqualTo(Quantity.of("0.32"));
        assertThat(Quantity.of(15).times(new BigDecimal("0.1"))).isEqualTo(Quantity.of("1.5"));
        assertThat(Quantity.of(105).dividedBy(BigDecimal.TEN)).isEqualTo(Quantity.of("10.5"));
    }

    @Test
    void shouldDropFractionalPartWhenTruncating() {
        assertThat(Quantity.of("10.99").truncated()).isEqualTo(Quantity.of(10));
        assertThat(Quantity.of("0.5").truncated()).isEqualTo(Quantity.ZERO);
    }

    @Test
    void shouldCompareValues() {
        assertThat(Quantity.of(11).isGreaterThan(Quantity.of(10))).isTrue();
        assertThat(Quantity.of(10).isGreaterThan(Quantity.of("10.0"))).isFalse();
        assertThat(Quantity.ZERO.isZero()).isTrue();
    }
}
