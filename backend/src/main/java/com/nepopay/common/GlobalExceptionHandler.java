package com.nepopay.common;

import java.sql.SQLException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Single place where exceptions become safe, structured JSON. Never leaks stack traces or SQL. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NepOpayException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(NepOpayException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, "Malformed request body."));
    }

    /** Two requests racing past the "exists?" check: the unique index is the real guard, and lands here. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        if (isUniqueViolation(ex)) {
            ErrorCode code = ErrorCode.USER_ALREADY_EXISTS;
            return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, code.getDefaultMessage()));
        }
        return handleUnexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex); // full detail stays in server logs only
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code, code.getDefaultMessage()));
    }

    private static boolean isUniqueViolation(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException s && "23505".equals(s.getSQLState())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
