package com.nepopay.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UserSearchIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    TestUsers users;

    @BeforeEach
    void setUp() {
        users = new TestUsers(mvc);
    }

    private String search(TestUser who, String q) throws Exception {
        return mvc.perform(get("/api/v1/users/search").param("q", q).header("Authorization", who.auth()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void findsUsersByUsernamePrefixAndByFirstName() throws Exception {
        String s = TestUsers.unique();
        TestUser me = users.register();
        TestUser target = users.register("srch" + s, "Zebedee" + s);

        assertThat(search(me, "srch" + s)).contains("\"username\":\"srch" + s + "\"");
        assertThat(search(me, "@SRCH" + s)).contains("srch" + s);                 // '@' and case are ignored
        assertThat(search(me, "zebedee" + s)).contains("\"username\":\"" + target.username() + "\"");
    }

    @Test
    void resultsContainOnlySafePublicFields() throws Exception {
        String s = TestUsers.unique();
        TestUser me = users.register();
        users.register("safe" + s, "Safe" + s);

        String body = search(me, "safe" + s);
        assertThat(body).contains("accountLast4").contains("displayName");
        assertThat(body).doesNotContain("email").doesNotContain("phone").doesNotContain("password")
                .doesNotContain("pin").doesNotContain("accountNumber").doesNotContain("@example.com");

        String last4 = JsonPath.read(body, "$.data.content[0].accountLast4");
        String full = jdbc.queryForObject("""
                SELECT a.account_number FROM accounts a JOIN users u ON u.id = a.user_id
                WHERE u.username = ?""", String.class, "safe" + s);
        assertThat(full).endsWith(last4);
        assertThat(last4).hasSize(4);
    }

    @Test
    void underscoreIsALiteralCharacterNotAWildcard() throws Exception {
        String base = "w" + TestUsers.unique();
        TestUser me = users.register();
        users.register(base + "_a", "Wild");
        users.register(base + "xa", "Wild");

        String body = search(me, base + "_");
        assertThat(body).contains(base + "_a").doesNotContain(base + "xa");
    }

    @Test
    void doesNotReturnTheCallerThemself() throws Exception {
        TestUser me = users.register();
        assertThat(search(me, me.username())).doesNotContain("\"username\":\"" + me.username() + "\"");
    }

    @Test
    void suspendedUsersAreHidden() throws Exception {
        String s = TestUsers.unique();
        TestUser me = users.register();
        users.register("susp" + s, "Susp");
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", "susp" + s);
        assertThat(search(me, "susp" + s)).doesNotContain("susp" + s);
    }

    @Test
    void tooShortQueryIsRejected() throws Exception {
        TestUser me = users.register();
        mvc.perform(get("/api/v1/users/search").param("q", "a").header("Authorization", me.auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void paginationWorksAndSizeIsCapped() throws Exception {
        String s = TestUsers.unique();
        TestUser me = users.register();
        for (int i = 0; i < 3; i++) {
            users.register("pg" + i + s, "Pager" + s);
        }
        mvc.perform(get("/api/v1/users/search").param("q", "pager" + s).param("size", "2")
                        .header("Authorization", me.auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        mvc.perform(get("/api/v1/users/search").param("q", "pager" + s).param("size", "5000")
                        .header("Authorization", me.auth()))
                .andExpect(jsonPath("$.data.size").value(50));
    }

    @Test
    void searchRequiresLogin() throws Exception {
        mvc.perform(get("/api/v1/users/search").param("q", "abc")).andExpect(status().isUnauthorized());
    }
}
