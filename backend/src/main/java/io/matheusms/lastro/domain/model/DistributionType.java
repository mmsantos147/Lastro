package io.matheusms.lastro.domain.model;

public enum DistributionType {
    DIVIDEND,
    /** Juros sobre Capital Próprio: interest on equity, a Brazilian distribution with income tax withheld at source. */
    JCP,
    /** Monthly income paid by a FII (Brazilian real estate fund). */
    FII_INCOME,
    FIXED_INCOME_INTEREST,
    /** Crypto reward (e.g. Binance Earn) paid in units of an asset. */
    STAKING_REWARD
}
