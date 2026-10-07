package com.nepopay.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepopay.admin.AdminDtos.AuditView;
import com.nepopay.common.PageResponse;
import com.nepopay.common.Paging;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditQueryService {

    private static final String SELECT = """
            SELECT a.id, a.action, u.username, a.entity_type, a.entity_id,
                   cast(a.metadata as text) AS metadata, a.created_at
            FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdminAuditQueryService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditView> search(String action, String username, String from, String to, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (action != null && !action.isBlank()) {
            where.append(" AND a.action = :action");
            p.addValue("action", action.trim().toUpperCase(Locale.ROOT));
        }
        if (username != null && !username.isBlank()) {
            where.append(" AND lower(u.username) = :username");
            p.addValue("username", username.trim().toLowerCase(Locale.ROOT));
        }
        if (from != null && !from.isBlank()) {
            where.append(" AND a.created_at >= :from");
            p.addValue("from", AdminDates.startOfDay(from));
        }
        if (to != null && !to.isBlank()) {
            where.append(" AND a.created_at < :to");
            p.addValue("to", AdminDates.startOfNextDay(to));
        }

        long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs a LEFT JOIN users u ON u.id = a.user_id" + where,
                p, Long.class);
        Pageable pg = Paging.of(page, size);
        p.addValue("limit", pg.getPageSize());
        p.addValue("offset", pg.getOffset());
        List<AuditView> rows = jdbc.query(SELECT + where + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit OFFSET :offset",
                p, mapper());
        return new PageResponse<>(rows, pg.getPageNumber(), pg.getPageSize(), total,
                (int) Math.ceil(total / (double) pg.getPageSize()));
    }

    @Transactional(readOnly = true)
    public List<AuditView> recent(int limit) {
        return jdbc.query(SELECT + " ORDER BY a.created_at DESC, a.id DESC LIMIT :limit",
                new MapSqlParameterSource("limit", limit), mapper());
    }

    private RowMapper<AuditView> mapper() {
        return (rs, i) -> {
            try {
                String json = rs.getString("metadata");
                return new AuditView(rs.getLong("id"), rs.getString("action"), rs.getString("username"),
                        rs.getString("entity_type"), rs.getString("entity_id"),
                        json == null ? null : mapper.readTree(json),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant());
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Bad audit metadata", e);
            }
        };
    }
}
