package io.matheusms.lastro.application;

import io.matheusms.lastro.application.PortfolioLoader.UserPortfolio;
import io.matheusms.lastro.domain.port.AssetRepository;
import io.matheusms.lastro.domain.port.InstitutionRepository;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Checks shared by every manual entry: referenced assets and institutions exist, and the user's portfolio stays
 * consistent after the change (e.g. no sell larger than the quantity held at that date). The whole portfolio is
 * replayed because a trade may affect another asset (a fee paid in BNB).
 */
@Component
class EntryValidator {

    private final AssetRepository assets;
    private final InstitutionRepository institutions;
    private final PortfolioLoader loader;
    private final PositionService positions;

    EntryValidator(AssetRepository assets, InstitutionRepository institutions, PortfolioLoader loader,
            PositionService positions) {
        this.assets = assets;
        this.institutions = institutions;
        this.loader = loader;
        this.positions = positions;
    }

    void requireAsset(Long assetId) {
        if (assets.findById(assetId).isEmpty()) {
            throw new NotFoundException("Asset", assetId);
        }
    }

    void requireInstitution(Long institutionId) {
        if (institutions.findById(institutionId).isEmpty()) {
            throw new NotFoundException("Institution", institutionId);
        }
    }

    /**
     * Replays the user's portfolio with the change applied.
     *
     * @throws io.matheusms.lastro.domain.service.InsufficientQuantityException if the change makes the history
     *     inconsistent
     */
    void requireConsistentPortfolio(Long userId, UnaryOperator<UserPortfolio> change) {
        positions.requireConsistent(change.apply(loader.load(userId)));
    }
}
