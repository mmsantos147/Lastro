package io.matheusms.lastro.adapter.in.rest;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rounding of ratios (returns, weights, indices) in responses: 8 decimal places are plenty for display. */
public final class Ratios {

    private static final int SCALE = 8;

    private Ratios() {
    }

    /** @return {@code null} for {@code null} */
    public static BigDecimal rounded(BigDecimal ratio) {
        return ratio == null ? null : ratio.setScale(SCALE, RoundingMode.HALF_EVEN);
    }
}
