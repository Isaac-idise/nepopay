package com.nepopay.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nepopay.admin")
public record AdminProperties(String username, String email, String initialPassword) {
}
