package com.nepopay.user;

import com.nepopay.common.ApiResponse;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    public record MeResponse(String username, String displayName, String email, String phone,
                             String role, String status, Instant createdAt) {
    }

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    /** The identity always comes from the verified token, never from a client-supplied id. */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        User u = users.findById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(new MeResponse(u.getUsername(), u.getDisplayName(), u.getEmail(),
                u.getPhone(), u.getRole().name(), u.getStatus().name(), u.getCreatedAt()));
    }
}
