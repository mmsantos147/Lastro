package io.matheusms.lastro.domain.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CorporateActionTest {

    private static final LocalDate RECORD_DATE = LocalDate.of(2026, 1, 5);

    @Test
    void shouldRequireAssignedCostForBonusShares() {
        assertThatThrownBy(() -> new CorporateAction(
                null, 1L, CorporateActionType.BONUS_SHARES, RECORD_DATE, new BigDecimal("0.1"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireAssignedCostInBrl() {
        assertThatThrownBy(() -> new CorporateAction(null, 1L, CorporateActionType.BONUS_SHARES, RECORD_DATE,
                new BigDecimal("0.1"), Money.of("5", Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveFactor() {
        assertThatThrownBy(() -> new CorporateAction(
                null, 1L, CorporateActionType.STOCK_SPLIT, RECORD_DATE, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
