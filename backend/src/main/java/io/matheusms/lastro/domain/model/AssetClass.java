package io.matheusms.lastro.domain.model;

public enum AssetClass {
    STOCK,
    /** Fundo de Investimento Imobiliário: Brazilian listed real estate fund. */
    FII,
    ETF,
    /** Tesouro Direto: Brazilian government bonds sold to individuals. */
    TREASURY,
    CRYPTO,
    /**
     * Private fixed income (CDB, LCI, LCA): not traded on an exchange, valued on the curve agreed at purchase (see
     * {@link FixedIncomeTerms}).
     */
    FIXED_INCOME
}
