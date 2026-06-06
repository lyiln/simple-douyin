package com.simpledouyin.api.logging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RequestLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO request_logs (
                request_id,
                user_id,
                method,
                path,
                `query`,
                request_body,
                response_body,
                status_code,
                business_code,
                duration_ms,
                client_ip,
                user_agent,
                error_message,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public RequestLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(RequestLogEntry entry) {
        jdbcTemplate.update(
                INSERT_SQL,
                entry.requestId(),
                entry.userId(),
                entry.method(),
                entry.path(),
                entry.query(),
                entry.requestBody(),
                entry.responseBody(),
                entry.statusCode(),
                entry.businessCode(),
                entry.durationMs(),
                entry.clientIp(),
                entry.userAgent(),
                entry.errorMessage(),
                entry.createdAt()
        );
    }
}
