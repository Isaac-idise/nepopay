package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.AdminTransactionDetail;
import com.nepopay.admin.AdminDtos.AdminTransactionView;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private final AdminTransactionQueryService service;

    public AdminTransactionController(AdminTransactionQueryService service) {
        this.service = service;
    }

    /** All filters optional. from/to are dates like 2026-10-01 (inclusive, Lagos time); amounts are in kobo. */
    @GetMapping
    public ApiResponse<PageResponse<AdminTransactionView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long minKobo,
            @RequestParam(required = false) Long maxKobo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(status, type, user, from, to, minKobo, maxKobo, page, size));
    }

    @GetMapping("/{reference}")
    public ApiResponse<AdminTransactionDetail> get(@PathVariable String reference) {
        return ApiResponse.ok(service.get(reference));
    }
}
