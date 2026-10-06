package com.nepopay.demo;

import com.nepopay.account.Account;
import com.nepopay.account.AccountRepository;
import com.nepopay.account.AccountType;
import com.nepopay.common.Money;
import com.nepopay.ledger.LedgerService;
import com.nepopay.transaction.Transaction;
import com.nepopay.transaction.TransactionType;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Demo money is issued by the SYSTEM_FUNDING account through a normal ledger posting. */
@Service
public class DemoFundingService {

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final Money welcomeCredit;

    public DemoFundingService(AccountRepository accounts, LedgerService ledger,
                              @Value("${nepopay.demo.welcome-credit-naira:100000}") long welcomeCreditNaira) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.welcomeCredit = Money.ofNaira(welcomeCreditNaira);
    }

    public Money welcomeAmount() {
        return welcomeCredit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction creditWelcomeFunds(Account userAccount) {
        // ID ONLY. Loading the funding Account entity here would cache its balance BEFORE the ledger locks
        // the row; with several signups at once that cached balance is stale and the update fails.
        UUID fundingId = accounts.findIdByAccountType(AccountType.SYSTEM_FUNDING)
                .orElseThrow(() -> new IllegalStateException("SYSTEM_FUNDING account is missing"));
        return ledger.post(fundingId, userAccount.getId(), welcomeCredit,
                TransactionType.DEMO_WELCOME_CREDIT, "Demo welcome credit");
    }
}
