package com.nepopay.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepopay.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int loginIpPerMinute,
        int loginAccountPerWindow,
        int registerPerHour,
        int refreshPerMinute,
        int searchPerMinute,
        int transferPerMinute,
        int pinChangePerWindow,
        int resetPerHour) {
}
