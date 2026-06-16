package com.simpledouyin.api.comment.repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.simpledouyin.api.comment.model.Comment;
import com.simpledouyin.api.comment.model.CommentPageCursor;

@Repository
public class CommentRepository {

    /** 插入评论，通过子查询确保视频存在且未删除（避免 TOCTOU 竞态） */
    private static final String INSERT_COMMENT_SQL = """
            INSERT INTO comments (video_id, author_id, content, created_at)
            SELECT ?, ?, ?, CURRENT_TIMESTAMP(3)
            FROM videos
            WHERE id = ? AND deleted_at IS NULL
            """;

    /** 查询单条评论（含作者信息） */
    private static final String FIND_COMMENT_BY_ID_SQL = """
            SELECT
                c.id,
                c.video_id,
                c.author_id,
                u.username AS author_username,
                u.nickname AS author_nickname,
                u.avatar_url AS author_avatar_url,
                c.content,
                c.created_at
            FROM comments c
            JOIN users u ON u.id = c.author_id
            WHERE c.id = ?
              AND c.deleted_at IS NULL
            """;

    /** 分页查询视频评论列表，按时间倒序 */
    private static final String FIND_COMMENTS_BASE_SQL = """
            SELECT
                c.id,
                c.video_id,
                c.author_id,
                u.username AS author_username,
                u.nickname AS author_nickname,
                u.avatar_url AS author_avatar_url,
                c.content,
                c.created_at
            FROM comments c
            JOIN users u ON u.id = c.author_id
            WHERE c.video_id = ?
              AND c.deleted_at IS NULL
            """;

    /** 统计视频评论数 */
    private static final String COUNT_COMMENTS_SQL = """
            SELECT COUNT(*)
            FROM comments
            WHERE video_id = ?
              AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 发表评论。INSERT 通过子查询验证视频存在且未删除，避免 TOCTOU 竞态。
     * 如果 affectedRows=0 说明视频不存在/已删除。
     * comment_count 改为实时 SELECT COUNT(*)，不再维护反规范化列。
     */
    public Comment create(long videoId, long authorId, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affectedRows = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_COMMENT_SQL,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, videoId);
            statement.setLong(2, authorId);
            statement.setString(3, content);
            statement.setLong(4, videoId);
            return statement;
        }, keyHolder);

        if (affectedRows == 0) {
            throw new IllegalStateException("video not found or deleted: " + videoId);
        }
        long commentId = generatedId(keyHolder);
        return findById(commentId)
                .orElseThrow(() -> new IllegalStateException("Created comment was not found"));
    }

    /**
     * 根据 ID 查询单条评论（未删除），含作者信息。
     */
    public Optional<Comment> findById(long commentId) {
        List<Comment> comments = jdbcTemplate.query(
                FIND_COMMENT_BY_ID_SQL,
                commentRowMapper(),
                commentId
        );
        return comments.stream().findFirst();
    }

    /**
     * 分页查询视频评论列表，按 created_at DESC, id DESC 排序。
     * @param limit 查询条数（通常为 pageSize + 1，用于判断 hasMore）
     */
    public List<Comment> findByVideoId(long videoId, CommentPageCursor cursor, int limit) {
        StringBuilder sql = new StringBuilder(FIND_COMMENTS_BASE_SQL);
        List<Object> args = new ArrayList<>();
        args.add(videoId);
        if (cursor != null) {
            sql.append("""
                      AND (
                          c.created_at < ?
                          OR (c.created_at = ? AND c.id < ?)
                      )
                    """);
            Timestamp cursorTime = Timestamp.valueOf(cursor.createdAt());
            args.add(cursorTime);
            args.add(cursorTime);
            args.add(cursor.id());
        }
        sql.append("""
                ORDER BY c.created_at DESC, c.id DESC
                LIMIT ?
                """);
        args.add(limit);
        return jdbcTemplate.query(sql.toString(), commentRowMapper(), args.toArray());
    }

    /**
     * 统计视频的评论总数。
     */
    public long countByVideoId(long videoId) {
        Long count = jdbcTemplate.queryForObject(COUNT_COMMENTS_SQL, Long.class, videoId);
        return count != null ? count : 0L;
    }

    private RowMapper<Comment> commentRowMapper() {
        return (resultSet, rowNumber) -> new Comment(
                resultSet.getLong("id"),
                resultSet.getLong("video_id"),
                resultSet.getLong("author_id"),
                resultSet.getString("author_username"),
                resultSet.getString("author_nickname"),
                resultSet.getString("author_avatar_url"),
                resultSet.getString("content"),
                resultSet.getTimestamp("created_at").toLocalDateTime()
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
        throw new IllegalStateException("Comment ID was not generated");
    }
}
