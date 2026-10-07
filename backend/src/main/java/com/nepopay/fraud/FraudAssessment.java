package com.nepopay.fraud;

import java.util.List;

public record FraudAssessment(int score, RiskLevel level, List<RuleHit> hits) {

    public static FraudAssessment none() {
        return new FraudAssessment(0, RiskLevel.LOW, List.of());
    }
}
