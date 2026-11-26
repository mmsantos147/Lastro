package io.matheusms.lastro.domain.model;

/**
 * Currencies in which prices and amounts are expressed. Positions are always valued in {@link #BRL}.
 * Stablecoin-quoted pairs (USDT) are recorded as {@link #USD}.
 */
public enum Currency {
    BRL,
    USD
}
