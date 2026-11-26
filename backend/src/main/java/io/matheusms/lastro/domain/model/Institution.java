package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.util.Objects;

/**
 * Place where assets are held: broker, bank, crypto exchange or the user's own wallet.
 *
 * @param id {@code null} until persisted
 * @param cnpj required for brokers and banks; optional for exchanges; {@code null} for wallets
 */
public record Institution(Long id, String name, InstitutionType type, Cnpj cnpj) {

    public Institution {
        requireNotBlank(name, "name");
        Objects.requireNonNull(type, "type must not be null");
        if ((type == InstitutionType.BROKER || type == InstitutionType.BANK) && cnpj == null) {
            throw new IllegalArgumentException(type + " requires a CNPJ");
        }
        if (type == InstitutionType.WALLET && cnpj != null) {
            throw new IllegalArgumentException("A wallet has no CNPJ");
        }
    }
}
