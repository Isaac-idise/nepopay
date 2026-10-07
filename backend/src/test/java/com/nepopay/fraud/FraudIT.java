package com.nepopay.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class FraudIT extends AbstractIntegrationTest {

    private static final long START = 10_000_000L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private ResultActions send(TestUser from, String to, long kobo, String pin, String key) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("Authorization", from.auth())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientUsername\":\"%s\",\"amountKobo\":%d,\"pin\":\"%s\"}".formatted(to, kobo, pin)));
    }

    private ResultActions send(TestUser from, String to, long kobo, String pin) throws Exception {
        return send(from, to, kobo, pin, UUID.randomUUID().toString());
    }

    private static String ref(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.reference");
    }

    private long holdBalance() {
        return jdbc.queryForObject("SELECT balance_kobo FROM accounts WHERE account_type = 'SYSTEM_HOLD'", Long.class);
    }

    private int score(String reference) {
        return jdbc.queryForObject("SELECT risk_score FROM transactions WHERE reference = ?", Integer.class, reference);
    }

    private String txStatus(String reference) {
        return jdbc.queryForObject("SELECT status FROM transactions WHERE reference = ?", String.class, reference);
    }

    private String alertValue(String column, String reference) {
        return jdbc.queryForObject("SELECT cast(f." + column + " as text) FROM fraud_alerts f JOIN transactions t "
                + "ON t.id = f.transaction_id WHERE t.reference = ?", String.class, reference);
    }

    private int alertCount(String reference) {
        return jdbc.queryForObject("SELECT count(*) FROM fraud_alerts f JOIN transactions t ON t.id = f.transaction_id "
                + "WHERE t.reference = ?", Integer.class, reference);
    }

    private long ledgerNet(TestUser u) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount_kobo ELSE -e.amount_kobo END), 0)
                FROM ledger_entries e JOIN accounts a ON a.id = e.account_id JOIN users x ON x.id = a.user_id
                WHERE x.username = ?""", Long.class, u.username());
    }

    private TestUser admin() throws Exception {
        TestUser u = users.register();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", u.username());
        return users.login(u.username()); // new login so the token carries the ADMIN role
    }

    // ---------------- scoring ladder ----------------

    @Test
    void smallTransferToANewFriendIsLowRiskAndLeavesNoAlert() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String ref = ref(send(a, b.username(), 500_000, "1234").andExpect(status().isCreated()));
        assertThat(score(ref)).isEqualTo(20);                    // NEW_RECIPIENT only
        assertThat(alertCount(ref)).isZero();
    }

    @Test
    void mediumRiskTransferGoesThroughButIsLogged() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String ref = ref(send(a, b.username(), 2_000_000, "1234").andExpect(status().isCreated()));

        assertThat(score(ref)).isEqualTo(30);                    // NEW_RECIPIENT 20 + NEW_ACCOUNT_LARGE_TRANSFER 10
        assertThat(alertValue("risk_level", ref)).isEqualTo("MEDIUM");
        assertThat(alertValue("status", ref)).isEqualTo("INFO");
        assertThat(alertValue("reasons", ref)).contains("NEW_RECIPIENT").contains("NEW_ACCOUNT_LARGE_TRANSFER");
        assertThat(users.balance(b)).isEqualTo(START + 2_000_000);
    }

    @Test
    void beneficiaryRemovesTheNewRecipientPoints() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", a.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\"}".formatted(b.username())))
                .andExpect(status().isCreated());
        String ref = ref(send(a, b.username(), 500_000, "1234").andExpect(status().isCreated()));
        assertThat(score(ref)).isZero();
    }

    @Test
    void rapidTransfersAddPointsOnTheFifth() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        int[] scores = new int[5];
        for (int i = 0; i < 5; i++) {
            scores[i] = score(ref(send(a, b.username(), 100_000, "1234").andExpect(status().isCreated())));
        }
        assertThat(scores).containsExactly(20, 0, 0, 0, 20);     // first: new recipient; fifth: rapid transfers
    }

    @Test
    void allRulesTogetherReachCritical() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        send(a, b.username(), 100, "0000").andExpect(jsonPath("$.errorCode").value("INVALID_PIN")); // leaves PIN_FAILED evidence

        String ref = ref(send(a, b.username(), 9_500_000, "1234").andExpect(status().isAccepted()));
        // HIGH_AMOUNT 30 + NEW_RECIPIENT 20 + NEW_ACCOUNT 10 + DRAINS 10 + PIN_FAILURES 20
        assertThat(score(ref)).isEqualTo(90);
        assertThat(alertValue("risk_level", ref)).isEqualTo("CRITICAL");
        assertThat(alertValue("reasons", ref)).contains("RECENT_PIN_FAILURES").contains("DRAINS_BALANCE");
    }

    // ---------------- held funds ----------------

    @Test
    void highRiskTransferIsHeldNotDelivered() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        long holdBefore = holdBalance();

        String key = UUID.randomUUID().toString();
        String body = send(a, b.username(), 6_000_000, "1234", key)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("FLAGGED"))
                .andExpect(jsonPath("$.data.message", containsString("review")))
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(body, "$.data.reference");
        assertThat(body).doesNotContain("riskScore").doesNotContain("risk");   // the customer never sees the score

        assertThat(score(ref)).isEqualTo(60);
        assertThat(alertValue("status", ref)).isEqualTo("OPEN");
        assertThat(alertValue("risk_level", ref)).isEqualTo("HIGH");

        // sender is debited, the money sits in SYSTEM_HOLD, the receiver has NOT been paid
        assertThat(users.balance(a)).isEqualTo(START - 6_000_000);
        assertThat(users.balance(b)).isEqualTo(START);
        assertThat(holdBalance()).isEqualTo(holdBefore + 6_000_000);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                JOIN accounts a ON a.id = e.account_id
                WHERE t.reference = ? AND e.entry_type = 'CREDIT' AND a.account_type = 'SYSTEM_HOLD'""",
                Integer.class, ref)).isEqualTo(1);

        // sender sees it as FLAGGED; receiver sees nothing yet (history and notifications)
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.status").value("FLAGGED"));
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", b.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/transactions").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.unread").value(1));
        mvc.perform(get("/api/v1/notifications").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.content[?(@.type=='FRAUD_ALERT')]").isNotEmpty());

        // pressing Send again with the same key just returns the same held transfer
        send(a, b.username(), 6_000_000, "1234", key)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FLAGGED"))
                .andExpect(jsonPath("$.data.idempotentReplay").value(true));
        assertThat(users.balance(a)).isEqualTo(START - 6_000_000);
    }

    @Test
    void anHeldTransferStillCannotOverspend() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        send(a, b.username(), 10_000_001, "1234")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"));
        assertThat(users.balance(a)).isEqualTo(START);
    }

    // ---------------- admin review ----------------

    @Test
    void adminApprovalReleasesTheMoneyToTheReceiver() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        TestUser admin = admin();
        long holdBefore = holdBalance();
        String ref = ref(send(a, b.username(), 6_000_000, "1234").andExpect(status().isAccepted()));
        String alertId = alertValue("id", ref);

        // the admin finds it in the OPEN queue and can read WHY it was held
        mvc.perform(get("/api/v1/admin/fraud-alerts").param("status", "OPEN").header("Authorization", admin.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.transaction.reference=='" + ref + "')]").isNotEmpty());
        mvc.perform(get("/api/v1/admin/fraud-alerts/" + alertId).header("Authorization", admin.auth()))
                .andExpect(jsonPath("$.data.riskScore").value(60))
                .andExpect(jsonPath("$.data.reasons.length()").value(3))
                .andExpect(jsonPath("$.data.transaction.sender.username").value(a.username()));

        mvc.perform(post("/api/v1/admin/fraud-alerts/" + alertId + "/approve").header("Authorization", admin.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Looks fine\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(txStatus(ref)).isEqualTo("SUCCESSFUL");
        assertThat(users.balance(a)).isEqualTo(START - 6_000_000);
        assertThat(users.balance(b)).isEqualTo(START + 6_000_000);
        assertThat(holdBalance()).isEqualTo(holdBefore);                     // the hold is empty again
        assertThat(ledgerNet(a)).isEqualTo(users.balance(a));
        assertThat(ledgerNet(b)).isEqualTo(users.balance(b));

        // four balanced entries on the one transaction: A->HOLD, then HOLD->B
        Long debits = jdbc.queryForObject("SELECT SUM(e.amount_kobo) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id WHERE t.reference = ? AND e.entry_type = 'DEBIT'", Long.class, ref);
        Long credits = jdbc.queryForObject("SELECT SUM(e.amount_kobo) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id WHERE t.reference = ? AND e.entry_type = 'CREDIT'", Long.class, ref);
        assertThat(debits).isEqualTo(credits).isEqualTo(12_000_000L);

        // the receiver now sees it, and was notified
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", b.auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.direction").value("CREDIT"));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.unread").value(2));

        // a decided alert cannot be decided again
        mvc.perform(post("/api/v1/admin/fraud-alerts/" + alertId + "/reject").header("Authorization", admin.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"changed my mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALERT_ALREADY_REVIEWED"));

        Integer audited = jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'FRAUD_APPROVED' AND entity_id = ?", Integer.class, alertId);
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void adminRejectionReturnsTheMoneyToTheSender() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        TestUser admin = admin();
        long holdBefore = holdBalance();
        String ref = ref(send(a, b.username(), 6_000_000, "1234").andExpect(status().isAccepted()));
        String alertId = alertValue("id", ref);

        // a reason is mandatory when rejecting
        mvc.perform(post("/api/v1/admin/fraud-alerts/" + alertId + "/reject").header("Authorization", admin.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/admin/fraud-alerts/" + alertId + "/reject").header("Authorization", admin.auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Suspicious pattern\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(txStatus(ref)).isEqualTo("REVERSED");
        assertThat(users.balance(a)).isEqualTo(START);                       // fully refunded
        assertThat(users.balance(b)).isEqualTo(START);                       // never paid
        assertThat(holdBalance()).isEqualTo(holdBefore);
        assertThat(ledgerNet(a)).isEqualTo(START);

        // history is not rewritten: original (REVERSED) plus a linked REVERSAL transaction
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[?(@.type=='REVERSAL')]").isNotEmpty())
                .andExpect(jsonPath("$.data.content[?(@.status=='REVERSED')]").isNotEmpty());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM transactions r JOIN transactions o ON o.id = r.related_transaction_id
                WHERE r.type = 'REVERSAL' AND o.reference = ?""", Integer.class, ref)).isEqualTo(1);
        mvc.perform(get("/api/v1/transactions").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(1));       // receiver never saw anything
        mvc.perform(get("/api/v1/notifications").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.content[0].title").value("Transfer declined"));
    }

    @Test
    void onlyAdminsCanReachTheReviewEndpoints() throws Exception {
        TestUser a = users.register();
        mvc.perform(get("/api/v1/admin/fraud-alerts").header("Authorization", a.auth()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mvc.perform(post("/api/v1/admin/fraud-alerts/" + UUID.randomUUID() + "/approve").header("Authorization", a.auth()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/fraud-alerts")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownAlertAndBadStatusFilterAreHandled() throws Exception {
        TestUser admin = admin();
        mvc.perform(get("/api/v1/admin/fraud-alerts/" + UUID.randomUUID()).header("Authorization", admin.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/admin/fraud-alerts").param("status", "BOGUS").header("Authorization", admin.auth()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/fraud-alerts").param("status", "ALL").header("Authorization", admin.auth()))
                .andExpect(status().isOk());
    }
}
