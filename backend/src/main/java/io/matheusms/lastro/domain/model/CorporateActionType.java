package io.matheusms.lastro.domain.model;

/**
 * Events that change a position without a trade. See {@link CorporateAction} for the meaning of the factor
 * in each type.
 */
public enum CorporateActionType {
    STOCK_SPLIT,
    REVERSE_SPLIT,
    BONUS_SHARES,
    /** Return of part of the invested capital; reduces cost without changing quantity. Not supported yet. */
    AMORTIZATION,
    /**
     * Conversion of shares into shares of another asset ("incorporação", ticker change): the position leaves the
     * asset and its total cost moves to the successor.
     */
    MERGER
}
