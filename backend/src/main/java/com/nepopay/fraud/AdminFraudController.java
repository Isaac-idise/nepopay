package com.nepopay.fraud;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import com.nepopay.fraud.FraudDtos.AlertView;
import com.nepopay.fraud.FraudDtos.ReviewRequest;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin only: /api/v1/admin/** is restricted to the ADMIN role in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/fraud-alerts")
public class AdminFraudController {

    private final FraudAlertQueryService query;
    private final FraudReviewService review;

    public AdminFraudController(FraudAlertQueryService query, FraudReviewService review) {
        this.query = query;
        this.review = review;
    }

    /** status = OPEN (default) | APPROVED | REJECTED | INFO | ALL */
    @GetMapping
    public ApiResponse<PageResponse<AlertView>> list(@RequestParam(defaultValue = "OPEN") String status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        AlertStatus filter = null;
        if (!"ALL".equalsIgnoreCase(status)) {
            try {
                filter = AlertStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "status must be OPEN, APPROVED, REJECTED, INFO or ALL.");
            }
        }
        return ApiResponse.ok(PageResponse.of(query.page(filter, Paging.of(page, size))));
    }

    @GetMapping("/{id}")
    public ApiResponse<AlertView> get(@PathVariable UUID id) {
        return ApiResponse.ok(query.get(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<AlertView> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody(required = false) ReviewRequest body) {
        return ApiResponse.ok(review.approve(CurrentUser.id(jwt), id, body == null ? null : body.note()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AlertView> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @Valid @RequestBody(required = false) ReviewRequest body) {
        return ApiResponse.ok(review.reject(CurrentUser.id(jwt), id, body == null ? null : body.note()));
    }
}
