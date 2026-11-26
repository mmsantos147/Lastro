package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.util.Objects;

/**
 * Tradable asset.
 *
 * @param id {@code null} until persisted
 * @param code ticker (e.g. {@code PETR4}), ISIN or crypto symbol (e.g. {@code BTC}); stored in upper case
 * @param cnpj issuer or fund CNPJ; {@code null} when not applicable (e.g. Tesouro Direto, crypto)
 * @param quoteCurrency currency in which the asset's market price is quoted
 */
public record Asset(
        Long id, String code, String name, AssetClass assetClass, Cnpj cnpj, Currency quoteCurrency, boolean active) {

    public Asset {
        requireNotBlank(code, "code");
        requireNotBlank(name, "name");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        code = code.strip().toUpperCase();
    }
}
