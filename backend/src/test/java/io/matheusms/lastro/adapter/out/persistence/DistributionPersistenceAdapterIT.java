package io.matheusms.lastro.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.matheusms.lastro.domain.model.Distribution;
import io.matheusms.lastro.domain.model.DistributionType;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.Quantity;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@PersistenceAdapterTest
class DistributionPersistenceAdapterIT {

    @Autowired
    private DistributionPersistenceAdapter distributions;
    @Autowired
    private UserPersistenceAdapter users;
    @Autowired
    private InstitutionPersistenceAdapter institutions;
    @Autowired
    private AssetPersistenceAdapter assets;

    private Long userId;
    private Long institutionId;
    private Long assetId;

    @BeforeEach
    void setUp() {
        userId = users.create(TestData.user(), TestData.PASSWORD_HASH).id();
        institutionId = institutions.save(TestData.broker()).id();
        assetId = assets.save(TestData.stock("BBAS3")).id();
    }

    @Test
    void shouldReturnDistributionsPaidWithinPeriodInclusive() {
        var beforePeriod = distributions.save(jcp(LocalDate.of(2026, 2, 28)));
        var firstDay = distributions.save(jcp(LocalDate.of(2026, 3, 1)));
        var lastDay = distributions.save(jcp(LocalDate.of(2026, 3, 31)));
        var afterPeriod = distributions.save(jcp(LocalDate.of(2026, 4, 1)));

        var result = distributions.findByUserAndPaymentDateBetween(
                userId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(result).containsExactly(firstDay, lastDay).doesNotContain(beforePeriod, afterPeriod);
    }

    @Test
    void shouldRoundTripStakingRewardPaidInAsset() {
        var btcId = assets.save(TestData.crypto("BTC")).id();
        var reward = new Distribution(null, userId, btcId, institutionId, DistributionType.STAKING_REWARD,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), Quantity.of("0.5"),
                Money.brl("3.50"), Money.brl("0"), Money.brl("3.50"), btcId, Quantity.of("0.00001"));

        var saved = distributions.save(reward);

        assertThat(distributions.findByUserAndPaymentDateBetween(userId, LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31))).containsExactly(saved);
    }

    private Distribution jcp(LocalDate paymentDate) {
        return new Distribution(null, userId, assetId, institutionId, DistributionType.JCP,
                paymentDate.minusDays(20), paymentDate, Quantity.of(100),
                Money.brl("12.34"), Money.brl("1.85"), Money.brl("10.49"), null, null);
    }
}
