package io.matheusms.lastro.domain.model;

/** What the yield of a fixed income security follows. */
public enum FixedIncomeIndexer {
    /** A percentage of the CDI (interbank deposit rate), optionally plus a spread: "110% do CDI", "CDI + 1%". */
    CDI,
    /** A percentage of the Selic (central bank base rate), optionally plus a spread. */
    SELIC,
    /** IPCA (official inflation index) plus a real rate: "IPCA + 6%". */
    IPCA,
    /** A fixed annual rate known at purchase ("prefixado"). */
    PREFIXED
}
