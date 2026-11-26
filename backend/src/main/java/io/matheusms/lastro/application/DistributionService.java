package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.port.DistributionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Income received from assets ("proventos"). */
@Service
public class DistributionService {

    private final DistributionRepository distributions;
    private final EntryValidator validator;

    DistributionService(DistributionRepository distributions, EntryValidator validator) {
        this.distributions = distributions;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public List<Distribution> list(Long userId) {
        return distributions.findByUser(userId);
    }

    @Transactional
    public Distribution register(Distribution distribution) {
        validator.requireAsset(distribution.assetId());
        validator.requireInstitution(distribution.institutionId());
        if (distribution.paidInAssetId() != null) {
            validator.requireAsset(distribution.paidInAssetId());
        }
        return distributions.save(distribution);
    }

    @Transactional
    public void delete(Long userId, Long distributionId) {
        var distribution = distributions.findById(distributionId)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Distribution", distributionId));
        if (distribution.paidInAssetId() != null) {
            // A reward paid in an asset added quantity that later sells may depend on
            validator.requireConsistentPortfolio(userId, portfolio -> portfolio.withDistributions(
                    portfolio.distributions().stream().filter(other -> !distributionId.equals(other.id())).toList()));
        }
        distributions.deleteById(distributionId);
    }
}
