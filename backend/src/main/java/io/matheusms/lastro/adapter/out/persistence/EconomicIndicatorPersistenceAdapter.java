package io.matheusms.lastro.adapter.out.persistence;

import io.matheusms.lastro.domain.model.EconomicIndicator;
import io.matheusms.lastro.domain.model.IndicatorType;
import io.matheusms.lastro.domain.port.EconomicIndicatorRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EconomicIndicatorPersistenceAdapter implements EconomicIndicatorRepository {

    private final EconomicIndicatorJpaRepository repository;

    EconomicIndicatorPersistenceAdapter(EconomicIndicatorJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void upsert(Collection<EconomicIndicator> indicators) {
        indicators.forEach(indicator ->
                repository.upsert(indicator.type().name(), indicator.date(), indicator.value()));
    }

    @Override
    public List<EconomicIndicator> findByTypeBetween(IndicatorType type, LocalDate from, LocalDate to) {
        return repository.findByTypeAndDateBetweenOrderByDateAsc(type, from, to).stream()
                .map(EconomicIndicatorEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<LocalDate> findEarliestDate(IndicatorType type) {
        return repository.findEarliestDate(type);
    }

    @Override
    public Optional<LocalDate> findLatestDate(IndicatorType type) {
        return repository.findLatestDate(type);
    }
}
