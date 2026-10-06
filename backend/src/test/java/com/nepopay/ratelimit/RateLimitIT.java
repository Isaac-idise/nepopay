package com.nepopay.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nepopay.AbstractIntegrationTest;
import com.nepopay.TestUsers;
import com.nepopay.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nepopay.rate-limit.enabled=true",
        "nepopay.rate-limit.login-ip-per-minute=1000",
        "nepopay.rate-limit.register-per-hour=1000",
        "nepopay.rate-limit.login-account-per-window=3",
        "nepopay.rate-limit.refresh-per-minute=3",
        "nepopay.rate-limit.search-per-minute=3",
        "nepopay.rate-limit.transfer-per-minute=2"})
class RateLimitIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private void login(String identifier, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void repeatedLoginsForOneAccountAreThrottledEvenWithTheCorrectPassword() throws Exception {
        TestUser u = new TestUsers(mvc).register();   // registering already used 1 of this account's 3 logins
        login(u.username(), "WrongPass1", 401);
        login(u.username(), "WrongPass1", 401);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Passw0rdTest\"}".formatted(u.username())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"));
    }

    @Test
    void searchIsLimitedPerUserNotGlobally() throws Exception {
        TestUsers users = new TestUsers(mvc);
        TestUser a = users.register();
        TestUser b = users.register();

        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/users/search").param("q", "abc").header("Authorization", a.auth()))
                    .andExpect(status().isOk());
        }
        String retryAfter = mvc.perform(get("/api/v1/users/search").param("q", "abc").header("Authorization", a.auth()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1);

        mvc.perform(get("/api/v1/users/search").param("q", "abc").header("Authorization", b.auth()))
                .andExpect(status().isOk());
    }

    @Test
    void transfersAreLimitedPerUser() throws Exception {
        TestUser a = new TestUsers(mvc).register();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/transfers").header("Authorization", a.auth())
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipientUsername\":\"nobody-here\",\"amountKobo\":100,\"pin\":\"1234\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(post("/api/v1/transfers").header("Authorization", a.auth())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"nobody-here\",\"amountKobo\":100,\"pin\":\"1234\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refreshIsLimitedPerClientAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/refresh").with(fromIp("10.9.9.9"))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/refresh").with(fromIp("10.9.9.9"))).andExpect(status().isTooManyRequests());
        // a different address is not affected
        mvc.perform(post("/api/v1/auth/refresh").with(fromIp("10.8.8.8"))).andExpect(status().isUnauthorized());
    }
}
