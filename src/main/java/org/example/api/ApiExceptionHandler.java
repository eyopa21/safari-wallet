package org.example.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLTransientConnectionException;

import static org.example.api.ErrorCode.INTERNAL_SERVER_ERROR;
import static org.example.api.ErrorCode.SERVICE_UNAVAILABLE;
import static org.example.api.ErrorCode.VALIDATION_FAILED;

// One shape for every error. Messages are ours, never a stack trace, SQL or a driver message.
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return respond(e.getCode(), e.getMessage());
    }

    // -- 400 ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> snake(f.getField()) + " " + f.getDefaultMessage())
                .sorted()
                .findFirst().orElse("request is invalid");
        return respond(VALIDATION_FAILED, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return respond(VALIDATION_FAILED, "request body is missing or malformed");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException e) {
        return respond(VALIDATION_FAILED, e.getHeaderName() + " header is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> badParam(MethodArgumentTypeMismatchException e) {
        return respond(VALIDATION_FAILED, e.getName() + " has the wrong type");
    }

    // -- 404 / 405 / 415 for things Spring rejects before our code runs ---------

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noRoute(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(new ApiError("NOT_FOUND", "no such endpoint"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> badMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(new ApiError("METHOD_NOT_ALLOWED", e.getMethod() + " is not allowed here"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> badMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415).body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "send application/json"));
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ApiError> springError(ErrorResponseException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(e.getStatusCode().toString().replace(' ', '_'), "request could not be handled"));
    }

    // -- 503 when the database is gone ------------------------------------------

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            SQLTransientConnectionException.class})
    ResponseEntity<ApiError> dbDown(Exception e) {
        log.error("database unreachable: {}", e.getClass().getSimpleName());
        return respond(SERVICE_UNAVAILABLE, "service temporarily unavailable, try again");
    }

    // -- 500 for everything else, details stay in the log -------------------------

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        log.error("unhandled error", e);
        return respond(INTERNAL_SERVER_ERROR, "unexpected error");
    }

    private static ResponseEntity<ApiError> respond(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus()).body(ApiError.of(code, message));
    }

    // Java field name -> the JSON name the client sent
    private static String snake(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
