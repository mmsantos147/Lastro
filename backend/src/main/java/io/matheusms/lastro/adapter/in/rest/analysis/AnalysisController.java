package io.matheusms.lastro.adapter.in.rest.analysis;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.AnalysisService;
import io.matheusms.lastro.application.AssetService;
import io.matheusms.lastro.domain.model.Analysis;
import io.matheusms.lastro.domain.model.Asset;
import io.matheusms.lastro.domain.model.AssetClass;
import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.SuggestionReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Analyses saved by Claude (created only through MCP; the website lists, shows and deletes them). */
@RestController
@RequestMapping("/api/analyses")
class AnalysisController {

    private final AnalysisService analyses;
    private final AssetService assets;

    AnalysisController(AnalysisService analyses, AssetService assets) {
        this.analyses = analyses;
        this.assets = assets;
    }

    @GetMapping
    List<AnalysisSummary> list(@CurrentUser Long userId) {
        return analyses.list(userId).stream().map(AnalysisSummary::from).toList();
    }

    /** The text is untrusted markdown written by the LLM: the frontend renders it without raw HTML. */
    @GetMapping("/{id}")
    AnalysisResponse get(@CurrentUser Long userId, @PathVariable Long id) {
        var detail = analyses.get(userId, id);
        Map<Long, Asset> assetsById = assets.list().stream().collect(Collectors.toMap(Asset::id, Function.identity()));
        return AnalysisResponse.from(detail.analysis(),
                detail.suggestion().map(suggestion -> SuggestionResponse.from(suggestion, assetsById)).orElse(null));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        analyses.delete(userId, id);
    }

    record AnalysisSummary(Long id, Instant createdAt, String title, BigDecimal amount, boolean hasSuggestion) {

        static AnalysisSummary from(Analysis analysis) {
            return new AnalysisSummary(analysis.id(), analysis.createdAt(), analysis.title(),
                    analysis.amount() == null ? null : analysis.amount().roundedToCents().amount(),
                    analysis.contributionSuggestionId() != null);
        }
    }

    record AnalysisResponse(
            Long id, Instant createdAt, String title, BigDecimal amount, String text, SuggestionResponse suggestion) {

        static AnalysisResponse from(Analysis analysis, SuggestionResponse suggestion) {
            return new AnalysisResponse(analysis.id(), analysis.createdAt(), analysis.title(),
                    analysis.amount() == null ? null : analysis.amount().roundedToCents().amount(), analysis.text(),
                    suggestion);
        }
    }

    /** The table shown with the analysis: always from the persisted suggestion, never from the LLM text. */
    record SuggestionResponse(Long id, Instant createdAt, BigDecimal amount, List<ItemResponse> items) {

        static SuggestionResponse from(ContributionSuggestion suggestion, Map<Long, Asset> assetsById) {
            return new SuggestionResponse(suggestion.id(), suggestion.createdAt(),
                    suggestion.amount().roundedToCents().amount(),
                    suggestion.items().stream().map(item -> {
                        var asset = item.assetId() == null ? null : assetsById.get(item.assetId());
                        return new ItemResponse(item.assetClass(), item.assetId(),
                                asset == null ? null : asset.code(), asset == null ? null : asset.name(),
                                item.suggestedAmount().roundedToCents().amount(), item.reason(),
                                item.currentPercentage(), item.targetPercentage());
                    }).toList());
        }
    }

    record ItemResponse(
            AssetClass assetClass, Long assetId, String assetCode, String assetName, BigDecimal suggestedAmount,
            SuggestionReason reason, BigDecimal currentPercentage, BigDecimal targetPercentage) {
    }
}
