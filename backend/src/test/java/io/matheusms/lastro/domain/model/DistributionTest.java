package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DistributionTest {

    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 3, 10);
    private static final LocalDate PAYMENT_DATE = LocalDate.of(2026, 3, 31);

    @Test
    void shouldAcceptNetAmountEqualToGrossMinusWithheldTax() {
        var jcp = jcp(PAYMENT_DATE, "100.00", "15.00", "85.00");

        assertThat(jcp.netAmount()).isEqualTo(Money.brl("85"));
    }

    @Test
    void shouldRejectInconsistentNetAmount() {
        assertThatThrownBy(() -> jcp(PAYMENT_DATE, "100.00", "15.00", "86.00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPaymentBeforeRecordDate() {
        assertThatThrownBy(() -> jcp(RECORD_DATE.minusDays(1), "100.00", "0", "100.00"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequirePaidInAssetForStakingRewards() {
        assertThatThrownBy(() -> stakingReward(null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(stakingReward(7L, Quantity.of("0.0001")).paidQuantity()).isEqualTo(Quantity.of("0.0001"));
    }

    @Test
    void shouldRequirePaidInAssetAndQuantityTogether() {
        assertThatThrownBy(() -> stakingReward(7L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Distribution jcp(LocalDate paymentDate, String gross, String tax, String net) {
        return new Distribution(null, 1L, 1L, 1L, DistributionType.JCP, RECORD_DATE, paymentDate,
                Quantity.of(100), Money.brl(gross), Money.brl(tax), Money.brl(net), null, null);
    }

    private static Distribution stakingReward(Long paidInAssetId, Quantity paidQuantity) {
        return new Distribution(null, 1L, 7L, 2L, DistributionType.STAKING_REWARD, PAYMENT_DATE, PAYMENT_DATE,
                Quantity.of("0.5"), Money.brl("35.00"), Money.brl("0"), Money.brl("35.00"),
                paidInAssetId, paidQuantity);
    }
}
