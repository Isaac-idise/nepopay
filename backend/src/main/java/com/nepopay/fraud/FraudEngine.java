package com.nepopay.fraud;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Adds up the points from every rule (capped at 100) and turns the total into a risk level. */
@Service
public class FraudEngine {

    private final List<FraudRule> rules;

    public FraudEngine(List<FraudRule> rules) {
        this.rules = rules;
    }

    public FraudAssessment assess(TransferContext ctx) {
        List<RuleHit> hits = rules.stream()
                .map(r -> r.evaluate(ctx))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(RuleHit::points).reversed().thenComparing(RuleHit::rule))
                .toList();
        int score = Math.min(100, hits.stream().mapToInt(RuleHit::points).sum());
        return new FraudAssessment(score, RiskLevel.of(score), hits);
    }
}
