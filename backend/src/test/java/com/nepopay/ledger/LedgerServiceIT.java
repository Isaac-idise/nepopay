package com.nepopay.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nepopay.AbstractIntegrationTest;
import com.nepopay.auth.RegisterRequest;
import com.nepopay.auth.RegisterResponse;
import com.nepopay.auth.RegistrationService;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.Money;
import com.nepopay.common.NepOpayException;
import com.nepopay.transaction.Transaction;
import com.nepopay.transaction.TransactionType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerServiceIT extends AbstractIntegrationTest {

    @Autowired RegistrationService registration;
    @Autowired LedgerService ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private static final long HUNDRED_K = 10_000_000L;

    private UUID newUser() {
        String s = Long.toString(System.nanoTime(), 36);
        String phone = "080" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        RegisterResponse r = registration.register(new RegisterRequest(
                "Test", "User", s + "@example.com", phone, "u" + s, "Passw0rdTest", "1234"));
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_number = ?", UUID.class, r.accountNumber());
    }

    private long balance(UUID acc) {
        return jdbc.queryForObject("SELECT balance_kobo FROM accounts WHERE id = ?", Long.class, acc);
    }

    /** credits minus debits for one account: must always equal its cached balance. */
    private long ledgerNet(UUID acc) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount_kobo ELSE -amount_kobo END), 0)
                FROM ledger_entries WHERE account_id = ?""", Long.class, acc);
    }

    private void send(UUID from, UUID to, long naira) {
        tx.executeWithoutResult(s -> ledger.post(from, to, Money.ofNaira(naira), TransactionType.P2P_TRANSFER, "test"));
    }

    @Test
    void transferMovesMoneyAndWritesBalancedEntries() {
        UUID a = newUser();
        UUID b = newUser();

        Transaction t = tx.execute(s -> ledger.post(a, b, Money.ofNaira(20_000), TransactionType.P2P_TRANSFER, "test"));

        assertThat(balance(a)).isEqualTo(HUNDRED_K - 2_000_000L);
        assertThat(balance(b)).isEqualTo(HUNDRED_K + 2_000_000L);
        assertThat(ledgerNet(a)).isEqualTo(balance(a));
        assertThat(ledgerNet(b)).isEqualTo(balance(b));

        Long debits = jdbc.queryForObject(
                "SELECT SUM(amount_kobo) FROM ledger_entries WHERE transaction_id = ? AND entry_type = 'DEBIT'", Long.class, t.getId());
        Long credits = jdbc.queryForObject(
                "SELECT SUM(amount_kobo) FROM ledger_entries WHERE transaction_id = ? AND entry_type = 'CREDIT'", Long.class, t.getId());
        assertThat(debits).isEqualTo(credits).isEqualTo(2_000_000L);
    }

    @Test
    void insufficientBalanceChangesNothing() {
        UUID a = newUser();
        UUID b = newUser();

        assertThatThrownBy(() -> send(a, b, 100_001))
                .isInstanceOfSatisfying(NepOpayException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));

        assertThat(balance(a)).isEqualTo(HUNDRED_K);
        assertThat(balance(b)).isEqualTo(HUNDRED_K);
    }

    @Test
    void concurrentSpendingCanNeverOverdraw() throws Exception {
        UUID a = newUser();
        UUID b = newUser();
        int attempts = 30; // 30 x N10,000 against a N100,000 balance: exactly 10 may succeed

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    send(a, b, 10_000);
                    return true;
                } catch (NepOpayException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) {
            if (f.get(30, TimeUnit.SECONDS)) {
                ok++;
            }
        }
        pool.shutdown();

        assertThat(ok).isEqualTo(10);
        assertThat(balance(a)).isZero();
        assertThat(balance(b)).isEqualTo(2 * HUNDRED_K);
        assertThat(ledgerNet(a)).isEqualTo(balance(a));
        assertThat(ledgerNet(b)).isEqualTo(balance(b));
    }

    @Test
    void oppositeTransfersAtTheSameTimeDoNotDeadlock() throws Exception {
        UUID a = newUser();
        UUID b = newUser();

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            final boolean forward = i % 2 == 0;
            futures.add(pool.submit(() -> {
                start.await();
                if (forward) {
                    send(a, b, 1_000);
                } else {
                    send(b, a, 1_000);
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS); // a deadlock would time out here
        }
        pool.shutdown();

        assertThat(balance(a)).isEqualTo(HUNDRED_K);
        assertThat(balance(b)).isEqualTo(HUNDRED_K);
        assertThat(ledgerNet(a)).isEqualTo(balance(a));
    }
}
