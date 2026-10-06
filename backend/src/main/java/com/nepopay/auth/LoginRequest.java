package com.nepopay.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** identifier = username or email. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String identifier,
        @NotBlank @Size(max = 128) String password) {
}
