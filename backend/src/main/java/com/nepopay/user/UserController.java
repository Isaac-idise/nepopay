package com.nepopay.user;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    public record MeResponse(String username, String displayName, String email, String phone,
                             String role, String status, Instant createdAt) {
    }

    private final UserRepository users;
    private final UserSearchService search;

    public UserController(UserRepository users, UserSearchService search) {
        this.users = users;
        this.search = search;
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        User u = users.findById(CurrentUser.id(jwt))
                .orElseThrow(() -> new NepOpayException(ErrorCode.UNAUTHORIZED));
        return ApiResponse.ok(new MeResponse(u.getUsername(), u.getDisplayName(), u.getEmail(),
                u.getPhone(), u.getRole().name(), u.getStatus().name(), u.getCreatedAt()));
    }

    @GetMapping("/search")
    public ApiResponse<PageResponse<UserSearchResult>> search(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(PageResponse.of(search.search(CurrentUser.id(jwt), q, Paging.of(page, size))));
    }
}
