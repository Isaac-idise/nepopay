package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.Dashboard;
import com.nepopay.admin.AdminDtos.FraudStats;
import com.nepopay.admin.AdminDtos.TransferStats;
import com.nepopay.admin.AdminDtos.UserStats;
import com.nepopay.admin.AdminDtos.VolumeStats;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbc;
    private final AdminAuditQueryService audit;

    public AdminDashboardService(JdbcTemplate jdbc, AdminAuditQueryService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Dashboard build() {
        OffsetDateTime today = AdminDates.startOfToday();
        OffsetDateTime weekAgo = OffsetDateTime.now().minusDays(7);

        UserStats users = jdbc.queryForObject("""
                SELECT count(*), count(*) FILTER (WHERE status = 'ACTIVE'), count(*) FILTER (WHERE status = 'SUSPENDED'),
                       count(*) FILTER (WHERE role = 'ADMIN'), count(*) FILTER (WHERE created_at >= ?)
                FROM users""",
                (rs, i) -> new UserStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)), today);

        long failedToday = count("SELECT count(*) FROM audit_logs WHERE action = 'TRANSFER_FAILED' AND created_at >= ?", today);
        long failedAll = count("SELECT count(*) FROM audit_logs WHERE action = 'TRANSFER_FAILED'");
        TransferStats transfers = jdbc.queryForObject("""
                SELECT count(*), count(*) FILTER (WHERE status = 'SUCCESSFUL'), count(*) FILTER (WHERE status = 'FLAGGED'),
                       count(*) FILTER (WHERE status = 'REVERSED')
                FROM transactions WHERE type = 'P2P_TRANSFER'""",
                (rs, i) -> new TransferStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), failedToday, failedAll));

        VolumeStats volume = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_kobo) FILTER (WHERE created_at >= ?), 0),
                       COALESCE(SUM(amount_kobo) FILTER (WHERE created_at >= ?), 0),
                       COALESCE(SUM(amount_kobo), 0)
                FROM transactions WHERE type = 'P2P_TRANSFER' AND status = 'SUCCESSFUL'""",
                (rs, i) -> new VolumeStats(rs.getLong(1), rs.getLong(2), rs.getLong(3)), today, weekAgo);

        long held = count("SELECT COALESCE(SUM(balance_kobo), 0) FROM accounts WHERE account_type = 'SYSTEM_HOLD'");
        FraudStats fraud = jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status = 'OPEN'), count(*) FILTER (WHERE status = 'INFO'),
                       count(*) FILTER (WHERE status = 'APPROVED'), count(*) FILTER (WHERE status = 'REJECTED')
                FROM fraud_alerts""",
                (rs, i) -> new FraudStats(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), held));

        return new Dashboard(users, transfers, volume, fraud, audit.recent(10), Instant.now());
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
}
