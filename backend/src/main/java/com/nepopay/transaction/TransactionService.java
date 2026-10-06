package com.nepopay.transaction;

import com.nepopay.account.Account;
import com.nepopay.account.AccountService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.ledger.LedgerEntry;
import com.nepopay.ledger.LedgerEntryRepository;
import com.nepopay.transaction.TransactionDtos.EntryView;
import com.nepopay.transaction.TransactionDtos.Party;
import com.nepopay.transaction.TransactionDtos.TransactionDetail;
import com.nepopay.transaction.TransactionDtos.TransactionView;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final AccountService accounts;
    private final TransactionRepository transactions;
    private final LedgerEntryRepository entries;

    public TransactionService(AccountService accounts, TransactionRepository transactions,
                              LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> history(UUID userId, Pageable pageable) {
        UUID mine = accounts.getUserAccount(userId).getId();
        return transactions.findHistory(mine, pageable).map(t -> view(t, mine));
    }

    @Transactional(readOnly = true)
    public TransactionDetail detail(UUID userId, String reference) {
        UUID mine = accounts.getUserAccount(userId).getId();
        // Someone else's transaction looks exactly like a missing one: existence is not revealed.
        Transaction t = transactions.findByReference(reference)
                .filter(x -> x.getSender().getId().equals(mine) || x.getReceiver().getId().equals(mine))
                .orElseThrow(() -> new NepOpayException(ErrorCode.NOT_FOUND, "Transaction not found."));

        TransactionView v = view(t, mine);
        List<EntryView> ledger = entries.findByTransactionIdAndAccountIdOrderByIdAsc(t.getId(), mine).stream()
                .map(TransactionService::entryView).toList();
        return new TransactionDetail(v.reference(), v.type(), v.status(), v.direction(), v.amountKobo(),
                v.counterparty(), v.description(), v.createdAt(), ledger);
    }

    private static TransactionView view(Transaction t, UUID mine) {
        boolean sent = t.getSender().getId().equals(mine);
        Account other = sent ? t.getReceiver() : t.getSender();
        return new TransactionView(t.getReference(), t.getType().name(), t.getStatus().name(),
                sent ? "DEBIT" : "CREDIT", t.getAmountKobo(), partyOf(other), t.getDescription(), t.getCreatedAt());
    }

    private static Party partyOf(Account a) {
        return a.getUser() == null
                ? new Party(null, "NepOpay")
                : new Party(a.getUser().getUsername(), a.getUser().getDisplayName());
    }

    private static EntryView entryView(LedgerEntry e) {
        return new EntryView(e.getEntryType().name(), e.getAmountKobo(), e.getBalanceAfterKobo(), e.getCreatedAt());
    }
}
