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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class PasswordChangeIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private ResultActions change(TestUser u, String current, String next) throws Exception {
        return mvc.perform(patch("/api/v1/users/me/password").header("Authorization", u.auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(username, password))).andReturn();
    }

    @Test
    void changingThePasswordSignsOutEveryDevice() throws Exception {
        TestUser u = new TestUsers(mvc).register();
        Cookie oldRefresh = login(u.username(), "Passw0rdTest").getResponse().getCookie("nepopay_refresh");

        change(u, "Passw0rdTest", "NewPassw0rd9").andExpect(status().isOk());

        assertThat(login(u.username(), "Passw0rdTest").getResponse().getStatus()).isEqualTo(401);
        assertThat(login(u.username(), "NewPassw0rd9").getResponse().getStatus()).isEqualTo(200);
        // the refresh token from before the change no longer works
        mvc.perform(post("/api/v1/auth/refresh").cookie(oldRefresh)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/notifications").header("Authorization", u.auth()))
                .andExpect(jsonPath("$.data.content[?(@.type=='SECURITY')]").isNotEmpty());
        Integer audited = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users x ON x.id = l.user_id
                WHERE x.username = ? AND l.action = 'PASSWORD_CHANGED'""", Integer.class, u.username());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void wrongCurrentPasswordIsRefusedAndAudited() throws Exception {
        TestUser u = new TestUsers(mvc).register();
        change(u, "WrongPass1", "NewPassw0rd9")
                .andExpect(status().isUnprocessableEntity())              // 422, not 401, so apps do not treat it as "logged out"
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD"));
        assertThat(login(u.username(), "Passw0rdTest").getResponse().getStatus()).isEqualTo(200); // unchanged
        Integer failures = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users x ON x.id = l.user_id
                WHERE x.username = ? AND l.action = 'PASSWORD_CHANGE_FAILED'""", Integer.class, u.username());
        assertThat(failures).isEqualTo(1);
    }

    @Test
    void weakOrUnchangedPasswordsAreRejected() throws Exception {
        TestUser u = new TestUsers(mvc).register();
        change(u, "Passw0rdTest", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        change(u, "Passw0rdTest", "onlyletters").andExpect(status().isBadRequest());
        change(u, "Passw0rdTest", "Passw0rdTest").andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rdTest\",\"newPassword\":\"NewPassw0rd9\"}"))
                .andExpect(status().isUnauthorized());
    }
}
