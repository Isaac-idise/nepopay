package com.nepopay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nepopay.admin.email=Root@Nepopay.test",
        "nepopay.admin.initial-password=ChangeMe-Initial-123"})
class AdminBootstrapIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminBootstrap bootstrap;

    @Test
    void theSeededAdminCanLogInAndUseAdminEndpointsButHasNoBankAccount() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"root@nepopay.test\",\"password\":\"ChangeMe-Initial-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");

        mvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        // the password is stored hashed
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = 'root@nepopay.test'", String.class);
        assertThat(hash).startsWith("$2").doesNotContain("ChangeMe");
    }

    @Test
    void seedingTwiceDoesNotCreateASecondAdmin() throws Exception {
        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());
        Integer n = jdbc.queryForObject("SELECT count(*) FROM users WHERE email = 'root@nepopay.test'", Integer.class);
        assertThat(n).isEqualTo(1);
    }
}
