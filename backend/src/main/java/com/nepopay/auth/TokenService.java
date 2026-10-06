package com.nepopay.auth;

import com.nepopay.config.SecurityProperties;
import com.nepopay.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Creates short-lived signed access tokens (JWT, HS256). Contains no secrets: just who you are and your role. */
@Service
public class TokenService {

    public record AccessToken(String value, long expiresInSeconds) {
    }

    private final JwtEncoder encoder;
    private final SecurityProperties props;

    public TokenService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public AccessToken issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nepopay")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expires)
                .claim("role", user.getRole().name())
                .claim("username", user.getUsername())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, props.accessTokenMinutes() * 60);
    }
}
