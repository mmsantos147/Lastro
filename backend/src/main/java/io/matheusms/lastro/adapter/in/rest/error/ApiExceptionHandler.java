package io.matheusms.lastro.adapter.in.rest.error;

import io.matheusms.lastro.application.ConflictException;
import io.matheusms.lastro.application.NotFoundException;
import io.matheusms.lastro.domain.model.statement.UnreadableStatementException;
import io.matheusms.lastro.domain.service.InsufficientQuantityException;
import io.matheusms.lastro.domain.service.MissingMarketDataException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem details. The {@code detail} is shown to the user by the frontend,
 * so it must never carry internal data (stack traces, SQL).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The data conflicts with an existing record");
    }

    /** The change would make the history inconsistent (e.g. selling more than held at that date). */
    @ExceptionHandler(InsufficientQuantityException.class)
    ProblemDetail insufficientQuantity(InsufficientQuantityException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }

    /** Domain invariants are checked in constructors and reported as IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** The frontend recognizes the {@code code} and suggests refreshing the market data. */
    @ExceptionHandler(MissingMarketDataException.class)
    ProblemDetail missingMarketData(MissingMarketDataException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setProperty("code", "MISSING_MARKET_DATA");
        return problem;
    }

    /** The frontend recognizes the {@code code} and asks for the right file. */
    @ExceptionHandler(UnreadableStatementException.class)
    ProblemDetail unreadableStatement(UnreadableStatementException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("code", "UNREADABLE_FILE");
        return problem;
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ProblemDetail unsupported(UnsupportedOperationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }
}
