package com.nepopay.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class DemoResetIT extends AbstractIntegrationTest {

    private static final long START = 10_000_000L;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private ResultActions reset(TestUser u, String pin) throws Exception {
        return mvc.perform(post("/api/v1/demo/reset").header("Authorization", u.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"%s\"}".formatted(pin)));
    }

    private long ledgerNet(TestUser u) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN e.entry_type = 'CREDIT' THEN e.amount_kobo ELSE -e.amount_kobo END), 0)
                FROM ledger_entries e JOIN accounts a ON a.id = e.account_id JOIN users x ON x.id = a.user_id
                WHERE x.username = ?""", Long.class, u.username());
    }

    @Test
    void resettingAnAccountWithExtraMoneyReturnsItAndHidesOnlyItsOwnHistory() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        String ref = users.send(a, b.username(), 3_000_000);            // a: 70,000   b: 130,000
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", b.auth())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\"}".formatted(a.username())));

        String body = reset(b, "1234").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceKobo").value(START))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.data.adjustmentReference")).isNotBlank();

        assertThat(users.balance(b)).isEqualTo(START);
        assertThat(ledgerNet(b)).isEqualTo(START);                       // ledger still agrees with the balance

        // b: history shows only the reset; the old transfer is gone; beneficiaries and old notifications cleared
        mvc.perform(get("/api/v1/transactions").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("DEMO_RESET"))
                .andExpect(jsonPath("$.data.content[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.data.content[0].counterparty.displayName").value("NepOpay"));
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", b.auth()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/beneficiaries").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/notifications").header("Authorization", b.auth()))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("SYSTEM"));

        // a: nothing changed (balance, history, ability to open the transfer)
        assertThat(users.balance(a)).isEqualTo(7_000_000);
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/transactions/" + ref).header("Authorization", a.auth()))
                .andExpect(status().isOk());
    }

    @Test
    void resettingAnAccountBelowTheStartTopsItUp() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        users.send(a, b.username(), 3_000_000);                          // a: 70,000

        reset(a, "1234").andExpect(status().isOk());

        assertThat(users.balance(a)).isEqualTo(START);
        assertThat(ledgerNet(a)).isEqualTo(START);
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].direction").value("CREDIT"));
        assertThat(users.balance(b)).isEqualTo(13_000_000);              // b is untouched
    }

    @Test
    void resettingAnUntouchedAccountMovesNoMoney() throws Exception {
        TestUser a = users.register();
        reset(a, "1234").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adjustmentReference").doesNotExist());
        assertThat(users.balance(a)).isEqualTo(START);
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void aWrongPinDoesNotResetAnything() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        users.send(a, b.username(), 3_000_000);

        reset(a, "0000").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        assertThat(users.balance(a)).isEqualTo(7_000_000);
        mvc.perform(get("/api/v1/transactions").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void resetRequiresLogin() throws Exception {
        mvc.perform(post("/api/v1/demo/reset").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }
}
