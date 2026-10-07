package com.nepopay.fraud;

public enum RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    /** 0-29 LOW, 30-59 MEDIUM, 60-79 HIGH, 80-100 CRITICAL. */
    public static RiskLevel of(int score) {
        if (score >= 80) return CRITICAL;
        if (score >= 60) return HIGH;
        if (score >= 30) return MEDIUM;
        return LOW;
    }

    /** HIGH and CRITICAL transfers are held for review. */
    public boolean holds() {
        return this == HIGH || this == CRITICAL;
    }
}
