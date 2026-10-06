package com.nepopay.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DemoResetRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String pin) {
}
