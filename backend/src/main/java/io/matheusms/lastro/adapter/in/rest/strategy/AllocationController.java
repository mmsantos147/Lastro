package io.matheusms.lastro.adapter.in.rest.strategy;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.AllocationService;
import io.matheusms.lastro.application.AssetService;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Bucket;
import io.matheusms.lastro.domain.service.AllocationAnalyzer.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/allocation")
class AllocationController {

    private final AllocationService allocation;
    private final AssetService assets;

    AllocationController(AllocationService allocation, AssetService assets) {
        this.allocation = allocation;
        this.assets = assets;
    }

    /** Current allocation by bucket vs the active strategy; {@code strategy} is null when none is active. */
    @GetMapping
    AllocationResponse allocation(@CurrentUser Long userId) {
        var result = allocation.allocationVsTarget(userId);
        Map<Long, Asset> assetsById = assets.list().stream().collect(Collectors.toMap(Asset::id, Function.identity()));
        return new AllocationResponse(
                result.strategy() == null ? null : new StrategyRef(result.strategy().id(), result.strategy().name()),
                result.total().roundedToCents().amount(),
                result.buckets().stream().map(bucket -> BucketResponse.from(bucket, assetsById)).toList());
    }

    record AllocationResponse(StrategyRef strategy, BigDecimal total, List<BucketResponse> buckets) {
    }

    record StrategyRef(Long id, String name) {
    }

    /** Percentages in percent; deviation in percentage points (current - target). */
    record BucketResponse(
            AssetClass assetClass,
            Long assetId,
            String assetCode,
            BigDecimal value,
            BigDecimal currentPercentage,
            BigDecimal targetPercentage,
            BigDecimal deviation,
            BigDecimal minTolerance,
            BigDecimal maxTolerance,
            Status status) {

        static BucketResponse from(Bucket bucket, Map<Long, Asset> assetsById) {
            var target = bucket.target();
            return new BucketResponse(bucket.assetClass(), bucket.assetId(),
                    bucket.assetId() == null ? null : assetsById.get(bucket.assetId()).code(),
                    bucket.value().roundedToCents().amount(), bucket.currentPercentage(), bucket.targetPercentage(),
                    bucket.deviation(), target == null ? null : target.minTolerance(),
                    target == null ? null : target.maxTolerance(), bucket.status());
        }
    }
}
