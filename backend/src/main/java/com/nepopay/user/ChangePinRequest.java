package com.nepopay.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String currentPin,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String newPin) {
}
