package com.simpledouyin.api.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.dto.LoginRequest;
import com.simpledouyin.api.auth.dto.LoginResponse;
import com.simpledouyin.api.auth.dto.LogoutResponse;
import com.simpledouyin.api.auth.dto.RegisterRequest;
import com.simpledouyin.api.auth.dto.RegisterResponse;
import com.simpledouyin.api.auth.dto.UserSummary;
import com.simpledouyin.api.auth.security.BearerAuthenticationFilter;
import com.simpledouyin.api.auth.service.AuthService;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.GlobalExceptionHandler;
import com.simpledouyin.api.logging.RequestIdFilter;
import com.simpledouyin.api.logging.RequestLogEntry;
import com.simpledouyin.api.logging.RequestLogRepository;
import com.simpledouyin.api.logging.RequestLoggingFilter;
import com.simpledouyin.api.logging.SensitiveDataSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private static final String REQUEST_ID = "register-test-request";
    private static final String PLAIN_PASSWORD = "Passw0rd!";
    private static final String ACCESS_TOKEN = "header.payload.signature";
    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private AuthService authService;
    private RequestLogRepository requestLogRepository;
    private ObjectMapper objectMapper;
    private HmacTokenService tokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        objectMapper = new ObjectMapper();
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
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(
                        new RequestIdFilter(),
                        loggingFilter,
                        new BearerAuthenticationFilter(tokenService, objectMapper)
                )
                .build();
    }

    @Test
    void returnsCreatedUnifiedResponseAndSanitizesRequestLog() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(
                new RegisterResponse(
                        new UserSummary(1001L, "alice", "Alice", null),
                        ACCESS_TOKEN,
                        7200
                )
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Passw0rd!",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.user.id").value(1001))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.user.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().requestBody()).contains("\"password\":\"***\"");
        assertThat(logEntry.getValue().requestBody()).doesNotContain(PLAIN_PASSWORD);
        assertThat(logEntry.getValue().responseBody()).doesNotContain(ACCESS_TOKEN);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(201);
    }

    @Test
    void returnsConflictForDuplicateUsername() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(
                new BusinessException(ErrorCode.CONFLICT, "username already exists")
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Passw0rd!",
                                  "nickname": "Alice"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void returnsOkLoginResponseAndSanitizesSensitiveLogFields() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(
                new LoginResponse(
                        new UserSummary(1001L, "alice", "Alice", null),
                        ACCESS_TOKEN,
                        7200
                )
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "Passw0rd!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.user.id").value(1001))
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.user.nickname").value("Alice"))
                .andExpect(jsonPath("$.data.user.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().requestId()).isEqualTo(REQUEST_ID);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/auth/login");
        assertThat(logEntry.getValue().requestBody()).contains("\"password\":\"***\"");
        assertThat(logEntry.getValue().requestBody()).doesNotContain(PLAIN_PASSWORD);
        assertThat(logEntry.getValue().responseBody()).doesNotContain(ACCESS_TOKEN);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
    }

    @Test
    void returnsUnauthorizedForInvalidLogin() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BusinessException(ErrorCode.UNAUTHORIZED)
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("unauthorized"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void returnsOkLogoutResponseForValidBearerTokenAndLogsUserId() throws Exception {
        String accessToken = tokenService.issue(1001L).value();
        when(authService.logout()).thenReturn(new LogoutResponse(true));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Request-Id", REQUEST_ID)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.loggedOut").value(true));

        ArgumentCaptor<RequestLogEntry> logEntry = ArgumentCaptor.forClass(RequestLogEntry.class);
        verify(requestLogRepository).save(logEntry.capture());

        assertThat(logEntry.getValue().userId()).isEqualTo(1001L);
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/auth/logout");
        assertThat(logEntry.getValue().requestBody()).isNull();
        assertThat(logEntry.getValue().responseBody()).doesNotContain(accessToken);
        assertThat(logEntry.getValue().businessCode()).isZero();
        assertThat(logEntry.getValue().statusCode()).isEqualTo(200);
    }

    @Test
    void returnsUnauthorizedForMissingAuthorizationOnLogout() throws Exception {
        assertUnauthorizedLogout(null);
    }

    @Test
    void returnsUnauthorizedForNonBearerAuthorizationOnLogout() throws Exception {
        assertUnauthorizedLogout("Basic abc123");
    }

    @Test
    void returnsUnauthorizedForInvalidSignatureOnLogout() throws Exception {
        String badToken = new HmacTokenService(
                objectMapper,
                "another-test-only-token-secret",
                7200
        ).issue(1001L).value();

        assertUnauthorizedLogout("Bearer " + badToken);
    }

    @Test
    void returnsUnauthorizedForExpiredTokenOnLogout() throws Exception {
        HmacTokenService expiredIssuer = new HmacTokenService(
                objectMapper,
                TOKEN_SECRET,
                1,
                Clock.fixed(Instant.now().minusSeconds(10), ZoneOffset.UTC)
        );

        assertUnauthorizedLogout("Bearer " + expiredIssuer.issue(1001L).value());
    }

    private void assertUnauthorizedLogout(String authorization) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request =
                post("/api/v1/auth/logout")
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
        assertThat(logEntry.getValue().path()).isEqualTo("/api/v1/auth/logout");
        assertThat(logEntry.getValue().responseBody()).contains("\"code\":40101");
        if (authorization != null && logEntry.getValue().requestBody() != null) {
            assertThat(logEntry.getValue().requestBody()).doesNotContain(authorization);
        }
        if (authorization != null) {
            assertThat(logEntry.getValue().responseBody()).doesNotContain(authorization);
        }
        verify(authService, never()).logout();
    }
}
