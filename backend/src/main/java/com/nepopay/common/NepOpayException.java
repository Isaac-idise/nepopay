package com.nepopay.common;

/** Business-rule failure carrying an ErrorCode. Thrown by services, translated to JSON by GlobalExceptionHandler. */
public class NepOpayException extends RuntimeException {

    private final ErrorCode errorCode;

    public NepOpayException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public NepOpayException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
