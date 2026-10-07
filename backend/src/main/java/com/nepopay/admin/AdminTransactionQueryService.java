package com.nepopay.admin;

import com.nepopay.admin.AdminDtos.AdminEntryView;
import com.nepopay.admin.AdminDtos.AdminTransactionDetail;
import com.nepopay.admin.AdminDtos.AdminTransactionView;
import com.nepopay.common.ErrorCode;
import com.nepopay.common.NepOpayException;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import com.nepopay.transaction.TransactionStatus;
import com.nepopay.transaction.TransactionType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only monitoring of every transaction on the platform, with optional filters. */
@Service
public class AdminTransactionQueryService {

    private static final String SELECT = """
            SELECT t.reference, t.type, t.status, t.amount_kobo, t.risk_score, t.description, t.created_at,
                   su.username AS sender_username, sa.account_type AS sender_type,
                   ru.username AS receiver_username, ra.account_type AS receiver_type
            """;

    private static final String FROM = """
            FROM transactions t
            JOIN accounts sa ON sa.id = t.sender_account_id LEFT JOIN users su ON su.id = sa.user_id
            JOIN accounts ra ON ra.id = t.receiver_account_id LEFT JOIN users ru ON ru.id = ra.user_id
            """;

    private static final RowMapper<AdminTransactionView> MAPPER = (rs, i) -> new AdminTransactionView(
            rs.getString("reference"), rs.getString("type"), rs.getString("status"), rs.getLong("amount_kobo"),
            rs.getInt("risk_score"),
            label(rs.getString("sender_username"), rs.getString("sender_type")),
            label(rs.getString("receiver_username"), rs.getString("receiver_type")),
            rs.getString("description"), rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final NamedParameterJdbcTemplate jdbc;

    public AdminTransactionQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionView> search(String status, String type, String user, String from, String to,
                                                     Long minKobo, Long maxKobo, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = :status");
            p.addValue("status", parse(TransactionStatus.class, status, "status").name());
        }
        if (type != null && !type.isBlank()) {
            where.append(" AND t.type = :type");
            p.addValue("type", parse(TransactionType.class, type, "type").name());
        }
        if (user != null && !user.isBlank()) {
            where.append(" AND (lower(su.username) = :user OR lower(ru.username) = :user)");
            p.addValue("user", user.trim().toLowerCase(Locale.ROOT));
        }
        if (from != null && !from.isBlank()) {
            where.append(" AND t.created_at >= :from");
            p.addValue("from", AdminDates.startOfDay(from));
        }
        if (to != null && !to.isBlank()) {
            where.append(" AND t.created_at < :to");
            p.addValue("to", AdminDates.startOfNextDay(to));
        }
        if (minKobo != null) {
            where.append(" AND t.amount_kobo >= :min");
            p.addValue("min", minKobo);
        }
        if (maxKobo != null) {
            where.append(" AND t.amount_kobo <= :max");
            p.addValue("max", maxKobo);
        }

        long total = jdbc.queryForObject("SELECT count(*) " + FROM + where, p, Long.class);
        Pageable pg = Paging.of(page, size);
        p.addValue("limit", pg.getPageSize());
        p.addValue("offset", pg.getOffset());
        List<AdminTransactionView> rows = jdbc.query(
                SELECT + FROM + where + " ORDER BY t.created_at DESC, t.id DESC LIMIT :limit OFFSET :offset", p, MAPPER);
        return new PageResponse<>(rows, pg.getPageNumber(), pg.getPageSize(), total,
                (int) Math.ceil(total / (double) pg.getPageSize()));
    }

    @Transactional(readOnly = true)
    public AdminTransactionDetail get(String reference) {
        MapSqlParameterSource p = new MapSqlParameterSource("ref", reference);
        List<AdminTransactionView> found = jdbc.query(SELECT + FROM + " WHERE t.reference = :ref", p, MAPPER);
        if (found.isEmpty()) {
            throw new NepOpayException(ErrorCode.NOT_FOUND, "Transaction not found.");
        }
        List<AdminEntryView> entries = jdbc.query("""
                SELECT e.entry_type, e.amount_kobo, e.balance_after_kobo, e.created_at, a.account_type, u.username
                FROM ledger_entries e
                JOIN transactions t ON t.id = e.transaction_id
                JOIN accounts a ON a.id = e.account_id LEFT JOIN users u ON u.id = a.user_id
                WHERE t.reference = :ref ORDER BY e.id
                """, p, (rs, i) -> new AdminEntryView(label(rs.getString("username"), rs.getString("account_type")),
                rs.getString("entry_type"), rs.getLong("amount_kobo"), rs.getLong("balance_after_kobo"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()));
        return new AdminTransactionDetail(found.get(0), entries);
    }

    /** A person's username, or the system account's name (SYSTEM_FUNDING / SYSTEM_HOLD). */
    private static String label(String username, String accountType) {
        return username != null ? username : accountType;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NepOpayException(ErrorCode.VALIDATION_ERROR, "Unknown " + field + ": " + value);
        }
    }
}
