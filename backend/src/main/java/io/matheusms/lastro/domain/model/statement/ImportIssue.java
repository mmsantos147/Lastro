package io.matheusms.lastro.domain.model.statement;

import java.util.Objects;

/**
 * Row of an imported file that was not recorded as read, or was recorded with a caveat. The frontend shows a message
 * per {@link Code}; {@code detail} is complementary, in English.
 *
 * @param line 1-based line of the file (the header is line 1)
 * @param description short text identifying the row, e.g. {@code "Incorporação · AUAU3 · 100"}
 */
public record ImportIssue(int line, Code code, String description, String detail) {

    public ImportIssue {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(description, "description must not be null");
    }

    public ImportIssue(int line, Code code, String description) {
        this(line, code, description, null);
    }

    public enum Code {
        /** Has no effect on positions (custody transfer, subscription rights, cash deposit, balance update). */
        INFORMATIONAL(false),
        /** Cash received in a corporate event (fraction auction, redemption in a merger); not recorded. */
        CORPORATE_EVENT_CASH(false),
        /**
         * Private fixed income (CDB, LCI, LCA) recorded without its terms (rate, maturity), which the file does not
         * have: valued at cost until the user types them.
         */
        FIXED_INCOME_TERMS_REQUIRED(true),
        /** Movement the import does not understand or does not support (e.g. capital return). */
        UNSUPPORTED(true),
        /** Shares received in a merger: the user must register the merger, naming the merged asset. */
        MERGER_REQUIRED(true),
        /** Recording the row would make the history inconsistent (e.g. selling more than held). */
        INCONSISTENT_HISTORY(true),
        /** A split or bonus on a date the user held none of the asset. */
        NO_POSITION_FOR_EVENT(true),
        /** Bonus shares recorded with assigned cost zero, because the file does not have it. */
        UNKNOWN_ASSIGNED_COST(true),
        /** Conversion between two crypto assets without a price in cash: recorded at zero cost (dust). */
        PRICE_UNAVAILABLE(true),
        /** The row could not be read. */
        INVALID_ROW(true);

        private final boolean needsAttention;

        Code(boolean needsAttention) {
            this.needsAttention = needsAttention;
        }

        /** {@code false} for rows that were correctly left out. */
        public boolean needsAttention() {
            return needsAttention;
        }
    }
}
