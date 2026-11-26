package io.matheusms.lastro.domain.model;

/** Economic indicators used as benchmarks and for currency conversion. The unit of the value depends on the type. */
public enum IndicatorType {
    /** Certificado de Depósito Interbancário: Brazilian interbank rate, the usual fixed income benchmark. % per day. */
    CDI,
    /** Brazilian central bank base rate. % per day. */
    SELIC,
    /** Índice Nacional de Preços ao Consumidor Amplo: official Brazilian inflation index. % per month. */
    IPCA,
    /** Ibovespa: main index of the B3 stock exchange. Points at the close. */
    IBOV,
    /** BRL per USD (PTAX selling rate, published by the central bank). */
    USD_BRL
}
