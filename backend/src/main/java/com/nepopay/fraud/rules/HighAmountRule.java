package com.nepopay.fraud.rules;

import com.nepopay.common.Money;
import com.nepopay.fraud.FraudProperties;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +30: a large single transfer. */
@Component
public class HighAmountRule implements FraudRule {

    private final FraudProperties props;

    public HighAmountRule(FraudProperties props) {
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        if (ctx.amount().kobo() < props.highAmountKobo()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("HIGH_AMOUNT", 30, "Amount " + ctx.amount()
                + " is at or above the high-value threshold of " + Money.ofKobo(props.highAmountKobo()) + "."));
    }
}
