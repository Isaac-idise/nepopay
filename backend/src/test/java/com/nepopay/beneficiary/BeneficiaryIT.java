package com.nepopay.beneficiary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class BeneficiaryIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private ResultActions add(TestUser who, String username, String nickname) throws Exception {
        String body = nickname == null
                ? "{\"username\":\"%s\"}".formatted(username)
                : "{\"username\":\"%s\",\"nickname\":\"%s\"}".formatted(username, nickname);
        return mvc.perform(post("/api/v1/beneficiaries").header("Authorization", who.auth())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions list(TestUser who) throws Exception {
        return mvc.perform(get("/api/v1/beneficiaries").header("Authorization", who.auth()));
    }

    @Test
    void addListAndRemove() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register("bn" + TestUsers.unique(), "Benny");

        add(a, "@" + b.username().toUpperCase(), "Bestie")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value(b.username()))
                .andExpect(jsonPath("$.data.nickname").value("Bestie"))
                .andExpect(jsonPath("$.data.displayName").value("Benny Tester"));

        String body = list(a).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("email").doesNotContain("phone").doesNotContain("accountNumber")
                .doesNotContain("@example.com").contains("accountLast4");

        add(a, b.username(), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BENEFICIARY_EXISTS"));

        // another user's list is separate
        list(b).andExpect(jsonPath("$.data.length()").value(0));

        mvc.perform(delete("/api/v1/beneficiaries/" + b.username()).header("Authorization", a.auth()))
                .andExpect(status().isOk());
        list(a).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/v1/beneficiaries/" + b.username()).header("Authorization", a.auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void cannotAddYourselfAnUnknownUserOrASuspendedUser() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();

        add(a, a.username(), null).andExpect(jsonPath("$.errorCode").value("SELF_TRANSFER"));
        add(a, "nobody" + TestUsers.unique(), null).andExpect(jsonPath("$.errorCode").value("RECIPIENT_UNAVAILABLE"));

        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", b.username());
        add(a, b.username(), null).andExpect(jsonPath("$.errorCode").value("RECIPIENT_UNAVAILABLE"));
    }

    @Test
    void aBeneficiaryCanBePaidByUsername() throws Exception {
        TestUser a = users.register();
        TestUser b = users.register();
        add(a, b.username(), "Mum").andExpect(status().isCreated());

        users.send(a, b.username(), 500_000);
        assertThat(users.balance(b)).isEqualTo(10_500_000);
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/beneficiaries")).andExpect(status().isUnauthorized());
    }
}
