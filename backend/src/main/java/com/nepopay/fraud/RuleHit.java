package com.nepopay.fraud;

/** One rule that fired: its name, the points it added, and a human-readable reason for the admin. */
public record RuleHit(String rule, int points, String reason) {
}
