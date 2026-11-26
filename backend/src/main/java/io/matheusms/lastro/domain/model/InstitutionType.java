package io.matheusms.lastro.domain.model;

public enum InstitutionType {
    BROKER,
    BANK,
    /** Crypto exchange (e.g. Binance). */
    EXCHANGE,
    /** Self-custody crypto wallet owned by the user. Has no CNPJ. */
    WALLET
}
