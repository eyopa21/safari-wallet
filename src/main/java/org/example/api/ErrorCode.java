package org.example.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

// the whole error contract: every code and the status it is always returned with
@Getter
public enum ErrorCode {

    VALIDATION_FAILED(BAD_REQUEST),

    WALLET_NOT_FOUND(NOT_FOUND),
    TRANSFER_NOT_FOUND(NOT_FOUND),
    PAYOUT_NOT_FOUND(NOT_FOUND),
    BATCH_NOT_FOUND(NOT_FOUND),

    IDEMPOTENCY_CONFLICT(CONFLICT),
    REQUEST_IN_PROGRESS(CONFLICT),
    ALREADY_REVERSED(CONFLICT),

    INSUFFICIENT_FUNDS(UNPROCESSABLE_ENTITY),
    WALLET_NOT_ACTIVE(UNPROCESSABLE_ENTITY),
    SAME_WALLET(UNPROCESSABLE_ENTITY),
    NOT_REVERSIBLE(UNPROCESSABLE_ENTITY),

    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }
}
