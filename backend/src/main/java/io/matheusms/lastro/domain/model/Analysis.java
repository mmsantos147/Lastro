package io.matheusms.lastro.domain.model;

import static io.matheusms.lastro.domain.model.Preconditions.requireNotBlank;

import java.time.Instant;
import java.util.Objects;

/**
 * Analysis written by Claude on claude.ai and saved through the MCP tool {@code save_analysis}. The text is
 * untrusted markdown: the frontend renders it without raw HTML, and the table of amounts comes from the linked
 * {@link ContributionSuggestion}, never from the text.
 *
 * @param id {@code null} until persisted
 * @param amount contribution discussed in the analysis, in BRL; {@code null} for exploratory conversations
 * @param contributionSuggestionId suggestion whose numbers the analysis comments on; may be {@code null}
 */
public record Analysis(
        Long id, Long userId, Instant createdAt, String title, Money amount, String text,
        Long contributionSuggestionId) {

    public static final int MAX_TITLE_LENGTH = 120;
    public static final int MAX_TEXT_LENGTH = 20_000;

    public Analysis {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        requireNotBlank(title, "title");
        requireNotBlank(text, "text");
        title = title.strip();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Title must have at most %d characters".formatted(MAX_TITLE_LENGTH));
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text must have at most %d characters".formatted(MAX_TEXT_LENGTH));
        }
        if (amount != null && (!amount.isIn(Currency.BRL) || amount.amount().signum() <= 0)) {
            throw new IllegalArgumentException("Amount must be positive and in BRL");
        }
    }
}
