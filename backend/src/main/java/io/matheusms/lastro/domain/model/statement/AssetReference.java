package io.matheusms.lastro.domain.model.statement;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Currency;
import java.util.Objects;

/**
 * Asset as named in an imported file. The import looks it up by code and creates it with these attributes when it
 * does not exist yet.
 *
 * @param code ticker or symbol, upper case
 * @param assetClass best guess from the file (e.g. a FII by its name)
 */
public record AssetReference(String code, String name, AssetClass assetClass, Currency quoteCurrency) {

    public AssetReference {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(assetClass, "assetClass must not be null");
        Objects.requireNonNull(quoteCurrency, "quoteCurrency must not be null");
        if (code.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("Asset code and name must not be blank");
        }
        code = code.strip().toUpperCase();
        name = name.strip();
    }
}
