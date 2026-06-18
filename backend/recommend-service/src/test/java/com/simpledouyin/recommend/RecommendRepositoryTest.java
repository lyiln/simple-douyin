package com.simpledouyin.recommend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecommendRepositoryTest {

    private static final long USER_A = 80001L;
    private static final long USER_B = 80002L;

    @Autowired
    private RecommendRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "INSERT IGNORE INTO users (id, username, password_hash, nickname, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                USER_A, "rec_test_a", "hashed", "UserA"
        );
        jdbcTemplate.update(
                "INSERT IGNORE INTO users (id, username, password_hash, nickname, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                USER_B, "rec_test_b", "hashed", "UserB"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM video_views WHERE user_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM video_likes WHERE video_id BETWEEN 81001 AND 87002");
        jdbcTemplate.update("DELETE FROM video_likes WHERE user_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM videos WHERE author_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", USER_A, USER_B);
    }

    private long insertVideo(long id, long authorId, long likeCount, String createdAt, String visibility) {
        jdbcTemplate.update(
                "INSERT INTO videos (id, author_id, caption, video_url, like_count, visibility, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'published', ?, CURRENT_TIMESTAMP(3))",
                id, authorId, "test", "/uploads/test.mp4", likeCount, visibility, createdAt
        );
        return id;
    }

    private void insertView(long userId, long videoId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO video_views (user_id, video_id, created_at, updated_at) "
                        + "VALUES (?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                userId, videoId
        );
    }

    private void insertLikes(long videoId, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO video_likes (user_id, video_id, created_at) "
                            + "VALUES (?, ?, CURRENT_TIMESTAMP(3))",
                    90000L + i, videoId
            );
        }
    }

    private Integer countViews(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_views WHERE user_id = ?",
                Integer.class,
                userId
        );
    }

    private Integer countViews(long userId, long videoId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM video_views WHERE user_id = ? AND video_id = ?",
                Integer.class,
                userId,
                videoId
        );
    }

    // R01: 三条视频 like_count 100/50/10 → 返回顺序 100/50/10
    @Test
    void sortsByLikeCountDesc() {
        long v1 = insertVideo(81001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(81002L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        long v3 = insertVideo(81003L, USER_A, 10L, "2026-06-03 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);
        insertLikes(v3, 10);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).extracting(RecommendVideoRow::videoId)
                .containsExactly(v1, v2, v3);
    }

    // R02: 同 like_count 不同 created_at → 新视频在前
    @Test
    void sortsByCreatedAtWhenLikesEqual() {
        long v1 = insertVideo(82001L, USER_A, 10L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(82002L, USER_A, 10L, "2026-06-03 08:00:00", "public");
        long v3 = insertVideo(82003L, USER_A, 10L, "2026-06-02 08:00:00", "public");
        insertLikes(v1, 10);
        insertLikes(v2, 10);
        insertLikes(v3, 10);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).extracting(RecommendVideoRow::videoId)
                .containsExactly(v2, v3, v1);
    }

    // R03: 当前用户已访问高赞视频 → 不再返回
    @Test
    void excludesViewedVideos() {
        long v1 = insertVideo(83001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(83002L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        long v3 = insertVideo(83003L, USER_A, 10L, "2026-06-03 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);
        insertLikes(v3, 10);

        insertView(USER_B, v1);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).extracting(RecommendVideoRow::videoId)
                .containsExactly(v2, v3);
    }

    // R04: 用户访问全部视频 → 空
    @Test
    void returnsEmptyWhenAllViewed() {
        long v1 = insertVideo(84001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(84002L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);

        insertView(USER_B, v1);
        insertView(USER_B, v2);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).isEmpty();
    }

    @Test
    void resetRecommendedHistoryClearsOnlyCurrentUserViews() {
        long v1 = insertVideo(84501L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(84502L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);

        insertView(USER_B, v1);
        insertView(USER_B, v2);
        insertView(USER_A, v1);

        assertThat(repository.findRecommended(USER_B, null, 10)).isEmpty();

        int clearedCount = repository.resetRecommendedHistory(USER_B);

        assertThat(clearedCount).isEqualTo(2);
        assertThat(countViews(USER_B)).isZero();
        assertThat(countViews(USER_A)).isEqualTo(1);
        assertThat(repository.findRecommended(USER_B, null, 10))
                .extracting(RecommendVideoRow::videoId)
                .containsExactly(v1, v2);
    }

    @Test
    void resetRecommendedVideoHistoryClearsOnlySelectedVideoForCurrentUser() {
        long v1 = insertVideo(84601L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(84602L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);

        insertView(USER_B, v1);
        insertView(USER_B, v2);
        insertView(USER_A, v1);

        int clearedCount = repository.resetRecommendedVideoHistory(USER_B, v1);

        assertThat(clearedCount).isEqualTo(1);
        assertThat(countViews(USER_B, v1)).isZero();
        assertThat(countViews(USER_B, v2)).isEqualTo(1);
        assertThat(countViews(USER_A, v1)).isEqualTo(1);
        assertThat(repository.findRecommended(USER_B, null, 10))
                .extracting(RecommendVideoRow::videoId)
                .containsExactly(v1);
    }

    @Test
    void resetRecommendedVideoHistoryReturnsZeroWhenNoViewExists() {
        long v1 = insertVideo(84611L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        insertLikes(v1, 100);

        int clearedCount = repository.resetRecommendedVideoHistory(USER_B, v1);

        assertThat(clearedCount).isZero();
        assertThat(countViews(USER_B)).isZero();
        assertThat(repository.findRecommended(USER_B, null, 10))
                .extracting(RecommendVideoRow::videoId)
                .containsExactly(v1);
    }

    // R05: limit=2 分两页 → 无重复、排序连续
    @Test
    void paginatesWithoutDuplicates() {
        long v1 = insertVideo(85001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(85002L, USER_A, 80L, "2026-06-02 08:00:00", "public");
        long v3 = insertVideo(85003L, USER_A, 60L, "2026-06-03 08:00:00", "public");
        long v4 = insertVideo(85004L, USER_A, 40L, "2026-06-04 08:00:00", "public");
        long v5 = insertVideo(85005L, USER_A, 20L, "2026-06-05 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 80);
        insertLikes(v3, 60);
        insertLikes(v4, 40);
        insertLikes(v5, 20);

        List<RecommendVideoRow> page1 = repository.findRecommended(USER_B, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1).extracting(RecommendVideoRow::videoId)
                .containsExactly(v1, v2);

        CursorCodec.Cursor cursor = new CursorCodec.Cursor(
                page1.get(1).likeCount(), page1.get(1).createdAt(), page1.get(1).videoId()
        );
        List<RecommendVideoRow> page2 = repository.findRecommended(USER_B, cursor, 2);
        assertThat(page2).extracting(RecommendVideoRow::videoId)
                .containsExactly(v3, v4);

        List<RecommendVideoRow> page3 = repository.findRecommended(USER_B,
                new CursorCodec.Cursor(page2.get(1).likeCount(), page2.get(1).createdAt(), page2.get(1).videoId()), 2);
        assertThat(page3).extracting(RecommendVideoRow::videoId)
                .containsExactly(v5);
    }

    // R06: 软删除高赞视频 → 不返回
    @Test
    void excludesDeletedVideos() {
        long v1 = insertVideo(86001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        long v2 = insertVideo(86002L, USER_A, 50L, "2026-06-02 08:00:00", "public");
        insertLikes(v1, 100);
        insertLikes(v2, 50);

        jdbcTemplate.update("UPDATE videos SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", v1);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).extracting(RecommendVideoRow::videoId)
                .containsExactly(v2);
    }

    // R07: visibility=private → 不返回
    @Test
    void excludesPrivateVideos() {
        long v1 = insertVideo(87001L, USER_A, 100L, "2026-06-01 08:00:00", "public");
        insertVideo(87002L, USER_A, 200L, "2026-06-02 08:00:00", "private");
        insertLikes(v1, 100);

        List<RecommendVideoRow> rows = repository.findRecommended(USER_B, null, 10);

        assertThat(rows).extracting(RecommendVideoRow::videoId)
                .containsExactly(v1);
    }
}
