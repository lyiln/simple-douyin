package com.simpledouyin.api.video.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VideoRepository 集成测试 — 使用真实 MySQL 验证 SQL 幂等性和计数器一致性。
 * 需要本地 MySQL (simple_douyin) 可用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VideoRepositoryTest {

    private static final long TEST_USER_ID = 90001L;
    private static final long TEST_VIDEO_ID = 90001L;
    private static final String TEST_USERNAME = "repo_test_user";
    private static final int TEST_DURATION_MS = 5000;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 插入测试用户
        jdbcTemplate.update(
                "INSERT IGNORE INTO users (id, username, password_hash, nickname, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                TEST_USER_ID, TEST_USERNAME, "hashed", "TestUser"
        );
        // 插入测试视频
        jdbcTemplate.update(
                "INSERT IGNORE INTO videos (id, author_id, caption, video_url, cover_url, duration_ms, "
                        + "like_count, view_count, comment_count, visibility, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 'public', 'published', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                TEST_VIDEO_ID, TEST_USER_ID, "test video", "/uploads/test.mp4", "/uploads/test.jpg", TEST_DURATION_MS
        );
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据（按外键依赖逆序删除）
        jdbcTemplate.update("DELETE FROM video_likes WHERE user_id = ? OR video_id = ?",
                TEST_USER_ID, TEST_VIDEO_ID);
        jdbcTemplate.update("DELETE FROM video_views WHERE user_id = ? OR video_id = ?",
                TEST_USER_ID, TEST_VIDEO_ID);
        jdbcTemplate.update("DELETE FROM videos WHERE id = ?", TEST_VIDEO_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", TEST_USER_ID);
    }

    // ==================== 点赞幂等性测试 ====================

    @Test
    void likeFirstTimeReturnsTrueAndIncreasesCount() {
        boolean created = videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);
        assertThat(created).isTrue();

        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void likeSecondTimeReturnsFalseAndCountStaysOne() {
        // 第一次点赞
        videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);
        // 第二次点赞（幂等）
        boolean created = videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);

        assertThat(created).isFalse();
        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void likeMultipleUsersEachIncrementsCount() {
        long user2 = TEST_USER_ID + 1;
        long user3 = TEST_USER_ID + 2;

        // 插入额外测试用户
        jdbcTemplate.update(
                "INSERT IGNORE INTO users (id, username, password_hash, nickname, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                user2, TEST_USERNAME + "_2", "hashed", "TestUser2"
        );
        jdbcTemplate.update(
                "INSERT IGNORE INTO users (id, username, password_hash, nickname, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                user3, TEST_USERNAME + "_3", "hashed", "TestUser3"
        );

        try {
            videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);
            videoRepository.like(user2, TEST_VIDEO_ID);
            videoRepository.like(user3, TEST_VIDEO_ID);

            long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
            assertThat(count).isEqualTo(3L);
        } finally {
            jdbcTemplate.update("DELETE FROM video_likes WHERE user_id IN (?, ?)", user2, user3);
            jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", user2, user3);
        }
    }

    // ==================== 取消点赞幂等性测试 ====================

    @Test
    void unlikeAfterLikeReturnsTrueAndDecreasesCount() {
        videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);

        boolean removed = videoRepository.unlike(TEST_USER_ID, TEST_VIDEO_ID);
        assertThat(removed).isTrue();

        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void unlikeWithoutLikeReturnsFalseAndCountStaysZero() {
        boolean removed = videoRepository.unlike(TEST_USER_ID, TEST_VIDEO_ID);
        assertThat(removed).isFalse();

        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void unlikeTwiceSecondCallReturnsFalse() {
        videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);

        boolean first = videoRepository.unlike(TEST_USER_ID, TEST_VIDEO_ID);
        boolean second = videoRepository.unlike(TEST_USER_ID, TEST_VIDEO_ID);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(0L);
    }

    // ==================== 访问记录幂等性测试 ====================

    @Test
    void recordViewFirstTimeReturnsTrueAndIncreasesCount() {
        boolean created = videoRepository.recordView(
                TEST_USER_ID, TEST_VIDEO_ID, "recommended_feed", TEST_DURATION_MS);
        assertThat(created).isTrue();

        long count = videoRepository.findViewCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void recordViewSecondTimeReturnsFalseAndCountStaysOne() {
        videoRepository.recordView(TEST_USER_ID, TEST_VIDEO_ID, "recommended_feed", TEST_DURATION_MS);

        boolean created = videoRepository.recordView(
                TEST_USER_ID, TEST_VIDEO_ID, "search_result", 3000);
        assertThat(created).isFalse();

        long count = videoRepository.findViewCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(1L);
    }

    // ==================== TOCTOU 防护测试 ====================

    @Test
    void likeOnNonExistentVideoReturnsFalse() {
        long nonExistentVideoId = 99999L;
        boolean created = videoRepository.like(TEST_USER_ID, nonExistentVideoId);
        assertThat(created).isFalse();

        // 确认没有插入脏数据
        long count = videoRepository.findLikeCount(nonExistentVideoId);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void likeOnDeletedVideoReturnsFalse() {
        // 软删除测试视频
        jdbcTemplate.update("UPDATE videos SET deleted_at = CURRENT_TIMESTAMP(3) WHERE id = ?", TEST_VIDEO_ID);

        try {
            boolean created = videoRepository.like(TEST_USER_ID, TEST_VIDEO_ID);
            assertThat(created).isFalse();
        } finally {
            // 恢复（DELETE + IGNORE INSERT 重建）
            jdbcTemplate.update("DELETE FROM videos WHERE id = ?", TEST_VIDEO_ID);
            jdbcTemplate.update(
                    "INSERT INTO videos (id, author_id, caption, video_url, cover_url, duration_ms, "
                            + "like_count, view_count, comment_count, visibility, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 0, 0, 0, 'public', 'published', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))",
                    TEST_VIDEO_ID, TEST_USER_ID, "test video", "/uploads/test.mp4", "/uploads/test.jpg", TEST_DURATION_MS
            );
        }
    }

    // ==================== 计数器一致性测试 ====================

    @Test
    void findLikeCountReturnsZeroForVideoWithNoLikes() {
        long count = videoRepository.findLikeCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void findViewCountReturnsZeroForVideoWithNoViews() {
        long count = videoRepository.findViewCount(TEST_VIDEO_ID);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void videoExistsReturnsTrueForExistingVideo() {
        assertThat(videoRepository.videoExists(TEST_VIDEO_ID)).isTrue();
    }

    @Test
    void videoExistsReturnsFalseForNonExistentVideo() {
        assertThat(videoRepository.videoExists(99999L)).isFalse();
    }
}
