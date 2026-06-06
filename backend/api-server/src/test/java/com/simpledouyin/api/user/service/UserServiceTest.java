package com.simpledouyin.api.user.service;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.user.dto.MeResponse;
import com.simpledouyin.api.user.model.UserProfile;
import com.simpledouyin.api.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserProfileRepository userProfileRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userProfileRepository = mock(UserProfileRepository.class);
        userService = new UserService(userProfileRepository);
    }

    @Test
    void returnsCurrentUserProfileWithCounts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContext.setCurrentUserId(request, 1001L);
        when(userProfileRepository.findProfileById(1001L)).thenReturn(Optional.of(
                new UserProfile(1001L, "alice", "Alice", null, 3L, 12L)
        ));

        MeResponse response = userService.currentUser(request);

        assertThat(response.profile().id()).isEqualTo(1001L);
        assertThat(response.profile().username()).isEqualTo("alice");
        assertThat(response.profile().nickname()).isEqualTo("Alice");
        assertThat(response.profile().avatarUrl()).isNull();
        assertThat(response.profile().videoCount()).isEqualTo(3L);
        assertThat(response.profile().likedCount()).isEqualTo(12L);
    }

    @Test
    void rejectsMissingCurrentUserId() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> userService.currentUser(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED)
                );
    }

    @Test
    void rejectsTokenUserNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContext.setCurrentUserId(request, 404L);
        when(userProfileRepository.findProfileById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.currentUser(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND)
                );
    }
}
