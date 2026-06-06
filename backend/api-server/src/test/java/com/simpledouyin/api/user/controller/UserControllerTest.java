package com.simpledouyin.api.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.security.BearerAuthenticationFilter;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import com.simpledouyin.api.user.dto.MeResponse;
import com.simpledouyin.api.user.dto.UserProfileResponse;
import com.simpledouyin.api.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private static final String REQUEST_ID = "me-test-request";
    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private UserService userService;
    private RequestLogRepository requestLogRepository;
    private HmacTokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        tokenService = new HmacTokenService(objectMapper, TOKEN_SECRET, 7200);
        SensitiveDataSanitizer sanitizer = new SensitiveDataSanitizer(objectMapper);
        RequestLoggingFilter loggingFilter = new RequestLoggingFilter(
                requestLogRepository,
                sanitizer,
                objectMapper,
                16384,
                1024,
                1024
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter,
                        new BearerAuthenticationFilter(tokenService, objectMapper)
                )
                .build();
    }

    @Test
    void returnsCurrentUserProfileAndLogsUserId() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(userService.currentUser(any(HttpServletRequest.class))).thenReturn(
                new MeResponse(new UserProfileResponse(
                        1001L,
                        "alice",
                        "Alice",
                        null,
                        3L,
                        12L
                ))
        );

        mockMvc.perform(get("/api/v1/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.profile.id").value(1001))
                .andExpect(jsonPath("$.data.profile.username").value("alice"))
                .andExpect(jsonPath("$.data.profile.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.profile.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.profile.videoCount").value(3))
                .andExpect(jsonPath("$.data.profile.likedCount").value(12))
                .andExpect(jsonPath("$.data.profile.password_hash").doesNotExist())
                .andExpect(jsonPath("$.data.profile.passwordHash").doesNotExist());

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().method()).isEqualTo("GET");
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/me");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().durationMs()).isPositive();
        assertThat(logEntry.getValue().responseBody()).doesNotContain("password_hash");
    }

    @Test
    void returnsUnauthorizedForMissingToken() throws Exception {
        assertUnauthorizedMe(null);
    }

    @Test
    void returnsUnauthorizedForInvalidToken() throws Exception {
        assertUnauthorizedMe("Bearer invalid-token");
    }

    @Test
    void returnsUserNotFoundForTokenUserMissing() throws Exception {
        String accessToken = tokenService.issue(404L).value();
        when(userService.currentUser(any(HttpServletRequest.class))).thenThrow(
                new BusinessException(ErrorCode.USER_NOT_FOUND)
        );

        mockMvc.perform(get("/api/v1/me")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(40402))
                .andExpect(jsonPath("$.message").value("user not found"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isEqualTo(404L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/me");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(404);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40402);
    }

    private void assertUnauthorizedMe(String authorization) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                get("/api/v1/me")
                        .header("X-Request-Id", REQUEST_ID);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("unauthorized"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isNull();
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/me");
        assertThat(logEntry.getValue().statusCode()).isEqualTo(401);
        assertThat(logEntry.getValue().businessCode()).isEqualTo(40101);
        verify(userService, never()).currentUser(any(HttpServletRequest.class));
    }
}
