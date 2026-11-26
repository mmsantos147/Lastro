package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.Quote;
import io.matheusms.lastro.domain.port.QuoteRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QuotePersistenceAdapter implements QuoteRepository {

    private final QuoteJpaRepository repository;

    QuotePersistenceAdapter(QuoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void upsert(Collection<Quote> quotes) {
        quotes.forEach(quote -> repository.upsert(quote.assetId(), quote.date(), quote.closePrice().amount(),
                quote.closePrice().currency().name(), quote.source().name()));
    }

    @Override
    public Optional<LocalDate> findEarliestDate(Long assetId) {
        return repository.findEarliestDate(assetId);
    }

    @Override
    public List<Quote> findByAssetsUpTo(Collection<Long> assetIds, LocalDate to) {
        if (assetIds.isEmpty()) {
            return List.of();
        }
        return repository.findByAssetsUpTo(assetIds, to).stream().map(QuoteEntity::toDomain).toList();
    }

    @Override
    public Optional<LocalDate> findLatestDate(Long assetId) {
        return repository.findLatestDate(assetId);
    }

    @Override
    public List<Quote> findByAsset(Long assetId) {
        return repository.findByAssetIdOrderByDateAsc(assetId).stream().map(QuoteEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(Long assetId, LocalDate date) {
        repository.deleteByAssetIdAndDate(assetId, date);
    }
}
