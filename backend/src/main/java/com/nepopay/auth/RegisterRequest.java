package com.nepopay.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 50) String firstName,
        @NotBlank @Size(max = 50) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^(\\+234|0)[789][01]\\d{8}$",
                message = "must be a valid Nigerian mobile number") String phone,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                message = "must be 3-20 letters, digits or underscores") String username,
        @NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
                message = "must be 8-72 characters with at least one letter and one number") String password,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String pin) {
}
