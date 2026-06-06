package com.simpledouyin.api.video.repository;

import com.simpledouyin.api.video.model.VideoAuthor;
import com.simpledouyin.api.video.model.VideoCreateCommand;
import com.simpledouyin.api.video.model.VideoOwnership;
import com.simpledouyin.api.video.model.VideoPageCursor;
import com.simpledouyin.api.video.model.VideoPost;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class VideoRepository {

    private static final String FIND_AUTHOR_SQL = """
            SELECT id, username, nickname, avatar_url
            FROM users
            WHERE id = ?
            """;

    private static final String INSERT_VIDEO_SQL = """
            INSERT INTO videos (
                author_id,
                caption,
                video_url,
                cover_url,
                duration_ms,
                like_count,
                view_count,
                comment_count,
                visibility,
                status,
                created_at,
                updated_at,
                deleted_at
            ) VALUES (?, ?, ?, ?, ?, 0, 0, 0, ?, 'published', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), NULL)
            """;

    private static final String FIND_POST_BY_ID_SQL = """
            SELECT
                v.id,
                v.author_id,
                u.username AS author_username,
                u.nickname AS author_nickname,
                u.avatar_url AS author_avatar_url,
                v.caption,
                v.video_url,
                v.cover_url,
                v.duration_ms,
                v.like_count,
                v.view_count,
                v.comment_count,
                v.visibility,
                v.status,
                v.created_at,
                EXISTS(
                    SELECT 1
                    FROM video_likes vl
                    WHERE vl.user_id = ?
                      AND vl.video_id = v.id
                ) AS liked,
                EXISTS(
                    SELECT 1
                    FROM video_views vv
                    WHERE vv.user_id = ?
                      AND vv.video_id = v.id
                ) AS viewed
            FROM videos v
            JOIN users u ON u.id = v.author_id
            WHERE v.id = ?
              AND v.deleted_at IS NULL
            """;

    private static final String FIND_MY_VIDEOS_BASE_SQL = """
            SELECT
                v.id,
                v.author_id,
                u.username AS author_username,
                u.nickname AS author_nickname,
                u.avatar_url AS author_avatar_url,
                v.caption,
                v.video_url,
                v.cover_url,
                v.duration_ms,
                v.like_count,
                v.view_count,
                v.comment_count,
                v.visibility,
                v.status,
                v.created_at,
                EXISTS(
                    SELECT 1
                    FROM video_likes vl
                    WHERE vl.user_id = ?
                      AND vl.video_id = v.id
                ) AS liked,
                EXISTS(
                    SELECT 1
                    FROM video_views vv
                    WHERE vv.user_id = ?
                      AND vv.video_id = v.id
                ) AS viewed
            FROM videos v
            JOIN users u ON u.id = v.author_id
            WHERE v.author_id = ?
              AND v.deleted_at IS NULL
            """;

    private static final String FIND_OWNERSHIP_SQL = """
            SELECT id, author_id, deleted_at
            FROM videos
            WHERE id = ?
            """;

    private static final String SOFT_DELETE_SQL = """
            UPDATE videos
            SET deleted_at = CURRENT_TIMESTAMP(3),
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = ?
              AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public VideoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<VideoAuthor> findAuthorById(long authorId) {
        List<VideoAuthor> authors = jdbcTemplate.query(
                FIND_AUTHOR_SQL,
                (resultSet, rowNumber) -> new VideoAuthor(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("nickname"),
                        resultSet.getString("avatar_url")
                ),
                authorId
        );
        return authors.stream().findFirst();
    }

    public VideoPost create(VideoCreateCommand command, long viewerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_VIDEO_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, command.authorId());
            statement.setString(2, command.caption());
            statement.setString(3, command.videoUrl());
            statement.setString(4, command.coverUrl());
            if (command.durationMs() == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, command.durationMs());
            }
            statement.setString(6, command.visibility());
            return statement;
        }, keyHolder);

        long videoId = generatedId(keyHolder);
        return findPostById(videoId, viewerId)
                .orElseThrow(() -> new IllegalStateException("Created video was not found"));
    }

    public Optional<VideoPost> findPostById(long videoId, long viewerId) {
        List<VideoPost> posts = jdbcTemplate.query(
                FIND_POST_BY_ID_SQL,
                videoPostRowMapper(),
                viewerId,
                viewerId,
                videoId
        );
        return posts.stream().findFirst();
    }

    public List<VideoPost> findMyVideos(long userId, VideoPageCursor cursor, int limit) {
        StringBuilder sql = new StringBuilder(FIND_MY_VIDEOS_BASE_SQL);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(userId);
        args.add(userId);
        if (cursor != null) {
            sql.append("""
                      AND (
                          v.created_at < ?
                          OR (v.created_at = ? AND v.id < ?)
                      )
                    """);
            Timestamp cursorTime = Timestamp.valueOf(cursor.createdAt());
            args.add(cursorTime);
            args.add(cursorTime);
            args.add(cursor.id());
        }
        sql.append("""
                ORDER BY v.created_at DESC, v.id DESC
                LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), videoPostRowMapper(), args.toArray());
    }

    public Optional<VideoOwnership> findOwnershipById(long videoId) {
        List<VideoOwnership> videos = jdbcTemplate.query(
                FIND_OWNERSHIP_SQL,
                (resultSet, rowNumber) -> new VideoOwnership(
                        resultSet.getLong("id"),
                        resultSet.getLong("author_id"),
                        toLocalDateTime(resultSet.getTimestamp("deleted_at"))
                ),
                videoId
        );
        return videos.stream().findFirst();
    }

    public void softDelete(long videoId) {
        jdbcTemplate.update(SOFT_DELETE_SQL, videoId);
    }

    private RowMapper<VideoPost> videoPostRowMapper() {
        return (resultSet, rowNumber) -> new VideoPost(
                resultSet.getLong("id"),
                resultSet.getLong("author_id"),
                resultSet.getString("author_username"),
                resultSet.getString("author_nickname"),
                resultSet.getString("author_avatar_url"),
                resultSet.getString("caption"),
                resultSet.getString("video_url"),
                resultSet.getString("cover_url"),
                (Integer) resultSet.getObject("duration_ms"),
                resultSet.getLong("like_count"),
                resultSet.getLong("view_count"),
                resultSet.getLong("comment_count"),
                resultSet.getString("visibility"),
                resultSet.getString("status"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getBoolean("liked"),
                resultSet.getBoolean("viewed")
        );
    }

    private long generatedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        List<Map<String, Object>> keyList = keyHolder.getKeyList();
        if (!keyList.isEmpty()) {
            Object value = keyList.get(0).values().stream().findFirst().orElse(null);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        throw new IllegalStateException("Video ID was not generated");
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
