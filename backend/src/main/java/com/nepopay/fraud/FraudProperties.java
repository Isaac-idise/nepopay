package com.nepopay.fraud;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepopay.fraud")
public record FraudProperties(
        boolean enabled,
        long highAmountKobo,
        int rapidTransferCount,
        long rapidWindowMinutes,
        long drainMinKobo,
        long newAccountMinutes,
        long newAccountMinKobo,
        long pinFailureWindowMinutes) {
}
