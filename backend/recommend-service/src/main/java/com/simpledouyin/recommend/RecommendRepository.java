package com.simpledouyin.recommend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RecommendRepository {

    private static final String BASE_SQL = """
            SELECT v.id,
                   (SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) AS like_count,
                   v.created_at
            FROM videos v
            WHERE v.status = 'published'
              AND v.visibility = 'public'
              AND v.deleted_at IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM video_views vv
                  WHERE vv.user_id = ? AND vv.video_id = v.id
              )
            """;

    private static final String CURSOR_CLAUSE = """
              AND (
                  v.like_count < ?
                  OR (v.like_count = ? AND v.created_at < ?)
                  OR (v.like_count = ? AND v.created_at = ? AND v.id < ?)
              )
            """;

    private static final String ORDER_BY = """
            ORDER BY v.like_count DESC, v.created_at DESC, v.id DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RecommendRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecommendVideoRow> findRecommended(long userId, CursorCodec.Cursor cursor, int limit) {
        StringBuilder sql = new StringBuilder(BASE_SQL);
        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (cursor != null) {
            sql.append(CURSOR_CLAUSE);
            long likeCount = cursor.likeCount();
            Timestamp createdAt = Timestamp.valueOf(cursor.createdAt());
            long videoId = cursor.videoId();
            args.add(likeCount);
            args.add(likeCount);
            args.add(createdAt);
            args.add(likeCount);
            args.add(createdAt);
            args.add(videoId);
        }

        sql.append(ORDER_BY);
        args.add(limit);

        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new RecommendVideoRow(
                        rs.getLong("id"),
                        rs.getLong("like_count"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ),
                args.toArray()
        );
    }
}
