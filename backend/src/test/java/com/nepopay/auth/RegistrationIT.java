package com.nepopay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class RegistrationIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static String unique() {
        return Long.toString(System.nanoTime(), 36);
    }

    private static String phone() {
        return "080" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private ResultActions register(String username, String email, String phone, String password, String pin) throws Exception {
        String body = """
                {"firstName":"Ada","lastName":"Obi","email":"%s","phone":"%s",
                 "username":"%s","password":"%s","pin":"%s"}
                """.formatted(email, phone, username, password, pin);
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registrationCreatesFundedAccountWithLedgerAndNotification() throws Exception {
        String u = "u" + unique();
        String content = register(u, u + "@example.com", phone(), "Passw0rdTest", "1234")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.balanceKobo").value(10_000_000))
                .andExpect(jsonPath("$.data.currency").value("NGN"))
                .andExpect(jsonPath("$.data.accountNumber", startsWith("99")))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String ref = JsonPath.read(content, "$.data.welcomeTransactionReference");

        // Password is stored as a BCrypt hash, never as plaintext.
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, u);
        assertThat(hash).startsWith("$2").doesNotContain("Passw0rdTest");

        // The 100k is a real transaction with a DEBIT on the funding account and a CREDIT on the user.
        assertThat(jdbc.queryForObject("SELECT type FROM transactions WHERE reference = ?", String.class, ref))
                .isEqualTo("DEMO_WELCOME_CREDIT");
        assertThat(count("""
                SELECT count(*) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                JOIN accounts a ON a.id = e.account_id
                WHERE t.reference = ? AND e.entry_type = 'DEBIT' AND a.account_type = 'SYSTEM_FUNDING'""", ref)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM ledger_entries e JOIN transactions t ON t.id = e.transaction_id
                JOIN accounts a ON a.id = e.account_id
                WHERE t.reference = ? AND e.entry_type = 'CREDIT' AND a.account_type = 'USER'""", ref)).isEqualTo(1);

        assertThat(count("""
                SELECT count(*) FROM notifications n JOIN users u ON u.id = n.user_id
                WHERE u.username = ? AND n.type = 'WELCOME'""", u)).isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM audit_logs l JOIN users u ON u.id = l.user_id
                WHERE u.username = ? AND l.action = 'REGISTER'""", u)).isEqualTo(1);
    }

    @Test
    void duplicateUsernameIgnoringCaseIsRejected() throws Exception {
        String u = "u" + unique();
        register(u, u + "@example.com", phone(), "Passw0rdTest", "1234").andExpect(status().isCreated());
        register(u.toUpperCase(), "x" + u + "@example.com", phone(), "Passw0rdTest", "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
    }

    @Test
    void reservedUsernameIsRejected() throws Exception {
        register("admin", "admin" + unique() + "@example.com", phone(), "Passw0rdTest", "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String u = "u" + unique();
        register(u, u + "@example.com", phone(), "Passw0rdTest", "1234").andExpect(status().isCreated());
        register("v" + unique(), u + "@EXAMPLE.com", phone(), "Passw0rdTest", "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
    }

    @Test
    void samePhoneInPlus234FormIsADuplicate() throws Exception {
        String u = "u" + unique();
        String local = phone();
        register(u, u + "@example.com", local, "Passw0rdTest", "1234").andExpect(status().isCreated());
        String intl = "+234" + local.substring(1);
        register("v" + unique(), "v" + u + "@example.com", intl, "Passw0rdTest", "1234")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PHONE_TAKEN"));
    }

    @Test
    void weakPasswordAndBadPinAreRejected() throws Exception {
        String u = "u" + unique();
        register(u, u + "@example.com", phone(), "short", "1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        register(u, u + "@example.com", phone(), "Passw0rdTest", "12ab")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    private int count(String sql, Object arg) {
        Integer n = jdbc.queryForObject(sql, Integer.class, arg);
        return n == null ? 0 : n;
    }
}
