package com.nepopay.auth;

import com.nepopay.common.ApiResponse;
import com.nepopay.config.SecurityProperties;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "nepopay_refresh";

    private final RegistrationService registration;
    private final AuthService auth;
    private final SecurityProperties props;

    public AuthController(RegistrationService registration, AuthService auth, SecurityProperties props) {
        this.registration = registration;
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(registration.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withRefreshCookie(auth.login(request, userAgent));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return withRefreshCookie(auth.refresh(refreshToken, userAgent));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .body(ApiResponse.<Void>ok(null));
    }

    private ResponseEntity<ApiResponse<TokenResponse>> withRefreshCookie(AuthService.AuthResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookie(result.refreshToken(), Duration.ofDays(props.refreshTokenDays())).toString())
                .body(ApiResponse.ok(result.body()));
    }

    /** httpOnly = JavaScript cannot read it (protects against XSS theft). Path-limited to the auth endpoints. */
    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite(props.cookieSameSite())
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
