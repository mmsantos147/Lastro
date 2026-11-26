package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.AssetHistory;
import io.matheusms.lastro.domain.model.Transfer;
import io.matheusms.lastro.domain.port.TransferRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Movements of an asset between the user's own institutions. */
@Service
public class TransferService {

    private final TransferRepository transfers;
    private final EntryValidator validator;

    TransferService(TransferRepository transfers, EntryValidator validator) {
        this.transfers = transfers;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Transfer> list(Long userId) {
        return transfers.findByUser(userId);
    }

    /** @throws io.matheusms.lastro.domain.service.InsufficientQuantityException if it exceeds the position */
    @Transactional
    public Transfer register(Transfer transfer) {
        validator.requireAsset(transfer.assetId());
        validator.requireInstitution(transfer.fromInstitutionId());
        validator.requireInstitution(transfer.toInstitutionId());
        validator.requireConsistentPortfolio(transfer.userId(), portfolio -> portfolio.withHistory(transfer.assetId(),
                history -> {
                    var updated = new ArrayList<>(history.transfers());
                    updated.add(transfer);
                    return new AssetHistory(history.assetId(), history.trades(), updated, history.corporateActions());
                }));
        return transfers.save(transfer);
    }

    @Transactional
    public void delete(Long userId, Long transferId) {
        var transfer = transfers.findById(transferId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Transfer", transferId));
        validator.requireConsistentPortfolio(userId, portfolio -> portfolio.withHistory(transfer.assetId(),
                history -> new AssetHistory(
                        history.assetId(),
                        history.trades(),
                        history.transfers().stream().filter(other -> !transferId.equals(other.id())).toList(),
                        history.corporateActions())));
        transfers.deleteById(transferId);
    }
}
