package com.nepopay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class AdminIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private ResultActions getAs(TestUser who, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", who.auth()));
    }

    private ResultActions rawSend(TestUser from, String to, long kobo, String pin) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("Authorization", from.auth())
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientUsername\":\"%s\",\"amountKobo\":%d,\"pin\":\"%s\"}".formatted(to, kobo, pin)));
    }

    private static long num(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    // ---------------- access control ----------------

    @Test
    void everyAdminEndpointRefusesAnonymousAndOrdinaryUsers() throws Exception {
        TestUser user = users.register();
        TestUser admin = users.admin(jdbc);
        for (String url : List.of("/api/v1/admin/dashboard", "/api/v1/admin/users", "/api/v1/admin/transactions",
                "/api/v1/admin/audit-logs", "/api/v1/admin/ledger/reconciliation", "/api/v1/admin/fraud-alerts")) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            getAs(user, url).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
            getAs(admin, url).andExpect(status().isOk());
        }
    }

    // ---------------- dashboard ----------------

    @Test
    void dashboardNumbersMatchTheDatabase() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser a = users.register();
        TestUser b = users.register();
        TestUser c = users.register();
        users.send(a, b.username(), 2_000_000);                                  // delivered
        rawSend(a, c.username(), 6_000_000, "1234").andExpect(status().isAccepted()); // held for review
        rawSend(a, c.username(), 100_000_000, "1234");                           // fails (over the limit): counted as a failed attempt

        String body = getAs(admin, "/api/v1/admin/dashboard").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(num(body, "$.data.users.total")).isEqualTo(count("SELECT count(*) FROM users"));
        assertThat(num(body, "$.data.users.admins")).isEqualTo(count("SELECT count(*) FROM users WHERE role = 'ADMIN'"));
        assertThat(num(body, "$.data.users.registeredToday")).isGreaterThanOrEqualTo(4);
        assertThat(num(body, "$.data.transfers.successful"))
                .isEqualTo(count("SELECT count(*) FROM transactions WHERE type='P2P_TRANSFER' AND status='SUCCESSFUL'"));
        assertThat(num(body, "$.data.transfers.flagged"))
                .isEqualTo(count("SELECT count(*) FROM transactions WHERE type='P2P_TRANSFER' AND status='FLAGGED'"));
        assertThat(num(body, "$.data.transfers.failedAttemptsAllTime"))
                .isEqualTo(count("SELECT count(*) FROM audit_logs WHERE action='TRANSFER_FAILED'"));
        assertThat(num(body, "$.data.transfers.failedAttemptsToday")).isGreaterThanOrEqualTo(1);
        assertThat(num(body, "$.data.volume.allTimeKobo"))
                .isEqualTo(count("SELECT COALESCE(SUM(amount_kobo),0) FROM transactions WHERE type='P2P_TRANSFER' AND status='SUCCESSFUL'"));
        assertThat(num(body, "$.data.volume.todayKobo")).isGreaterThanOrEqualTo(2_000_000);
        assertThat(num(body, "$.data.fraud.open")).isEqualTo(count("SELECT count(*) FROM fraud_alerts WHERE status='OPEN'"));
        assertThat(num(body, "$.data.fraud.heldKobo"))
                .isEqualTo(count("SELECT balance_kobo FROM accounts WHERE account_type='SYSTEM_HOLD'"));
        assertThat((List<?>) JsonPath.read(body, "$.data.recentAudit")).hasSize(10);
        assertThat(body).doesNotContain("password");
    }

    // ---------------- users ----------------

    @Test
    void adminCanFindUsersAndSeesTheirPrivateDetails() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser u = users.register("fnd" + TestUsers.unique(), "Findable");

        getAs(admin, "/api/v1/admin/users?q=" + u.username()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].email").value(u.username() + "@example.com"))
                .andExpect(jsonPath("$.data.content[0].accountNumber", startsWith("99")))
                .andExpect(jsonPath("$.data.content[0].balanceKobo").value(10_000_000))
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist());
        getAs(admin, "/api/v1/admin/users/" + u.username()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        getAs(admin, "/api/v1/admin/users/nobody" + TestUsers.unique()).andExpect(status().isNotFound());

        // an admin has no bank account
        getAs(admin, "/api/v1/admin/users/" + admin.username())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.accountNumber").doesNotExist());

        getAs(admin, "/api/v1/admin/users?status=BOGUS").andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE username = ?", u.username());
        getAs(admin, "/api/v1/admin/users?status=SUSPENDED&q=" + u.username())
                .andExpect(jsonPath("$.data.content.length()").value(1));
        getAs(admin, "/api/v1/admin/users?status=ACTIVE&q=" + u.username())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    private ResultActions setStatus(TestUser admin, String username, String body) throws Exception {
        return mvc.perform(patch("/api/v1/admin/users/" + username + "/status").header("Authorization", admin.auth())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void suspendingAUserBlocksLoginRefreshAndMoneyMovementThenReactivationRestoresThem() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser victim = users.register();
        TestUser friend = users.register();
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"Passw0rdTest\"}".formatted(victim.username()))).andReturn();
        Cookie refresh = login.getResponse().getCookie("nepopay_refresh");

        setStatus(admin, victim.username(), "{\"status\":\"SUSPENDED\"}").andExpect(status().isBadRequest()); // reason required
        setStatus(admin, victim.username(), "{\"status\":\"SUSPENDED\",\"reason\":\"Reported by a user\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Passw0rdTest\"}".formatted(victim.username())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        // even with a still-valid access token, money cannot move
        rawSend(victim, friend.username(), 100_000, "1234")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCOUNT_SUSPENDED"));
        // and nobody can send to a suspended user
        rawSend(friend, victim.username(), 100_000, "1234")
                .andExpect(jsonPath("$.errorCode").value("RECIPIENT_UNAVAILABLE"));

        setStatus(admin, victim.username(), "{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        users.login(victim.username());                                         // can log in again

        Integer audited = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE entity_id = ? AND action IN ('USER_SUSPENDED','USER_REACTIVATED')""",
                Integer.class, victim.username());
        assertThat(audited).isEqualTo(2);
    }

    @Test
    void adminAccountsCannotBeSuspendedThroughTheApi() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser other = users.admin(jdbc);
        setStatus(admin, other.username(), "{\"status\":\"SUSPENDED\",\"reason\":\"test\"}")
                .andExpect(status().isForbidden());
        setStatus(admin, "nobody" + TestUsers.unique(), "{\"status\":\"ACTIVE\"}").andExpect(status().isNotFound());
    }

    // ---------------- transaction monitoring ----------------

    @Test
    void transactionsCanBeFilteredAndInspectedWithTheirLedgerEntries() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser a = users.register();
        TestUser b = users.register();
        TestUser c = users.register();
        String small = users.send(a, b.username(), 500_000);
        String heldRef = JsonPath.read(rawSend(a, c.username(), 6_000_000, "1234").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString(), "$.data.reference");
        String base = "/api/v1/admin/transactions?user=" + a.username();
        String today = LocalDate.now(ZoneId.of("Africa/Lagos")).toString();

        getAs(admin, base).andExpect(jsonPath("$.data.totalElements").value(3));                  // welcome + 2 sends
        getAs(admin, base + "&status=FLAGGED").andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reference").value(heldRef));
        getAs(admin, base + "&type=P2P_TRANSFER").andExpect(jsonPath("$.data.totalElements").value(2));
        getAs(admin, base + "&type=DEMO_WELCOME_CREDIT").andExpect(jsonPath("$.data.content[0].sender").value("SYSTEM_FUNDING"));
        getAs(admin, base + "&minKobo=1000000").andExpect(jsonPath("$.data.totalElements").value(2));
        getAs(admin, base + "&maxKobo=1000000").andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reference").value(small));
        getAs(admin, base + "&from=" + today + "&to=" + today).andExpect(jsonPath("$.data.totalElements").value(3));
        getAs(admin, base + "&from=2999-01-01").andExpect(jsonPath("$.data.totalElements").value(0));
        getAs(admin, base + "&status=NOPE").andExpect(status().isBadRequest());
        getAs(admin, base + "&from=yesterday").andExpect(status().isBadRequest());
        getAs(admin, base + "&size=5000").andExpect(jsonPath("$.data.size").value(50));

        getAs(admin, "/api/v1/admin/transactions/" + heldRef).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaction.sender").value(a.username()))
                .andExpect(jsonPath("$.data.transaction.receiver").value(c.username()))
                .andExpect(jsonPath("$.data.transaction.riskScore").value(60))
                .andExpect(jsonPath("$.data.ledgerEntries.length()").value(2))
                .andExpect(jsonPath("$.data.ledgerEntries[?(@.account=='SYSTEM_HOLD')].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.data.ledgerEntries[?(@.account=='" + a.username() + "')].entryType").value("DEBIT"));
        getAs(admin, "/api/v1/admin/transactions/NPY-NOPE").andExpect(status().isNotFound());
    }

    // ---------------- audit logs ----------------

    @Test
    void auditLogsAreSearchableAndShowStructuredDetails() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser a = users.register();
        TestUser b = users.register();
        users.send(a, b.username(), 500_000);
        rawSend(a, b.username(), 100_000, "0000");                               // wrong PIN

        getAs(admin, "/api/v1/admin/audit-logs?action=TRANSFER&username=" + a.username())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].metadata.amountKobo").value(500_000))
                .andExpect(jsonPath("$.data.content[0].metadata.recipient").value(b.username()));
        getAs(admin, "/api/v1/admin/audit-logs?action=pin_failed&username=" + a.username())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        getAs(admin, "/api/v1/admin/audit-logs?username=" + a.username() + "&from=2999-01-01")
                .andExpect(jsonPath("$.data.totalElements").value(0));
        getAs(admin, "/api/v1/admin/audit-logs?from=not-a-date").andExpect(status().isBadRequest());
    }

    // ---------------- reconciliation ----------------

    @Test
    void healthyBooksReconcile() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser a = users.register();
        TestUser b = users.register();
        users.send(a, b.username(), 500_000);
        rawSend(a, b.username(), 6_000_000, "1234").andExpect(status().isAccepted());   // money in SYSTEM_HOLD

        getAs(admin, "/api/v1/admin/ledger/reconciliation").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.totalBalanceKobo").value(0))
                .andExpect(jsonPath("$.data.accountDrift.length()").value(0))
                .andExpect(jsonPath("$.data.unbalancedTransactions.length()").value(0))
                .andExpect(jsonPath("$.data.transactionsWithoutEntries").value(0))
                .andExpect(jsonPath("$.data.holdMatchesFlagged").value(true));
    }

    @Test
    void reconciliationCatchesATamperedBalance() throws Exception {
        TestUser admin = users.admin(jdbc);
        TestUser victim = users.register();
        String number = jdbc.queryForObject("""
                SELECT a.account_number FROM accounts a JOIN users u ON u.id = a.user_id WHERE u.username = ?""",
                String.class, victim.username());

        jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo + 1 WHERE account_number = ?", number); // someone cheats by 1 kobo
        try {
            getAs(admin, "/api/v1/admin/ledger/reconciliation").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ok").value(false))
                    .andExpect(jsonPath("$.data.totalBalanceKobo").value(1))
                    .andExpect(jsonPath("$.data.accountDrift[?(@.accountNumber=='" + number + "')].balanceKobo").value(10_000_001))
                    .andExpect(jsonPath("$.data.accountDrift[?(@.accountNumber=='" + number + "')].ledgerKobo").value(10_000_000));
        } finally {
            jdbc.update("UPDATE accounts SET balance_kobo = balance_kobo - 1 WHERE account_number = ?", number);
        }
        getAs(admin, "/api/v1/admin/ledger/reconciliation").andExpect(jsonPath("$.data.ok").value(true));
    }
}
