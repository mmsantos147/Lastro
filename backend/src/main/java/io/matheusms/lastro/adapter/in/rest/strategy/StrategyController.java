package io.matheusms.lastro.adapter.in.rest.strategy;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.StrategyService;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.Strategy;
import io.matheusms.lastro.domain.model.TargetAllocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
class StrategyController {

    private final StrategyService strategies;

    StrategyController(StrategyService strategies) {
        this.strategies = strategies;
    }

    @GetMapping
    List<StrategyResponse> list(@CurrentUser Long userId) {
        return strategies.list(userId).stream().map(StrategyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    StrategyResponse create(@CurrentUser Long userId, @Valid @RequestBody StrategyRequest request) {
        return StrategyResponse.from(strategies.create(userId, request.name(), Boolean.TRUE.equals(request.active()),
                request.toTargets()));
    }

    @PutMapping("/{id}")
    StrategyResponse update(@CurrentUser Long userId, @PathVariable Long id, @Valid @RequestBody StrategyRequest request) {
        return StrategyResponse.from(strategies.update(userId, id, request.name(), request.toTargets()));
    }

    @PostMapping("/{id}/activate")
    StrategyResponse activate(@CurrentUser Long userId, @PathVariable Long id) {
        return StrategyResponse.from(strategies.activate(userId, id));
    }

    @PostMapping("/{id}/deactivate")
    StrategyResponse deactivate(@CurrentUser Long userId, @PathVariable Long id) {
        return StrategyResponse.from(strategies.deactivate(userId, id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        strategies.delete(userId, id);
    }

    record StrategyRequest(@NotBlank String name, Boolean active, @NotEmpty List<@Valid TargetRequest> targets) {

        List<TargetAllocation> toTargets() {
            return targets.stream().map(TargetRequest::toDomain).toList();
        }
    }

    /** Percentages in percent (25 = 25%); tolerances in percentage points, zero when omitted. */
    record TargetRequest(
            AssetClass assetClass,
            Long assetId,
            @NotNull BigDecimal targetPercentage,
            BigDecimal minTolerance,
            BigDecimal maxTolerance,
            BigDecimal maxPerAsset) {

        TargetAllocation toDomain() {
            return new TargetAllocation(assetClass, assetId, targetPercentage,
                    minTolerance == null ? BigDecimal.ZERO : minTolerance,
                    maxTolerance == null ? BigDecimal.ZERO : maxTolerance, maxPerAsset);
        }
    }

    record StrategyResponse(Long id, String name, boolean active, List<TargetResponse> targets) {

        static StrategyResponse from(Strategy strategy) {
            return new StrategyResponse(strategy.id(), strategy.name(), strategy.active(),
                    strategy.targets().stream().map(TargetResponse::from).toList());
        }
    }

    record TargetResponse(
            AssetClass assetClass, Long assetId, BigDecimal targetPercentage, BigDecimal minTolerance,
            BigDecimal maxTolerance, BigDecimal maxPerAsset) {

        static TargetResponse from(TargetAllocation target) {
            return new TargetResponse(target.assetClass(), target.assetId(), target.targetPercentage(),
                    target.minTolerance(), target.maxTolerance(), target.maxPerAsset());
        }
    }
}
