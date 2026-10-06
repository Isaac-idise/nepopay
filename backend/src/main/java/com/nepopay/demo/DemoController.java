package com.nepopay.demo;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoResetService reset;

    public DemoController(DemoResetService reset) {
        this.reset = reset;
    }

    @PostMapping("/reset")
    public ApiResponse<DemoResetResponse> reset(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody DemoResetRequest request) {
        return ApiResponse.ok(reset.reset(CurrentUser.id(jwt), request.pin()));
    }
}
