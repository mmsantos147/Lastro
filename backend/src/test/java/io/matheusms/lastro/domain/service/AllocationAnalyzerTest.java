package io.matheusms.lastro.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.TargetAllocation;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Bucket;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Status;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllocationAnalyzerTest {

    private final AllocationAnalyzer analyzer = new AllocationAnalyzer();

    @Test
    void shouldCompareEachBucketWithItsToleranceBand() {
        var buckets = analyzer.analyze(
                List.of(new Holding(1L, AssetClass.STOCK, Money.brl("560")),
                        new Holding(2L, AssetClass.FII, Money.brl("340")),
                        new Holding(3L, AssetClass.ETF, Money.brl("100"))),
                List.of(TargetAllocation.forClass(AssetClass.STOCK, "50", "5"),
                        TargetAllocation.forClass(AssetClass.FII, "40", "5"),
                        TargetAllocation.forClass(AssetClass.ETF, "10", "5")));

        assertThat(buckets).extracting(Bucket::assetClass, Bucket::status).containsExactly(
                tuple(AssetClass.STOCK, Status.ABOVE),
                tuple(AssetClass.FII, Status.BELOW),
                tuple(AssetClass.ETF, Status.WITHIN));
        assertThat(buckets.getFirst().deviation()).isEqualByComparingTo("6");
    }

    @Test
    void shouldListHeldClassesWithoutTarget() {
        var buckets = analyzer.analyze(
                List.of(new Holding(1L, AssetClass.STOCK, Money.brl("50")),
                        new Holding(2L, AssetClass.CRYPTO, Money.brl("50"))),
                List.of(TargetAllocation.forClass(AssetClass.STOCK, "100", "5")));

        assertThat(buckets).extracting(Bucket::assetClass, Bucket::status, Bucket::currentPercentage)
                .containsExactly(
                        tuple(AssetClass.STOCK, Status.BELOW, new java.math.BigDecimal("50.0000")),
                        tuple(AssetClass.CRYPTO, Status.NO_TARGET, new java.math.BigDecimal("50.0000")));
    }
}
