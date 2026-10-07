package com.nepopay.fraud;

import java.util.Optional;

/**
 * One fraud rule. To add a rule: write a class that implements this and annotate it @Component.
 * The engine finds it automatically; no existing code changes (Open/Closed principle).
 */
@FunctionalInterface
public interface FraudRule {
    Optional<RuleHit> evaluate(TransferContext ctx);
}
