package io.matheusms.lastro.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import io.matheusms.lastro.domain.model.Analysis;
import io.matheusms.lastro.domain.model.ContributionSuggestion;
import io.matheusms.lastro.domain.model.Money;
import io.matheusms.lastro.domain.model.SuggestionSource;
import io.matheusms.lastro.domain.port.AnalysisRepository;
import io.matheusms.lastro.domain.port.ContributionSuggestionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final Long USER_ID = 1L;

    @Mock
    private AnalysisRepository analyses;
    @Mock
    private ContributionSuggestionRepository suggestions;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(analyses, suggestions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldLinkARecentSuggestionOfTheUser() {
        savesWithId();
        given(suggestions.findById(7L)).willReturn(Optional.of(suggestion(USER_ID, NOW.minus(Duration.ofHours(23)))));

        var saved = service.save(USER_ID, "Aporte", "Texto", Money.brl("1000"), 7L);

        assertThat(saved.analysis().contributionSuggestionId()).isEqualTo(7L);
        assertThat(saved.warning()).isEmpty();
    }

    @Test
    void shouldSaveWithoutLinkWhenTheSuggestionIsOlderThan24Hours() {
        savesWithId();
        given(suggestions.findById(7L)).willReturn(Optional.of(suggestion(USER_ID, NOW.minus(Duration.ofHours(25)))));

        var saved = service.save(USER_ID, "Aporte", "Texto", null, 7L);

        assertThat(saved.analysis().contributionSuggestionId()).isNull();
        assertThat(saved.warning()).hasValueSatisfying(warning -> assertThat(warning).contains("older than 24 hours"));
    }

    @Test
    void shouldNotLinkSuggestionsOfAnotherUser() {
        savesWithId();
        given(suggestions.findById(7L)).willReturn(Optional.of(suggestion(99L, NOW)));

        var saved = service.save(USER_ID, "Aporte", "Texto", null, 7L);

        assertThat(saved.analysis().contributionSuggestionId()).isNull();
        assertThat(saved.warning()).isPresent();
    }

    @Test
    void shouldRejectTextsLongerThanTheLimit() {
        assertThatThrownBy(() -> service.save(USER_ID, "Aporte", "x".repeat(Analysis.MAX_TEXT_LENGTH + 1), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void savesWithId() {
        given(analyses.save(any())).willAnswer(invocation -> {
            Analysis analysis = invocation.getArgument(0);
            return new Analysis(10L, analysis.userId(), analysis.createdAt(), analysis.title(), analysis.amount(),
                    analysis.text(), analysis.contributionSuggestionId());
        });
    }

    private static ContributionSuggestion suggestion(Long userId, Instant createdAt) {
        return new ContributionSuggestion(7L, userId, 3L, createdAt, Money.brl("1000"), SuggestionSource.MCP, List.of());
    }
}
