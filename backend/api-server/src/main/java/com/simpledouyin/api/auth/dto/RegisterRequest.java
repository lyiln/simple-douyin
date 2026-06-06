package com.simpledouyin.api.auth.dto;

public record RegisterRequest(
        String username,
        String password,
        String nickname
) {
}
