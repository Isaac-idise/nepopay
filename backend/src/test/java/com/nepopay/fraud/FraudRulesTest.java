package com.nepopay.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nepopay.audit.AuditLogRepository;
import com.nepopay.beneficiary.BeneficiaryRepository;
import com.nepopay.common.Money;
import com.nepopay.fraud.rules.DrainsBalanceRule;
import com.nepopay.fraud.rules.HighAmountRule;
import com.nepopay.fraud.rules.NewAccountLargeTransferRule;
import com.nepopay.fraud.rules.NewRecipientRule;
import com.nepopay.fraud.rules.RapidTransfersRule;
import com.nepopay.fraud.rules.RecentPinFailuresRule;
import com.nepopay.transaction.TransactionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FraudRulesTest {

    private static final FraudProperties PROPS =
            new FraudProperties(true, 5_000_000L, 5, 10L, 1_000_000L, 10L, 2_000_000L, 30L);
    private static final Instant NOW = Instant.parse("2026-10-06T12:00:00Z");

    private static TransferContext ctx(long amount, long balance, Instant accountCreated) {
        return new TransferContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Money.ofKobo(amount), balance, accountCreated, NOW);
    }

    private static TransferContext ctx(long amount) {
        return ctx(amount, 10_000_000, NOW.minus(1, ChronoUnit.DAYS));
    }

    @Test
    void highAmountFiresAtTheThresholdNotBelow() {
        HighAmountRule rule = new HighAmountRule(PROPS);
        assertThat(rule.evaluate(ctx(4_999_999))).isEmpty();
        assertThat(rule.evaluate(ctx(5_000_000))).get().extracting(RuleHit::points).isEqualTo(30);
    }

    @Test
    void drainsBalanceNeedsBothAHugeShareAndARealAmount() {
        DrainsBalanceRule rule = new DrainsBalanceRule(PROPS);
        assertThat(rule.evaluate(ctx(9_000_000, 10_000_000, NOW))).get().extracting(RuleHit::points).isEqualTo(10);
        assertThat(rule.evaluate(ctx(8_999_999, 10_000_000, NOW))).isEmpty();   // under 90%
        assertThat(rule.evaluate(ctx(500_000, 500_000, NOW))).isEmpty();        // tiny balance is ignored
    }

    @Test
    void newAccountRuleChecksAgeAndAmount() {
        NewAccountLargeTransferRule rule = new NewAccountLargeTransferRule(PROPS);
        assertThat(rule.evaluate(ctx(2_000_000, 10_000_000, NOW.minus(5, ChronoUnit.MINUTES)))).isPresent();
        assertThat(rule.evaluate(ctx(2_000_000, 10_000_000, NOW.minus(11, ChronoUnit.MINUTES)))).isEmpty();
        assertThat(rule.evaluate(ctx(1_999_999, 10_000_000, NOW.minus(5, ChronoUnit.MINUTES)))).isEmpty();
    }

    @Test
    void newRecipientRuleIgnoresBeneficiariesAndPastPayees() {
        TransactionRepository txs = mock(TransactionRepository.class);
        BeneficiaryRepository bens = mock(BeneficiaryRepository.class);
        NewRecipientRule rule = new NewRecipientRule(txs, bens);
        TransferContext c = ctx(100_000);

        when(bens.existsByUserIdAndBeneficiaryAccountId(c.senderUserId(), c.recipientAccountId())).thenReturn(true);
        assertThat(rule.evaluate(c)).isEmpty();

        when(bens.existsByUserIdAndBeneficiaryAccountId(c.senderUserId(), c.recipientAccountId())).thenReturn(false);
        when(txs.countPaidBetween(c.senderAccountId(), c.recipientAccountId())).thenReturn(1L);
        assertThat(rule.evaluate(c)).isEmpty();

        when(txs.countPaidBetween(c.senderAccountId(), c.recipientAccountId())).thenReturn(0L);
        assertThat(rule.evaluate(c)).get().extracting(RuleHit::points).isEqualTo(20);
    }

    @Test
    void rapidTransfersFiresWhenThisWouldBeTheFifth() {
        TransactionRepository txs = mock(TransactionRepository.class);
        RapidTransfersRule rule = new RapidTransfersRule(txs, PROPS);
        TransferContext c = ctx(100_000);

        when(txs.countSentSince(eq(c.senderAccountId()), any())).thenReturn(3L);   // this = 4th
        assertThat(rule.evaluate(c)).isEmpty();
        when(txs.countSentSince(eq(c.senderAccountId()), any())).thenReturn(4L);   // this = 5th
        assertThat(rule.evaluate(c)).get().extracting(RuleHit::points).isEqualTo(20);
    }

    @Test
    void pinFailureRuleReadsTheAuditLog() {
        AuditLogRepository audit = mock(AuditLogRepository.class);
        RecentPinFailuresRule rule = new RecentPinFailuresRule(audit, PROPS);
        TransferContext c = ctx(100_000);

        when(audit.countByUserIdAndActionAndCreatedAtAfter(eq(c.senderUserId()), eq("PIN_FAILED"), any())).thenReturn(0L);
        assertThat(rule.evaluate(c)).isEmpty();
        when(audit.countByUserIdAndActionAndCreatedAtAfter(eq(c.senderUserId()), eq("PIN_FAILED"), any())).thenReturn(2L);
        assertThat(rule.evaluate(c)).get().extracting(RuleHit::points).isEqualTo(20);
    }
}
