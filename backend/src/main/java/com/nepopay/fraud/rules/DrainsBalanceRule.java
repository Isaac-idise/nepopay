package com.nepopay.fraud.rules;

import com.nepopay.fraud.FraudProperties;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +10: sending (almost) everything at once. Ignored for tiny balances. */
@Component
public class DrainsBalanceRule implements FraudRule {

    private final FraudProperties props;

    public DrainsBalanceRule(FraudProperties props) {
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        long amount = ctx.amount().kobo();
        if (amount < props.drainMinKobo() || amount * 10 < ctx.senderBalanceKobo() * 9) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("DRAINS_BALANCE", 10, "Transfer uses at least 90% of the sender's balance."));
    }
}
