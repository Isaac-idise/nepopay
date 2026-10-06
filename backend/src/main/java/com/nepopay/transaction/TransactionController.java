package com.nepopay.transaction;

import com.nepopay.auth.CurrentUser;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import com.nepopay.transaction.TransactionDtos.TransactionDetail;
import com.nepopay.transaction.TransactionDtos.TransactionView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<TransactionView>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(service.history(CurrentUser.id(jwt), Paging.of(page, size))));
    }

    @GetMapping("/{reference}")
    public ApiResponse<TransactionDetail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable String reference) {
        return ApiResponse.ok(service.detail(CurrentUser.id(jwt), reference));
    }
}
