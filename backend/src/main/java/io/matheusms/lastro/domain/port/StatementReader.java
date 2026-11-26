package io.matheusms.lastro.domain.port;

import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.statement.Statement;

/** Reads a file exported by a broker or exchange (B3 movements, Binance transaction history). */
public interface StatementReader {

    /** {@link EntrySource#B3_IMPORT} or {@link EntrySource#BINANCE_CSV}. */
    EntrySource source();

    /**
     * @throws io.matheusms.lastro.domain.model.statement.UnreadableStatementException if the file is not in the
     *     expected format
     */
    Statement read(String fileName, byte[] content);
}
