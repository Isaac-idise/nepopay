package com.nepopay.fraud.rules;

import com.nepopay.common.Money;
import com.nepopay.fraud.FraudProperties;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +10: a brand-new account making a sizeable transfer. */
@Component
public class NewAccountLargeTransferRule implements FraudRule {

    private final FraudProperties props;

    public NewAccountLargeTransferRule(FraudProperties props) {
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        boolean isNew = Duration.between(ctx.senderAccountCreatedAt(), ctx.now()).toMinutes() < props.newAccountMinutes();
        if (!isNew || ctx.amount().kobo() < props.newAccountMinKobo()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("NEW_ACCOUNT_LARGE_TRANSFER", 10, "Account is less than "
                + props.newAccountMinutes() + " minutes old and is sending " + ctx.amount()
                + " (threshold " + Money.ofKobo(props.newAccountMinKobo()) + ")."));
    }
}
