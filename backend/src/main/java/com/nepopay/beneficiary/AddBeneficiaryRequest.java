package com.nepopay.beneficiary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(
        @NotBlank @Size(max = 40) String username,
        @Size(max = 30) String nickname) {
}
