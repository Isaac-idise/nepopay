package com.nepopay.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class PinChangeIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private ResultActions changePin(TestUser u, String current, String next) throws Exception {
        return mvc.perform(patch("/api/v1/users/me/pin").header("Authorization", u.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"%s\",\"newPin\":\"%s\"}".formatted(current, next)));
    }

    private ResultActions send(TestUser from, String to, String pin) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("Authorization", from.auth())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientUsername\":\"%s\",\"amountKobo\":100000,\"pin\":\"%s\"}".formatted(to, pin)));
    }

    @Test
    void changingThePinSwitchesWhichPinWorks() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        changePin(a, "1234", "5678").andExpect(status().isOk());

        send(a, b.username(), "1234").andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        send(a, b.username(), "5678").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/notifications").header("Authorization", a.auth()))
                .andExpect(jsonPath("$.data.content[?(@.type=='SECURITY')]").isNotEmpty());
        Integer audited = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users u ON u.id = l.user_id
                WHERE u.username = ? AND l.action = 'PIN_CHANGED'""", Integer.class, a.username());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void wrongCurrentPinCountsTowardsTheSameLockout() throws Exception {
        TestUser a = users.register();
        changePin(a, "0000", "5678").andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        changePin(a, "0000", "5678").andExpect(jsonPath("$.errorCode").value("INVALID_PIN"));
        changePin(a, "0000", "5678").andExpect(status().isLocked());

        // the PIN did not change, and while locked even the right current PIN is refused
        changePin(a, "1234", "5678").andExpect(status().isLocked());
    }

    @Test
    void invalidChangesAreRejected() throws Exception {
        TestUser a = users.register();
        changePin(a, "1234", "1234").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        changePin(a, "1234", "12ab").andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/users/me/pin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"1234\",\"newPin\":\"5678\"}"))
                .andExpect(status().isUnauthorized());
    }
}
