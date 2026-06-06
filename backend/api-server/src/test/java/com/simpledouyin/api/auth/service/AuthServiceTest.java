package com.simpledouyin.api.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.dto.RegisterRequest;
import com.simpledouyin.api.auth.dto.RegisterResponse;
import com.simpledouyin.api.auth.model.UserAccount;
import com.simpledouyin.api.auth.repository.UserRepository;
import com.simpledouyin.api.auth.security.PasswordHasher;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String TOKEN_SECRET = "test-only-token-secret-with-enough-length";

    private UserRepository userRepository;
    private PasswordHasher passwordHasher;
    private HmacTokenService tokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordHasher = new PasswordHasher();
        tokenService = new HmacTokenService(new ObjectMapper(), TOKEN_SECRET, 7200);
        authService = new AuthService(userRepository, passwordHasher, tokenService);
    }

    @Test
    void registersUserWithHashedPasswordAndToken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.create(anyString(), anyString(), anyString()))
                .thenReturn(new UserAccount(1001L, "alice", "Alice", null));

        RegisterResponse response = authService.register(
                new RegisterRequest("alice", "Passw0rd!", "Alice")
        );

        ArgumentCaptor<String> passwordHash = ArgumentCaptor.forClass(String.class);
        verify(userRepository).create(
                org.mockito.ArgumentMatchers.eq("alice"),
                passwordHash.capture(),
                org.mockito.ArgumentMatchers.eq("Alice")
        );

        assertThat(passwordHash.getValue()).isNotEqualTo("Passw0rd!");
        assertThat(passwordHasher.matches("Passw0rd!", passwordHash.getValue())).isTrue();
        assertThat(response.user().id()).isEqualTo(1001L);
        assertThat(response.user().username()).isEqualTo("alice");
        assertThat(response.user().nickname()).isEqualTo("Alice");
        assertThat(response.user().avatarUrl()).isNull();
        assertThat(response.expiresIn()).isEqualTo(7200);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(tokenService.parseUserId(response.accessToken())).isEqualTo(1001L);
    }

    @Test
    void rejectsMissingUsername() {
        assertMissing(new RegisterRequest(" ", "Passw0rd!", "Alice"));
    }

    @Test
    void rejectsMissingPassword() {
        assertMissing(new RegisterRequest("alice", " ", "Alice"));
    }

    @Test
    void rejectsMissingNickname() {
        assertMissing(new RegisterRequest("alice", "Passw0rd!", null));
    }

    @Test
    void rejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "Passw0rd!", "Alice")
        ))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT)
                );
    }

    private void assertMissing(RegisterRequest request) {
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.REQUIRED_VALUE_MISSING)
                );
    }
}
