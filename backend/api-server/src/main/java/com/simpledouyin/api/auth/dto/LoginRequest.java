package com.simpledouyin.api.auth.dto;

public record LoginRequest(
        String username,
        String password
) {
}
