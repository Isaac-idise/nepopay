package com.nepopay.fraud.rules;

import com.nepopay.fraud.FraudProperties;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import com.nepopay.transaction.TransactionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +20: many transfers in a short time (this one would be the Nth). */
@Component
public class RapidTransfersRule implements FraudRule {

    private final TransactionRepository transactions;
    private final FraudProperties props;

    public RapidTransfersRule(TransactionRepository transactions, FraudProperties props) {
        this.transactions = transactions;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        Instant since = ctx.now().minus(props.rapidWindowMinutes(), ChronoUnit.MINUTES);
        long recent = transactions.countSentSince(ctx.senderAccountId(), since);
        if (recent + 1 < props.rapidTransferCount()) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("RAPID_TRANSFERS", 20, "This would be transfer number " + (recent + 1)
                + " in the last " + props.rapidWindowMinutes() + " minutes."));
    }
}
