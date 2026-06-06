package com.simpledouyin.api.auth.token;

public record IssuedToken(
        String value,
        long expiresIn
) {
}
