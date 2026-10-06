package com.nepopay.auth;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Identity always comes from the verified token, never from a client-supplied id. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
