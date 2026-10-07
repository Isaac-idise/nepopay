package com.nepopay.fraud.rules;

import com.nepopay.audit.AuditLogRepository;
import com.nepopay.fraud.FraudProperties;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +20: someone recently guessed wrong PINs on this account (the audit log is the evidence). */
@Component
public class RecentPinFailuresRule implements FraudRule {

    private final AuditLogRepository audit;
    private final FraudProperties props;

    public RecentPinFailuresRule(AuditLogRepository audit, FraudProperties props) {
        this.audit = audit;
        this.props = props;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        Instant since = ctx.now().minus(props.pinFailureWindowMinutes(), ChronoUnit.MINUTES);
        long failures = audit.countByUserIdAndActionAndCreatedAtAfter(ctx.senderUserId(), "PIN_FAILED", since);
        if (failures == 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("RECENT_PIN_FAILURES", 20,
                failures + " wrong PIN attempt(s) in the last " + props.pinFailureWindowMinutes() + " minutes."));
    }
}
