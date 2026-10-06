package com.nepopay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nepopay.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class AuthIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Passw0rdTest";
    private static final String COOKIE = "nepopay_refresh";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder jwtEncoder;

    private record Creds(String username, String email) {
    }

    private Creds register() throws Exception {
        String s = Long.toString(System.nanoTime(), 36);
        String phone = "080" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        String body = """
                {"firstName":"Ada","lastName":"Obi","email":"%s@example.com","phone":"%s",
                 "username":"u%s","password":"%s","pin":"1234"}
                """.formatted(s, phone, s, PASSWORD);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return new Creds("u" + s, s + "@example.com");
    }

    private MvcResult login(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password))).andReturn();
    }

    private String accessToken(MvcResult login) throws Exception {
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String refreshCookie(MvcResult result) {
        Cookie c = result.getResponse().getCookie(COOKIE);
        return c == null ? null : c.getValue();
    }

    private MvcResult refresh(String cookieValue) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, cookieValue))).andReturn();
    }

    @Test
    void loginReturnsAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        Creds c = register();
        MvcResult r = login(c.username(), PASSWORD);

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String body = r.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(body, "$.data.tokenType")).isEqualTo("Bearer");
        assertThat(JsonPath.<String>read(body, "$.data.accessToken")).isNotBlank();
        assertThat(body).doesNotContain(PASSWORD).doesNotContain("passwordHash");

        Cookie cookie = r.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
    }

    @Test
    void loginAlsoWorksWithEmail() throws Exception {
        Creds c = register();
        assertThat(login(c.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameError() throws Exception {
        Creds c = register();
        MvcResult wrongPassword = login(c.username(), "WrongPass1");
        MvcResult unknownUser = login("nobody" + System.nanoTime(), PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(wrongPassword.getResponse().getContentAsString(), "$.errorCode"))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(wrongPassword.getResponse().getContentAsString(), "$.message"))
                .isEqualTo(JsonPath.<String>read(unknownUser.getResponse().getContentAsString(), "$.message"));
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessTokenOpensProfileAndAccount() throws Exception {
        Creds c = register();
        String token = accessToken(login(c.username(), PASSWORD));

        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceKobo").value(10_000_000))
                .andExpect(jsonPath("$.data.accountNumber", startsWith("99")));
        mvc.perform(get("/api/v1/accounts/me/balance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balanceKobo").value(10_000_000));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(c.username()))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.pinHash").doesNotExist());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        Creds c = register();
        String[] parts = accessToken(login(c.username(), PASSWORD)).split("\\.");
        char first = parts[1].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + first + parts[1].substring(1) + "." + parts[2];

        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nepopay").subject(UUID.randomUUID().toString())
                .issuedAt(now.minus(2, ChronoUnit.HOURS)).expiresAt(now.minus(1, ChronoUnit.HOURS))
                .claim("role", "USER").build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        mvc.perform(get("/api/v1/accounts/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ordinaryUserCannotReachAdminEndpoints() throws Exception {
        Creds c = register();
        String token = accessToken(login(c.username(), PASSWORD));
        mvc.perform(get("/api/v1/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void refreshRotatesTokensAndReuseOfAnOldTokenKillsTheWholeSession() throws Exception {
        Creds c = register();
        String first = refreshCookie(login(c.username(), PASSWORD));

        MvcResult second = refresh(first);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        String secondToken = refreshCookie(second);
        assertThat(secondToken).isNotBlank().isNotEqualTo(first);

        // Replaying the already-used first token looks like theft...
        MvcResult replay = refresh(first);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(JsonPath.<String>read(replay.getResponse().getContentAsString(), "$.errorCode"))
                .isEqualTo("SESSION_EXPIRED");

        // ...so even the newest token is now dead.
        assertThat(refresh(secondToken).getResponse().getStatus()).isEqualTo(401);

        Integer alerts = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users u ON u.id = l.user_id
                WHERE u.username = ? AND l.action = 'TOKEN_REUSE_DETECTED'""", Integer.class, c.username());
        assertThat(alerts).isEqualTo(1);
    }

    @Test
    void logoutRevokesTheSession() throws Exception {
        Creds c = register();
        String cookie = refreshCookie(login(c.username(), PASSWORD));

        MvcResult out = mvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isOk()).andReturn();
        assertThat(out.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        assertThat(refresh(cookie).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void refreshWithoutCookieIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));
    }

    @Test
    void suspendedUserCannotLogIn() throws Exception {
        Creds c = register();
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE username = ?", c.username());
        MvcResult r = login(c.username(), PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(JsonPath.<String>read(r.getResponse().getContentAsString(), "$.errorCode"))
                .isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void refreshTokenIsStoredOnlyAsAHash() throws Exception {
        Creds c = register();
        String raw = refreshCookie(login(c.username(), PASSWORD));

        Integer plain = jdbc.queryForObject("SELECT count(*) FROM sessions WHERE token_hash = ?", Integer.class, raw);
        Integer hashLength = jdbc.queryForObject("""
                SELECT max(length(s.token_hash)) FROM sessions s JOIN users u ON u.id = s.user_id
                WHERE u.username = ?""", Integer.class, c.username());
        assertThat(plain).isZero();
        assertThat(hashLength).isEqualTo(64);
    }

    @Test
    void failedLoginsAreAudited() throws Exception {
        Creds c = register();
        login(c.username(), "WrongPass1");
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs l JOIN users u ON u.id = l.user_id
                WHERE u.username = ? AND l.action = 'LOGIN_FAILED'""", Integer.class, c.username());
        assertThat(n).isEqualTo(1);
    }
}
