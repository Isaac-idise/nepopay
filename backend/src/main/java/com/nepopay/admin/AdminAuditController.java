package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.AuditView;
import com.nepopay.common.ApiResponse;
import com.nepopay.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditQueryService service;

    public AdminAuditController(AdminAuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditView>> list(@RequestParam(required = false) String action,
                                                     @RequestParam(required = false) String username,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.search(action, username, from, to, page, size));
    }
}
