package com.nepopay.common;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error codes. The frontend maps these to NepOpay personality messages. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to do that."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found."),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "That username is not available."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "That email is already registered."),
    PHONE_TAKEN(HttpStatus.CONFLICT, "That phone number is already registered."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Those details are already registered."),
    INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance."),
    INVALID_PIN(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid transaction PIN."),
    PIN_LOCKED(HttpStatus.LOCKED, "Transactions are temporarily locked."),
    LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Transaction limit exceeded."),
    SELF_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "You cannot send money to yourself."),
    RECIPIENT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Recipient is unavailable."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "This request is already being processed."),
    IDEMPOTENCY_CONFLICT(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused with a different request."),
    TRANSACTION_FLAGGED(HttpStatus.ACCEPTED, "Transaction flagged for review."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
