package com.nepopay.beneficiary;

import java.time.Instant;

public record BeneficiaryView(String username, String displayName, String accountLast4,
                              String nickname, Instant createdAt) {
}
