package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Trade;
import io.matheusms.lastro.domain.port.TradeRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TradePersistenceAdapter implements TradeRepository {

    private final TradeJpaRepository repository;

    TradePersistenceAdapter(TradeJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Trade save(Trade trade) {
        return repository.save(TradeEntity.from(trade)).toDomain();
    }

    @Override
    public List<Trade> findByUserAndAsset(Long userId, Long assetId) {
        return repository.findByUserIdAndAssetIdOrderByDateAscIdAsc(userId, assetId).stream()
                .map(TradeEntity::toDomain)
                .toList();
    }

    @Override
    public List<Trade> findByUser(Long userId) {
        return repository.findByUserIdOrderByDateAscIdAsc(userId).stream().map(TradeEntity::toDomain).toList();
    }

    @Override
    public Optional<Trade> findById(Long id) {
        return repository.findById(id).map(TradeEntity::toDomain);
    }

    @Override
    public Set<Long> findTradedAssetIds(Long userId) {
        return repository.findDistinctAssetIdsByUserId(userId);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public Optional<LocalDate> findFirstTradeDate(Long assetId) {
        return repository.findFirstDateByAssetId(assetId);
    }

    @Override
    public Optional<Trade> findFirstTrade(Long assetId) {
        return repository.findFirstByAssetIdOrderByDateAscIdAsc(assetId).map(TradeEntity::toDomain);
    }

    @Override
    public Optional<LocalDate> findFirstTradeDate() {
        return repository.findFirstDate();
    }
}
