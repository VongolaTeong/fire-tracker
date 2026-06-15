package com.firetracker.common;

import com.firetracker.portfolio.MissingMarketDataException;
import com.firetracker.projection.InvalidProjectionRequestException;
import com.firetracker.transaction.CsvImportException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates common failures into RFC 9457 problem responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid");
        problem.setTitle("Validation failed");

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(CsvImportException.class)
    public ProblemDetail handleCsvImport(CsvImportException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("CSV import failed");
        return problem;
    }

    @ExceptionHandler(InvalidProjectionRequestException.class)
    public ProblemDetail handleInvalidProjection(InvalidProjectionRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid projection request");
        return problem;
    }

    @ExceptionHandler(MissingMarketDataException.class)
    public ProblemDetail handleMissingMarketData(MissingMarketDataException ex) {
        // 422: the request is well-formed, but the portfolio can't be valued until the
        // missing price/FX rows exist — distinct from a 400 (bad request) or 404 (no route).
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        problem.setTitle("Missing market data");
        return problem;
    }
}
