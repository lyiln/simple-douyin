package com.simpledouyin.api.user.repository;

import com.simpledouyin.api.user.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileRepositoryTest {

    @Test
    void queriesProfileCountsFromUndeletedVideosAndUserLikes() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(
                any(String.class),
                any(RowMapper.class),
                eq(1001L)
        )).thenReturn(List.of(new UserProfile(1001L, "alice", "Alice", null, 3L, 12L)));
        UserProfileRepository repository = new UserProfileRepository(jdbcTemplate);

        Optional<UserProfile> profile = repository.findProfileById(1001L);

        assertThat(profile).isPresent();
        assertThat(profile.orElseThrow().videoCount()).isEqualTo(3L);
        assertThat(profile.orElseThrow().likedCount()).isEqualTo(12L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(1001L));
        assertThat(sql.getValue()).contains("FROM videos v");
        assertThat(sql.getValue()).contains("v.author_id = u.id");
        assertThat(sql.getValue()).contains("v.deleted_at IS NULL");
        assertThat(sql.getValue()).contains("FROM video_likes vl");
        assertThat(sql.getValue()).contains("vl.user_id = u.id");
        assertThat(sql.getValue()).doesNotContain("password_hash");
    }
}
