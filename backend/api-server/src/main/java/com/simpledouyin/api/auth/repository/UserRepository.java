package com.simpledouyin.api.auth.repository;

import com.simpledouyin.api.auth.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;

@Repository
public class UserRepository {

    private static final String EXISTS_SQL = """
            SELECT EXISTS(
                SELECT 1
                FROM users
                WHERE username = ?
            )
            """;

    private static final String INSERT_SQL = """
            INSERT INTO users (
                username,
                password_hash,
                nickname,
                avatar_url,
                status,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, NULL, 'active', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByUsername(String username) {
        Boolean exists = jdbcTemplate.queryForObject(EXISTS_SQL, Boolean.class, username);
        return Boolean.TRUE.equals(exists);
    }

    public UserAccount create(String username, String passwordHash, String nickname) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            statement.setString(3, nickname);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("User ID was not generated");
        }
        return new UserAccount(generatedId.longValue(), username, nickname, null);
    }
}
