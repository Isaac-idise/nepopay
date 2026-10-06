package com.nepopay.transfer;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class TransferIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ResultActions send(TestUser from, String to, long kobo, String pin, String key) throws Exception {
        return mvc.perform(post("/api/v1/transfers")
                .header("Authorization", from.auth())
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientUsername\":\"%s\",\"amountKobo\":%d,\"description\":\"test\",\"pin\":\"%s\"}"
                        .formatted(to, kobo, pin)));
    }

    private long balance(TestUser u) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/me").header("Authorization", u.auth()))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.balanceKobo")).longValue();
    }

    @Test
    void successfulTransferMovesMoneyAndShowsUpEverywhere() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        String body = send(a, b.username(), 2_000_000, "1234", key())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESSFUL"))
                .andExpect(jsonPath("$.data.recipientUsername").value(b.username()))
                .andExpect(jsonPath("$.data.idempotentReplay").value(false))
                .andReturn().getResponse().getContentAsString();
        String ref = JsonPath.read(body, "$.data.reference");

        assertThat(balance(a)).isEqualTo(8_000_000);
        assertThat(balance(b)).isEqualTo(12_000_000);

        // Sender history: newest first (the transfer), then the welcome credit.
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].reference").value(ref))
                .andExpect(jsonPath("$.data.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.data.content[0].counterparty.username").value(b.username()))
                .andExpect(jsonPath("$.data.content[1].type").value("DEMO_WELCOME_CREDIT"))
                .andExpect(jsonPath("$.data.content[1].counterparty.displayName").value("NepOpay"));
        // Receiver history
        mvc.perform(get("/api/v1/transactions").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.content[0].reference").value(ref))
                .andExpect(jsonPath("$.data.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.data.content[0].counterparty.username").value(a.username()));

        // Detail shows ONLY the caller's own ledger entry.
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", a.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerEntries.length()").value(1))
                .andExpect(jsonPath("$.data.ledgerEntries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.data.ledgerEntries[0].amountKobo").value(2_000_000))
                .andExpect(jsonPath("$.data.ledgerEntries[0].balanceAfterKobo").value(8_000_000));
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.ledgerEntries[0].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.data.ledgerEntries[0].balanceAfterKobo").value(12_000_000));

        // A stranger cannot even tell that the transaction exists.
        TestUser stranger = users.register();
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", stranger.auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));

        // Notifications: welcome + transfer for each side.
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.unread").value(2));
        mvc.perform(get("/api/v1/notifications").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.content[0].type").value("TRANSFER_RECEIVED"))
                .andExpect(jsonPath("$.data.content[0].message", containsString(ref)));

        // Double-entry: debits equal credits for this transaction.
        Long debits = jdbc.queryForObject("SELECT SUM(amount_kobo) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id WHERE t.reference = ? AND e.entry_type = 'DEBIT'", Long.class, ref);
        Long credits = jdbc.queryForObject("SELECT SUM(amount_kobo) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id WHERE t.reference = ? AND e.entry_type = 'CREDIT'", Long.class, ref);
        assertThat(debits).isEqualTo(credits).isEqualTo(2_000_000L);
    }

    @Test
    void wrongPinIsCountedAndThreeFailuresLockTransfers() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        send(a, b.username(), 100_000, "0000", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PIN"))
                .andExpect(jsonPath("$.message", containsString("2 attempt(s) remaining")));
        send(a, b.username(), 100_000, "0000", key())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        send(a, b.username(), 100_000, "0000", key())
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("PIN_LOCKED"));
        // Even the CORRECT pin is refused while locked.
        send(a, b.username(), 100_000, "1234", key())
                .andExpect(status().isLocked());

        assertThat(balance(a)).isEqualTo(10_000_000);
        assertThat(balance(b)).isEqualTo(10_000_000);
        Integer audits = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users u ON u.id = l.user_id
                WHERE u.username = ? AND l.action IN ('PIN_FAILED', 'PIN_LOCKED')""", Integer.class, a.username());
        assertThat(audits).isEqualTo(4); // 3 failures + 1 lock event
    }

    @Test
    void correctPinResetsTheFailureCounter() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        send(a, b.username(), 100_000, "0000", key()).andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        send(a, b.username(), 100_000, "0000", key()).andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        send(a, b.username(), 100_000, "1234", key()).andExpect(status().isCreated());
        // Counter was reset, so this is failure number 1 again, not number 3.
        send(a, b.username(), 100_000, "0000", key())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PIN"))
                .andExpect(jsonPath("$.message", containsString("2 attempt(s) remaining")));
    }

    @Test
    void insufficientBalanceChangesNothingAndTheKeyCanBeReused() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String k = key();

        send(a, b.username(), 10_000_001, "1234", k)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.message", containsString("₦100,000.00")));
        assertThat(balance(a)).isEqualTo(10_000_000);
        assertThat(balance(b)).isEqualTo(10_000_000);

        // The failed attempt released its key, so a corrected request may reuse it.
        send(a, b.username(), 500_000, "1234", k).andExpect(status().isCreated());
    }

    @Test
    void recipientRules() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        send(a, "nobody" + TestUsers.unique(), 100_000, "1234", key())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RECIPIENT_UNAVAILABLE"));
        send(a, a.username(), 100_000, "1234", key())
                .andExpect(jsonPath("$.errorCode").value("SELF_TRANSFER"));
        send(a, "@" + b.username().toUpperCase(), 100_000, "1234", key())   // '@' and any case are fine
                .andExpect(status().isCreated());

        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", b.username());
        send(a, b.username(), 100_000, "1234", key())
                .andExpect(jsonPath("$.errorCode").value("RECIPIENT_UNAVAILABLE"));
    }

    @Test
    void invalidAmountsAndLimits() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        send(a, b.username(), 0, "1234", key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        send(a, b.username(), -500, "1234", key()).andExpect(status().isBadRequest());
        send(a, b.username(), 50, "1234", key()).andExpect(status().isBadRequest())   // below N1 minimum
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        send(a, b.username(), 60_000_000, "1234", key()).andExpect(status().isUnprocessableEntity())  // N600,000
                .andExpect(jsonPath("$.errorCode").value("LIMIT_EXCEEDED"));
        assertThat(balance(a)).isEqualTo(10_000_000);
    }

    @Test
    void transferRequiresLoginAndAnIdempotencyKey() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"x\",\"amountKobo\":100,\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/transfers").header("Authorization", a.auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"%s\",\"amountKobo\":100000,\"pin\":\"1234\"}".formatted(b.username())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));

        send(a, b.username(), 100_000, "1234", "short")
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameKeySameRequestMovesMoneyOnlyOnce() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String k = key();

        String first = send(a, b.username(), 2_000_000, "1234", k).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = send(a, b.username(), 2_000_000, "1234", k).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotentReplay").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(second, "$.data.reference")).isEqualTo(JsonPath.<String>read(first, "$.data.reference"));
        assertThat(balance(a)).isEqualTo(8_000_000);
        assertThat(balance(b)).isEqualTo(12_000_000);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String k = key();

        send(a, b.username(), 2_000_000, "1234", k).andExpect(status().isCreated());
        send(a, b.username(), 3_000_000, "1234", k)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));
        assertThat(balance(a)).isEqualTo(8_000_000);
    }

    @Test
    void tenSimultaneousIdenticalRequestsMoveMoneyExactlyOnce() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String k = key();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return send(a, b.username(), 1_000_000, "1234", k).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            int s = f.get(30, TimeUnit.SECONDS);
            assertThat(s).isIn(201, 200, 409); // new, replay, or "still in progress"
            if (s == 201) {
                created++;
            }
        }
        pool.shutdown();

        assertThat(created).isEqualTo(1);
        assertThat(balance(a)).isEqualTo(9_000_000);
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM transactions t JOIN accounts s ON s.id = t.sender_account_id
                JOIN users u ON u.id = s.user_id WHERE u.username = ? AND t.type = 'P2P_TRANSFER'""",
                Integer.class, a.username());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void responsesNeverLeakPrivateDetailsOfTheOtherPerson() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        String transfer = send(a, b.username(), 100_000, "1234", key()).andReturn().getResponse().getContentAsString();
        String history = mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andReturn().getResponse().getContentAsString();
        for (String body : List.of(transfer, history)) {
            assertThat(body).doesNotContain("@example.com").doesNotContain("\"phone\"")
                    .doesNotContain("\"email\"").doesNotContain("accountNumber").doesNotContain("riskScore");
        }
    }
}
