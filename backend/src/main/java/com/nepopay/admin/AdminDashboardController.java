package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.Dashboard;
import com.nepopay.admin.AdminDtos.ReconciliationReport;
import com.nepopay.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Everything under /api/v1/admin/** is restricted to the ADMIN role in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminDashboardController {

    private final AdminDashboardService dashboard;
    private final ReconciliationService reconciliation;

    public AdminDashboardController(AdminDashboardService dashboard, ReconciliationService reconciliation) {
        this.dashboard = dashboard;
        this.reconciliation = reconciliation;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(dashboard.build());
    }

    @GetMapping("/ledger/reconciliation")
    public ApiResponse<ReconciliationReport> reconciliation() {
        return ApiResponse.ok(reconciliation.check());
    }
}
