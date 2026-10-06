package com.nepopay.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/** Uniform envelope for every API response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, Instant.now());
    }

    public static ApiResponse<Void> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, code.name(), message, Instant.now());
    }
}
