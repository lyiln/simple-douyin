package com.simpledouyin.api.user.repository;

import com.simpledouyin.api.user.model.UserProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserProfileRepository {

    private static final String FIND_PROFILE_SQL = """
            SELECT
                u.id,
                u.username,
                u.nickname,
                u.avatar_url,
                (
                    SELECT COUNT(*)
                    FROM videos v
                    WHERE v.author_id = u.id
                      AND v.deleted_at IS NULL
                ) AS video_count,
                (
                    SELECT COUNT(*)
                    FROM video_likes vl
                    WHERE vl.user_id = u.id
                ) AS liked_count
            FROM users u
            WHERE u.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserProfile> findProfileById(long userId) {
        List<UserProfile> profiles = jdbcTemplate.query(
                FIND_PROFILE_SQL,
                (resultSet, rowNumber) -> new UserProfile(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url"),
                        resultSet.getLong("video_count"),
                        resultSet.getLong("liked_count")
                ),
                userId
        );
        return profiles.stream().findFirst();
    }
}
