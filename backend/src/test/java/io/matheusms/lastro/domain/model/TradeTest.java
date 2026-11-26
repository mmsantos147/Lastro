package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TradeTest {

    @Test
    void shouldRejectZeroQuantity() {
        assertThatThrownBy(() -> trade(Quantity.ZERO, Money.brl("10"), TradeFee.none(Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeUnitPrice() {
        assertThatThrownBy(() -> trade(Quantity.of(1), Money.brl("-10"), TradeFee.none(Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeFee() {
        assertThatThrownBy(() -> new TradeFee.InCurrency(Money.brl("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectFeeInCurrencyOtherThanPriceCurrency() {
        assertThatThrownBy(() -> trade(Quantity.of(1), Money.of("10", Currency.USD), TradeFee.none(Currency.BRL)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptFeePaidInAnotherAsset() {
        var bnbFee = new TradeFee.InAsset(99L, Quantity.of("0.0015"));

        var trade = trade(Quantity.of("0.01"), Money.of("65000", Currency.USD), bnbFee);

        assertThat(trade.fee()).isEqualTo(bnbFee);
        assertThat(trade.priceCurrency()).isEqualTo(Currency.USD);
    }

    private static Trade trade(Quantity quantity, Money unitPrice, TradeFee fee) {
        return new Trade(null, 1L, 1L, 1L, TradeType.BUY, LocalDate.of(2026, 1, 5),
                quantity, unitPrice, fee, EntrySource.MANUAL, null);
    }
}
