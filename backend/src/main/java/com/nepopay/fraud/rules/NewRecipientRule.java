package com.nepopay.fraud.rules;

import com.nepopay.beneficiary.BeneficiaryRepository;
import com.nepopay.fraud.FraudRule;
import com.nepopay.fraud.RuleHit;
import com.nepopay.fraud.TransferContext;
import com.nepopay.transaction.TransactionRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** +20: paying someone who is neither a saved beneficiary nor someone paid before. */
@Component
public class NewRecipientRule implements FraudRule {

    private final TransactionRepository transactions;
    private final BeneficiaryRepository beneficiaries;

    public NewRecipientRule(TransactionRepository transactions, BeneficiaryRepository beneficiaries) {
        this.transactions = transactions;
        this.beneficiaries = beneficiaries;
    }

    @Override
    public Optional<RuleHit> evaluate(TransferContext ctx) {
        if (beneficiaries.existsByUserIdAndBeneficiaryAccountId(ctx.senderUserId(), ctx.recipientAccountId())) {
            return Optional.empty();
        }
        if (transactions.countPaidBetween(ctx.senderAccountId(), ctx.recipientAccountId()) > 0) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit("NEW_RECIPIENT", 20,
                "Recipient is not a saved beneficiary and has not been paid before."));
    }
}
