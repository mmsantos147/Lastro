package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/**
 * Root of the asset hierarchy. JOINED inheritance: subtypes with extra attributes (e.g. private fixed income
 * securities, phase 2 of the product) get their own table joined by id.
 */
@Entity
@Table(name = "asset")
@Inheritance(strategy = InheritanceType.JOINED)
class AssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Column(length = 14)
    private String cnpj;

    @Enumerated(EnumType.STRING)
    @Column(name = "quote_currency", nullable = false, length = 3)
    private Currency quoteCurrency;

    @Column(nullable = false)
    private boolean active;

    protected AssetEntity() {
    }

    static AssetEntity from(Asset asset) {
        var entity = new AssetEntity();
        entity.id = asset.id();
        entity.code = asset.code();
        entity.name = asset.name();
        entity.assetClass = asset.assetClass();
        entity.cnpj = asset.cnpj() == null ? null : asset.cnpj().value();
        entity.quoteCurrency = asset.quoteCurrency();
        entity.active = asset.active();
        return entity;
    }

    Asset toDomain() {
        return new Asset(id, code, name, assetClass, cnpj == null ? null : new Cnpj(cnpj), quoteCurrency, active);
    }
}
