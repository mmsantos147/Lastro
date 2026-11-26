package io.matheusms.lastro.adapter.in.rest.catalog;

import io.matheusms.lastro.application.AssetService;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
class AssetController {

    private final AssetService assets;

    AssetController(AssetService assets) {
        this.assets = assets;
    }

    @GetMapping
    List<AssetResponse> list() {
        return assets.list().stream().map(AssetResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssetResponse create(@Valid @RequestBody AssetRequest request) {
        return AssetResponse.from(assets.create(request.toDomain(null)));
    }

    @PutMapping("/{id}")
    AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return AssetResponse.from(assets.update(id, request.toDomain(id)));
    }

    record AssetRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull AssetClass assetClass,
            String cnpj,
            @NotNull Currency quoteCurrency,
            Boolean active) {

        Asset toDomain(Long id) {
            var parsedCnpj = cnpj == null || cnpj.isBlank() ? null : new Cnpj(cnpj);
            return new Asset(id, code, name, assetClass, parsedCnpj, quoteCurrency, active == null || active);
        }
    }

    record AssetResponse(
            Long id, String code, String name, AssetClass assetClass, String cnpj, Currency quoteCurrency,
            boolean active) {

        static AssetResponse from(Asset asset) {
            return new AssetResponse(asset.id(), asset.code(), asset.name(), asset.assetClass(),
                    asset.cnpj() == null ? null : asset.cnpj().value(), asset.quoteCurrency(), asset.active());
        }
    }
}
