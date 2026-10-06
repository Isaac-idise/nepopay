package com.nepopay.transfer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** amountKobo is in kobo (N20,000 = 2000000): whole numbers only, no decimal-point ambiguity. */
public record TransferRequest(
        @NotBlank @Size(max = 40) String recipientUsername,
        @Positive long amountKobo,
        @Size(max = 140) String description,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "must be exactly 4 digits") String pin) {
}
