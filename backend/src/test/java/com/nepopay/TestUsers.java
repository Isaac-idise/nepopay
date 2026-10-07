package com.nepopay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Test helper: registers a user through the real API and logs in for a real access token. */
public final class TestUsers {

    public record TestUser(String username, String token) {
        public String auth() {
            return "Bearer " + token;
        }
    }

    private final MockMvc mvc;

    public TestUsers(MockMvc mvc) {
        this.mvc = mvc;
    }

    public static String unique() {
        return Long.toString(Math.abs(System.nanoTime()), 36);
    }

    public TestUser register() throws Exception {
        return register("u" + unique(), "Test");
    }

    public TestUser register(String username, String firstName) throws Exception {
        String phone = "080" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        String body = """
                {"firstName":"%s","lastName":"Tester","email":"%s@example.com","phone":"%s",
                 "username":"%s","password":"Passw0rdTest","pin":"1234"}
                """.formatted(firstName, username, phone, username);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(username);
    }

    /** Fresh login (use after changing a user's role in the database, so the new token carries it). */
    public TestUser login(String username) throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"%s\",\"password\":\"Passw0rdTest\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        return new TestUser(username, token);
    }

    /** Registers a normal user, promotes it to ADMIN in the database, and logs in again so the token carries the role. */
    public TestUser admin(JdbcTemplate jdbc) throws Exception {
        TestUser u = register();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE username = ?", u.username());
        return login(u.username());
    }

    /** Sends money with PIN 1234 and returns the transaction reference. */
    public String send(TestUser from, String toUsername, long kobo) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/transfers")
                        .header("Authorization", from.auth())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUsername\":\"%s\",\"amountKobo\":%d,\"pin\":\"1234\"}".formatted(toUsername, kobo)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data.reference");
    }

    public long balance(TestUser u) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/me").header("Authorization", u.auth()))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.balanceKobo")).longValue();
    }
}
