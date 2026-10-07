package com.nepopay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import com.nepopay.common.Money;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FraudEngineTest {

    private static TransferContext ctx() {
        return new TransferContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.ofKobo(1_000_000), 10_000_000, Instant.now(), Instant.now());
    }

    private static FraudRule rule(String name, int points) {
        return c -> Optional.of(new RuleHit(name, points, name + " fired"));
    }

    private static FraudRule silent() {
        return c -> Optional.empty();
    }

    @Test
    void noRulesFiringMeansLowRisk() {
        FraudAssessment a = new FraudEngine(List.of(silent(), silent())).assess(ctx());
        assertThat(a.score()).isZero();
        assertThat(a.level()).isEqualTo(RiskLevel.LOW);
        assertThat(a.hits()).isEmpty();
    }

    @Test
    void pointsAddUpAndPickTheRightLevel() {
        FraudAssessment a = new FraudEngine(List.of(rule("A", 30), rule("B", 20), silent(), rule("C", 10))).assess(ctx());
        assertThat(a.score()).isEqualTo(60);
        assertThat(a.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(a.hits()).extracting(RuleHit::rule).containsExactly("A", "B", "C"); // biggest first
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        FraudAssessment a = new FraudEngine(List.of(rule("A", 70), rule("B", 50))).assess(ctx());
        assertThat(a.score()).isEqualTo(100);
        assertThat(a.level()).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void aNewRuleCanBeAddedWithoutChangingTheEngine() {
        FraudEngine engine = new FraudEngine(List.of(rule("EXISTING", 20), rule("BRAND_NEW_RULE", 40)));
        assertThat(engine.assess(ctx()).score()).isEqualTo(60);
    }
}
