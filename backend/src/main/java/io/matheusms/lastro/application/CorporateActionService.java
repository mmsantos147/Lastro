package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.CorporateAction;
import io.matheusms.lastro.domain.port.CorporateActionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Splits, reverse splits, bonus shares and mergers. They belong to the asset, not to a user, so they apply to everyone
 * holding it. The history check covers the user making the change (the MVP has a single user).
 */
@Service
public class CorporateActionService {

    private final CorporateActionRepository corporateActions;
    private final EntryValidator validator;

    CorporateActionService(CorporateActionRepository corporateActions, EntryValidator validator) {
        this.corporateActions = corporateActions;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<CorporateAction> list() {
        return corporateActions.findAll();
    }

    @Transactional
    public CorporateAction register(Long userId, CorporateAction action) {
        validator.requireAsset(action.assetId());
        if (action.isMerger()) {
            validator.requireAsset(action.successorAssetId());
        }
        boolean duplicated = corporateActions.findByAsset(action.assetId()).stream()
                .anyMatch(other -> other.type() == action.type() && other.recordDate().equals(action.recordDate()));
        if (duplicated) {
            throw new ConflictException("A %s on %s is already recorded for this asset"
                    .formatted(action.type(), action.recordDate()));
        }
        validator.requireConsistentPortfolio(userId, portfolio -> portfolio.withHistory(action.assetId(),
                history -> {
                    var updated = new ArrayList<>(history.corporateActions());
                    updated.add(action);
                    return new AssetHistory(history.assetId(), history.trades(), history.transfers(), updated);
                }));
        return corporateActions.save(action);
    }

    @Transactional
    public void delete(Long userId, Long actionId) {
        var action = corporateActions.findById(actionId)
                .orElseThrow(() -> new NotFoundException("Corporate action", actionId));
        validator.requireConsistentPortfolio(userId, portfolio -> portfolio.withHistory(action.assetId(),
                history -> new AssetHistory(
                        history.assetId(),
                        history.trades(),
                        history.transfers(),
                        history.corporateActions().stream().filter(other -> !actionId.equals(other.id())).toList())));
        corporateActions.deleteById(actionId);
    }
}
