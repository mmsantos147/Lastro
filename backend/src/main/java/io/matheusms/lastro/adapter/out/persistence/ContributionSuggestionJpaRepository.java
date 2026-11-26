package io.matheusms.lastro.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ContributionSuggestionJpaRepository extends JpaRepository<ContributionSuggestionEntity, Long> {

    boolean existsByStrategyId(Long strategyId);
}
