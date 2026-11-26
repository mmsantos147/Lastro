package io.matheusms.lastro.application;

import io.matheusms.lastro.domain.model.Analysis;
import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.port.AnalysisRepository;
import io.matheusms.lastro.domain.port.ContributionSuggestionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Analyses written by Claude and saved through MCP, shown on the Analyses screen. */
@Service
public class AnalysisService {

    /** A suggestion older than this is probably not the one the conversation computed. */
    static final Duration SUGGESTION_MAX_AGE = Duration.ofHours(24);

    private final AnalysisRepository analyses;
    private final ContributionSuggestionRepository suggestions;
    private final Clock clock;

    AnalysisService(AnalysisRepository analyses, ContributionSuggestionRepository suggestions, Clock clock) {
        this.analyses = analyses;
        this.suggestions = suggestions;
        this.clock = clock;
    }

    /**
     * Saves the analysis. A suggestion that does not exist, belongs to someone else or is older than 24 hours is
     * not linked; the analysis is saved anyway and the result carries a warning.
     *
     * @throws IllegalArgumentException if title or text are missing or too long
     */
    @Transactional
    public SavedAnalysis save(Long userId, String title, String text, Money amount, Long suggestionId) {
        var now = Instant.now(clock);
        String warning = null;
        Long linked = null;
        if (suggestionId != null) {
            var suggestion = suggestions.findById(suggestionId).filter(found -> found.userId().equals(userId));
            if (suggestion.isEmpty()) {
                warning = "Suggestion %d not found: the analysis was saved without it".formatted(suggestionId);
            } else if (suggestion.get().createdAt().isBefore(now.minus(SUGGESTION_MAX_AGE))) {
                warning = "Suggestion %d is older than 24 hours: the analysis was saved without it".formatted(suggestionId);
            } else {
                linked = suggestionId;
            }
        }
        var saved = analyses.save(new Analysis(null, userId, now, title, amount, text, linked));
        return new SavedAnalysis(saved, Optional.ofNullable(warning));
    }

    @Transactional(readOnly = true)
    public List<Analysis> list(Long userId) {
        return analyses.findByUser(userId);
    }

    /** The analysis with its linked suggestion, if any. */
    @Transactional(readOnly = true)
    public AnalysisDetail get(Long userId, Long analysisId) {
        var analysis = owned(userId, analysisId);
        var suggestion = Optional.ofNullable(analysis.contributionSuggestionId()).flatMap(suggestions::findById);
        return new AnalysisDetail(analysis, suggestion);
    }

    @Transactional
    public void delete(Long userId, Long analysisId) {
        owned(userId, analysisId);
        analyses.deleteById(analysisId);
    }

    private Analysis owned(Long userId, Long analysisId) {
        return analyses.findById(analysisId)
                .filter(analysis -> analysis.userId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Analysis", analysisId));
    }

    public record SavedAnalysis(Analysis analysis, Optional<String> warning) {
    }

    public record AnalysisDetail(Analysis analysis, Optional<ContributionSuggestion> suggestion) {
    }
}
