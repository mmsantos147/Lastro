package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.port.AssetRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalog of tradable assets. Shared by all users. */
@Service
public class AssetService {

    private final AssetRepository assets;

    AssetService(AssetRepository assets) {
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public List<Asset> list() {
        return assets.findAll();
    }

    @Transactional(readOnly = true)
    public Asset get(Long id) {
        return assets.findById(id).orElseThrow(() -> new NotFoundException("Asset", id));
    }

    @Transactional
    public Asset create(Asset asset) {
        requireUniqueCode(asset);
        return assets.save(asset);
    }

    /** Replaces the asset's attributes; trades keep pointing to it. */
    @Transactional
    public Asset update(Long id, Asset changes) {
        get(id);
        var updated = new Asset(id, changes.code(), changes.name(), changes.assetClass(), changes.cnpj(),
                changes.quoteCurrency(), changes.active());
        requireUniqueCode(updated);
        return assets.save(updated);
    }

    private void requireUniqueCode(Asset asset) {
        assets.findByCode(asset.code())
                .filter(existing -> !existing.id().equals(asset.id()))
                .ifPresent(existing -> {
                    throw new ConflictException("An asset with code %s already exists".formatted(asset.code()));
                });
    }
}
