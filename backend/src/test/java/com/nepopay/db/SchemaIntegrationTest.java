package com.nepopay.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nepopay.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves the database itself enforces our financial rules, using a real PostgreSQL (needs Docker). */
class SchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID createUser(String username) {
        return jdbc.queryForObject("""
                INSERT INTO users (username, first_name, last_name, email, phone, password_hash, pin_hash)
                VALUES (?, 'Test', 'User', ?, ?, 'x', 'y') RETURNING id
                """, UUID.class, username, username + "@example.com", "080" + Math.abs(username.hashCode()));
    }

    private UUID createUserAccount(UUID userId, String number) {
        return jdbc.queryForObject("""
                INSERT INTO accounts (user_id, account_number, account_type, balance_kobo)
                VALUES (?, ?, 'USER', 0) RETURNING id
                """, UUID.class, userId, number);
    }

    private UUID systemAccount(String type) {
        return jdbc.queryForObject("SELECT id FROM accounts WHERE account_type = ?", UUID.class, type);
    }

    @Test
    void systemAccountsAreSeeded() {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM accounts WHERE account_type LIKE 'SYSTEM_%'", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void userAccountCannotBeOverdrawn() {
        UUID acc = createUserAccount(createUser("overdraw" + UUID.randomUUID().toString().substring(0, 6)), "99" + (long) (Math.random() * 1e8));
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance_kobo = -1 WHERE id = ?", acc))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void fundingAccountMayGoNegative() {
        UUID funding = systemAccount("SYSTEM_FUNDING");
        int rows = jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo - 1000000000000 WHERE id = ?", funding);
        assertThat(rows).isEqualTo(1);
        jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo + 1000000000000 WHERE id = ?", funding);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        String name = "case" + UUID.randomUUID().toString().substring(0, 6);
        createUser(name);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO users (username, first_name, last_name, email, phone, password_hash, pin_hash)
                VALUES (?, 'A', 'B', ?, ?, 'x', 'y')
                """, name.toUpperCase(), "other" + name + "@example.com", "081" + Math.abs(name.hashCode())))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ledgerEntriesAreAppendOnly() {
        UUID user = createUser("ledger" + UUID.randomUUID().toString().substring(0, 6));
        UUID acc = createUserAccount(user, "99" + (long) (Math.random() * 1e8));
        UUID funding = systemAccount("SYSTEM_FUNDING");

        UUID tx = jdbc.queryForObject("""
                INSERT INTO transactions (reference, sender_account_id, receiver_account_id, amount_kobo, type, status)
                VALUES (?, ?, ?, 10000000, 'DEMO_WELCOME_CREDIT', 'SUCCESSFUL') RETURNING id
                """, UUID.class, "NPY-T-" + UUID.randomUUID().toString().substring(0, 8), funding, acc);
        // keep the books balanced (both entries AND both cached balances) so other tests that audit the ledger stay valid
        jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo - 10000000 WHERE id = ?", funding);
        jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo + 10000000 WHERE id = ?", acc);
        Long fundingBalance = jdbc.queryForObject("SELECT balance_kobo FROM accounts WHERE id = ?", Long.class, funding);
        jdbc.update("""
                INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount_kobo, balance_after_kobo)
                VALUES (?, ?, 'DEBIT', 10000000, ?)""", tx, funding, fundingBalance);
        Long entryId = jdbc.queryForObject("""
                INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount_kobo, balance_after_kobo)
                VALUES (?, ?, 'CREDIT', 10000000, 10000000) RETURNING id
                """, Long.class, tx, acc);

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount_kobo = 1 WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE id = ?", entryId))
                .isInstanceOf(DataAccessException.class);
    }
}
