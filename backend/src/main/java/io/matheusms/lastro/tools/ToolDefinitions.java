package io.matheusms.lastro.tools;

/**
 * Names and descriptions of the portfolio tools. Claude decides when to call each tool by reading these
 * descriptions, so they say what comes back, in which unit, and what to do with it.
 */
public final class ToolDefinitions {

    public static final String PORTFOLIO_SUMMARY = "portfolio_summary";
    public static final String PORTFOLIO_SUMMARY_DESCRIPTION = """
            Summary of the user's investment portfolio: current market value in BRL, net amount invested \
            (contributions minus sales and cash income), unrealized gain, and the time-weighted return since the \
            first trade and over the last 12 months (fields ending in _last_year) compared with CDI and Ibovespa over \
            the same periods. Returns are ratios (0.1 = 10%); null when unavailable. Call it first when analyzing \
            the portfolio.""";

    public static final String LIST_POSITIONS = "list_positions";
    public static final String LIST_POSITIONS_DESCRIPTION = """
            Open positions with quantity, average cost, latest price and its date, market value, unrealized result \
            and weight in the portfolio, all in BRL. Positions without a quote are valued at cost (price is null). \
            Optionally filter by asset class.""";
    public static final String ASSET_CLASS_PARAM = """
            Optional asset class filter: STOCK (Brazilian stocks), FII (real estate funds), ETF, TREASURY \
            (Tesouro Direto government bonds), CRYPTO or FIXED_INCOME (bank CDB/LCI/LCA, valued on the curve \
            agreed at purchase, before income tax).""";

    public static final String ALLOCATION_VS_TARGET = "allocation_vs_target";
    public static final String ALLOCATION_VS_TARGET_DESCRIPTION = """
            Current allocation compared with the target allocation of the active strategy. For each target (an \
            asset class or a specific asset): value in BRL, current and target percentages, deviation in \
            percentage points and status (BELOW, WITHIN or ABOVE the tolerance band; NO_TARGET for classes held \
            outside the strategy). When there is no active strategy, has_strategy is false: the user has not \
            defined a target allocation yet.""";

    public static final String SUGGEST_CONTRIBUTION = "suggest_contribution";
    public static final String SUGGEST_CONTRIBUTION_DESCRIPTION = """
            Runs the deterministic rules engine that splits a contribution among the targets of the active \
            strategy, rebalancing only with new money (it never suggests selling). Returns the items (class or \
            asset, amount in BRL, reason code, current and target percentages) and a suggestion_id to pass to \
            save_analysis. Items without an asset mean the user must choose an asset of that class. Present these \
            numbers as they are; never change them. Without an active strategy, returns a warning and no items.""";
    public static final String AMOUNT_PARAM = "Contribution amount in BRL, e.g. 2500.00";

    public static final String DISTRIBUTION_HISTORY = "distribution_history";
    public static final String DISTRIBUTION_HISTORY_DESCRIPTION = """
            Passive income received (dividends, JCP, real estate fund income, interest and staking rewards), net \
            of withheld tax, in BRL: total per month with the 12-month moving average, income of the last 12 \
            months per asset and yield on cost (12-month income over the current cost of the positions).""";
    public static final String MONTHS_PARAM = "How many months of history, from 1 to 120 (default 12)";

    public static final String SAVE_ANALYSIS = "save_analysis";
    public static final String SAVE_ANALYSIS_DESCRIPTION = """
            Saves the analysis written in this conversation so it appears on the Analyses screen of the user's \
            Lastro website. The text is markdown with the analysis paragraphs only, WITHOUT the table of amounts: \
            the website builds the table from suggestion_id. Returns the analysis id and URL; share the URL with \
            the user. If the suggestion is unknown or older than 24 hours, the analysis is saved without it and a \
            warning is returned.""";
    public static final String TITLE_PARAM = "Short descriptive title, up to 120 characters";
    public static final String TEXT_PARAM = "Analysis in markdown, up to 20000 characters, without the table of amounts";
    public static final String ANALYSIS_AMOUNT_PARAM = "Contribution amount discussed, in BRL (optional)";
    public static final String SUGGESTION_ID_PARAM = "suggestion_id returned by suggest_contribution (optional)";

    public static final String MONTHLY_ANALYSIS_PROMPT = "monthly_analysis";
    public static final String MONTHLY_ANALYSIS_PROMPT_DESCRIPTION = """
            Monthly portfolio analysis and contribution decision, in Portuguese, following the investor's rules.""";
    public static final String PROMPT_AMOUNT_PARAM = "Contribution amount in BRL (optional; Claude asks when missing)";

    private ToolDefinitions() {
    }
}
