package io.matheusms.lastro.adapter.in.rest.strategy;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.AssetService;
import io.matheusms.lastro.application.ContributionService;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionItem;
import io.matheusms.lastro.domain.model.SuggestionReason;
import io.matheusms.lastro.domain.model.SuggestionSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contribution-suggestions")
class ContributionSuggestionController {

    private final ContributionService contributions;
    private final AssetService assets;

    ContributionSuggestionController(ContributionService contributions, AssetService assets) {
        this.contributions = contributions;
        this.assets = assets;
    }

    /** Runs the rules engine; 422 with {@code code: NO_ACTIVE_STRATEGY} when there is no target allocation. */
    @PostMapping
    ResponseEntity<?> suggest(@CurrentUser Long userId, @Valid @RequestBody SuggestionRequest request) {
        var suggestion = contributions.suggest(userId, Money.brl(request.amount().toPlainString()), SuggestionSource.WEB);
        if (suggestion.isEmpty()) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                    "There is no active strategy, so there is no target allocation to rebalance towards");
            problem.setProperty("code", "NO_ACTIVE_STRATEGY");
            return ResponseEntity.unprocessableContent().body(problem);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(SuggestionResponse.from(suggestion.get(), assetsById()));
    }

    @GetMapping("/{id}")
    SuggestionResponse get(@CurrentUser Long userId, @PathVariable Long id) {
        return SuggestionResponse.from(contributions.get(userId, id), assetsById());
    }

    private Map<Long, Asset> assetsById() {
        return assets.list().stream().collect(Collectors.toMap(Asset::id, Function.identity()));
    }

    record SuggestionRequest(@NotNull @Positive BigDecimal amount) {
    }

    record SuggestionResponse(
            Long id, Instant createdAt, BigDecimal amount, Long strategyId, SuggestionSource source,
            List<ItemResponse> items) {

        static SuggestionResponse from(ContributionSuggestion suggestion, Map<Long, Asset> assetsById) {
            return new SuggestionResponse(suggestion.id(), suggestion.createdAt(),
                    suggestion.amount().roundedToCents().amount(), suggestion.strategyId(), suggestion.source(),
                    suggestion.items().stream().map(item -> ItemResponse.from(item, assetsById)).toList());
        }
    }

    /** {@code assetId}, {@code assetCode} and {@code assetName} are null when the investor picks the asset. */
    record ItemResponse(
            AssetClass assetClass, Long assetId, String assetCode, String assetName, BigDecimal suggestedAmount,
            SuggestionReason reason, BigDecimal currentPercentage, BigDecimal targetPercentage) {

        static ItemResponse from(SuggestionItem item, Map<Long, Asset> assetsById) {
            var asset = item.assetId() == null ? null : assetsById.get(item.assetId());
            return new ItemResponse(item.assetClass(), item.assetId(), asset == null ? null : asset.code(),
                    asset == null ? null : asset.name(), item.suggestedAmount().roundedToCents().amount(),
                    item.reason(), item.currentPercentage(), item.targetPercentage());
        }
    }
}
