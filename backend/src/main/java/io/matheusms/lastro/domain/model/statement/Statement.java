package io.matheusms.lastro.domain.model.statement;

import java.util.List;

/**
 * Content of an imported file (B3 movements, Binance transaction history), in domain terms.
 *
 * @param entries what can be recorded, in file order
 * @param issues rows left out by the reader, with the reason
 */
public record Statement(List<StatementEntry> entries, List<ImportIssue> issues) {

    public Statement {
        entries = List.copyOf(entries);
        issues = List.copyOf(issues);
    }
}
