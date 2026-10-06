package com.nepopay.transfer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepopay.limits")
public record TransferLimits(long minTransferKobo, long perTransferKobo, long dailyKobo) {
}
