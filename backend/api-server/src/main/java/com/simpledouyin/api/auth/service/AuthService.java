package com.simpledouyin.api.auth.service;

import com.simpledouyin.api.auth.dto.RegisterRequest;
import com.simpledouyin.api.auth.dto.RegisterResponse;
import com.simpledouyin.api.auth.dto.UserSummary;
import com.simpledouyin.api.auth.model.UserAccount;
import com.simpledouyin.api.auth.repository.UserRepository;
import com.simpledouyin.api.auth.security.PasswordHasher;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.auth.token.IssuedToken;
import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
public class AuthService {

    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_NICKNAME_LENGTH = 64;
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final HmacTokenService tokenService;

    public AuthService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            HmacTokenService tokenService
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (request == null) {
            throw missing("request");
        }

        String username = requiredTrimmed(request.username(), "username");
        String password = required(request.password(), "password");
        String nickname = requiredTrimmed(request.nickname(), "nickname");
        validateLengths(username, password, nickname);

        if (userRepository.existsByUsername(username)) {
            throw duplicateUsername();
        }

        UserAccount user;
        try {
            user = userRepository.create(
                    username,
                    passwordHasher.hash(password),
                    nickname
            );
        } catch (DuplicateKeyException exception) {
            throw duplicateUsername();
        }

        IssuedToken token = tokenService.issue(user.id());
        return new RegisterResponse(
                new UserSummary(
                        user.id(),
                        user.username(),
                        user.nickname(),
                        user.avatarUrl()
                ),
                token.value(),
                token.expiresIn()
        );
    }

    private String requiredTrimmed(String value, String field) {
        return required(value, field).trim();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw missing(field);
        }
        return value;
    }

    private void validateLengths(String username, String password, String nickname) {
        if (username.length() > MAX_USERNAME_LENGTH
                || nickname.length() > MAX_NICKNAME_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }
    }

    private BusinessException missing(String field) {
        return new BusinessException(
                ErrorCode.REQUIRED_VALUE_MISSING,
                field + " is required"
        );
    }

    private BusinessException duplicateUsername() {
        return new BusinessException(
                ErrorCode.CONFLICT,
                "username already exists"
        );
    }
}
