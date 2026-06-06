package com.simpledouyin.api.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.dto.RegisterRequest;
import com.simpledouyin.api.auth.dto.RegisterResponse;
import com.simpledouyin.api.auth.dto.UserSummary;
import com.simpledouyin.api.auth.service.AuthService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    private AuthService authService;
    private RequestLogRepository requestLogRepository;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        requestLogRepository = mock(RequestLogRepository.class);
        objectMapper = new ObjectMapper();
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
                .addFilters(new RequestIdFilter(), loggingFilter)
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
}
