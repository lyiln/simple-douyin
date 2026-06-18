package com.simpledouyin.recommend;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecommendRepository {

    /**
     * 推荐查询：实时 COUNT 子查询获取 like_count，按 like_count DESC, created_at DESC, id DESC 排序。
     *
     * 设计取舍说明：
     * - 当前实现牺牲索引效率换取强一致性（like_count 实时计算，无漂移风险）。
     * - idx_videos_recommend(status, visibility, deleted_at, like_count DESC, ...) 中
     *   like_count 列始终为 0（未维护反规范化计数器），因此该索引对排序完全失效，
     *   优化器会全表扫描 videos 并对每行执行关联子查询。
     * - 课程演示数据量小，此性能退化不可感知。生产环境应恢复反规范化计数器
     *   （like/unlike 时在事务内同步 UPDATE videos SET like_count = like_count ± 1）。
     */
    private static final String INNER_SQL =
            "SELECT v.id, "
                    + "(SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) AS like_count, "
                    + "v.created_at "
                    + "FROM videos v "
                    + "WHERE v.status = 'published' "
                    + "AND v.visibility = 'public' "
                    + "AND v.deleted_at IS NULL "
                    + "AND NOT EXISTS ("
                    // video_views 一行 = 用户已刷到过该视频，推荐去重依赖此语义。
                    // 禁止在非视频流访问场景（如点击评论、点赞等）写入 video_views，
                    // 否则会导致用户仅浏览评论的视频从推荐流中消失。
                    + "SELECT 1 FROM video_views vv "
                    + "WHERE vv.user_id = ? AND vv.video_id = v.id)";

    private static final String CURSOR_CLAUSE =
            " AND ("
                    + "t.like_count < ? "
                    + "OR (t.like_count = ? AND t.created_at < ?) "
                    + "OR (t.like_count = ? AND t.created_at = ? AND t.id < ?)"
                    + ")";

    private static final String ORDER_BY =
            " ORDER BY t.like_count DESC, t.created_at DESC, t.id DESC LIMIT ?";

    private final JdbcTemplate jdbcTemplate;

    public RecommendRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecommendVideoRow> findRecommended(long userId, CursorCodec.Cursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("SELECT t.id, t.like_count, t.created_at FROM (");
        sql.append(INNER_SQL);
        sql.append(") t WHERE 1=1");
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

    public int resetRecommendedHistory(long userId) {
        return jdbcTemplate.update("DELETE FROM video_views WHERE user_id = ?", userId);
    }
}
