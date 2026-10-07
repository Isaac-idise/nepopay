package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.AdminUserView;
import com.nepopay.admin.AdminDtos.StatusChangeRequest;
import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    /** q = text in username / name / email (optional); status = ACTIVE | SUSPENDED | ALL (default). */
    @GetMapping
    public ApiResponse<PageResponse<AdminUserView>> list(@RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "ALL") String status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(service.list(q, status, Paging.of(page, size))));
    }

    @GetMapping("/{username}")
    public ApiResponse<AdminUserView> get(@PathVariable String username) {
        return ApiResponse.ok(service.get(username));
    }

    @PatchMapping("/{username}/status")
    public ApiResponse<AdminUserView> changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable String username,
                                                   @Valid @RequestBody StatusChangeRequest body) {
        return ApiResponse.ok(service.changeStatus(CurrentUser.id(jwt), username, body.status(), body.reason()));
    }
}
