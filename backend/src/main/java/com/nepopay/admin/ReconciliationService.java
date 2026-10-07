package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.AccountDrift;
import com.nepopay.admin.AdminDtos.ReconciliationReport;
import com.nepopay.admin.AdminDtos.UnbalancedTransaction;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-checks the books from scratch. The ledger entries are the truth; cached balances must agree with them.
 * REPEATABLE_READ gives every query below the same snapshot, so a transfer committing mid-check
 * cannot make the report show a false mismatch.
 */
@Service
public class ReconciliationService {

    private static final String SIGNED_SUM =
            "COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount_kobo ELSE -e.amount_kobo END), 0)";

    private final JdbcTemplate jdbc;

    public ReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport check() {
        long total = one("SELECT COALESCE(SUM(balance_kobo), 0) FROM accounts");

        // 1. every account: cached balance == credits - debits
        List<AccountDrift> drift = jdbc.query("""
                SELECT a.account_number, a.account_type, u.username, a.balance_kobo, %s AS ledger_kobo
                FROM accounts a LEFT JOIN users u ON u.id = a.user_id
                LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY a.id, a.account_number, a.account_type, u.username, a.balance_kobo
                HAVING a.balance_kobo <> %s
                ORDER BY a.account_number LIMIT 50""".formatted(SIGNED_SUM, SIGNED_SUM),
                (rs, i) -> new AccountDrift(rs.getString("account_number"), rs.getString("account_type"),
                        rs.getString("username"), rs.getLong("balance_kobo"), rs.getLong("ledger_kobo")));

        // 2. every transaction: total debits == total credits
        List<UnbalancedTransaction> unbalanced = jdbc.query("""
                SELECT t.reference,
                       COALESCE(SUM(e.amount_kobo) FILTER (WHERE e.entry_type = 'DEBIT'), 0) AS debits,
                       COALESCE(SUM(e.amount_kobo) FILTER (WHERE e.entry_type = 'CREDIT'), 0) AS credits
                FROM transactions t JOIN ledger_entries e ON e.transaction_id = t.id
                GROUP BY t.id, t.reference
                HAVING COALESCE(SUM(e.amount_kobo) FILTER (WHERE e.entry_type = 'DEBIT'), 0)
                    <> COALESCE(SUM(e.amount_kobo) FILTER (WHERE e.entry_type = 'CREDIT'), 0)
                ORDER BY t.reference LIMIT 50""",
                (rs, i) -> new UnbalancedTransaction(rs.getString("reference"), rs.getLong("debits"), rs.getLong("credits")));

        // 3. a transaction that moved money must have entries
        long withoutEntries = one("""
                SELECT count(*) FROM transactions t
                WHERE NOT EXISTS (SELECT 1 FROM ledger_entries e WHERE e.transaction_id = t.id)""");

        // 4. money parked in SYSTEM_HOLD must equal the transfers still waiting for review
        long hold = one("SELECT COALESCE(SUM(balance_kobo), 0) FROM accounts WHERE account_type = 'SYSTEM_HOLD'");
        long flagged = one("SELECT COALESCE(SUM(amount_kobo), 0) FROM transactions WHERE status = 'FLAGGED'");
        boolean holdMatches = hold == flagged;

        // info only: a claimed idempotency key that never finished (a crashed request)
        long stale = one("SELECT count(*) FROM idempotency_keys WHERE status = 'IN_PROGRESS' AND created_at < now() - interval '5 minutes'");

        boolean ok = total == 0 && drift.isEmpty() && unbalanced.isEmpty() && withoutEntries == 0 && holdMatches;
        return new ReconciliationReport(ok, Instant.now(), total, drift, unbalanced, withoutEntries, hold, flagged,
                holdMatches, stale);
    }

    private long one(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
