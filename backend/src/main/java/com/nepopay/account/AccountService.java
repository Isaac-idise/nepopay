package com.nepopay.account;

import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.user.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_ATTEMPTS = 10;

    private final AccountRepository accounts;
    private final AccountNumberGenerator numbers;

    public AccountService(AccountRepository accounts, AccountNumberGenerator numbers) {
        this.accounts = accounts;
        this.numbers = numbers;
    }

    /** Opens a zero-balance account. Money only arrives later, through the ledger. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Account openUserAccount(User user) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String number = numbers.generate();
            if (!accounts.existsByAccountNumber(number)) {
                return accounts.save(Account.forUser(user, number));
            }
        }
        throw new IllegalStateException("Could not generate a unique account number");
    }

    @Transactional(readOnly = true)
    public Account getUserAccount(UUID userId) {
        return accounts.findByUserIdAndAccountType(userId, AccountType.USER)
                .orElseThrow(() -> new NepOpayException(ErrorCode.USER_NOT_FOUND, "Account not found."));
    }

    /** Call after the ledger has locked the account in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markHistoryReset(UUID accountId, Instant at) {
        accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found: " + accountId))
                .markHistoryReset(at);
    }
}
