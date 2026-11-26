package io.matheusms.lastro.domain.model.statement;

/** The uploaded file is not in the expected format (wrong file, corrupted, unknown columns). */
public class UnreadableStatementException extends RuntimeException {

    public UnreadableStatementException(String message) {
        super(message);
    }

    public UnreadableStatementException(String message, Throwable cause) {
        super(message, cause);
    }
}
