package com.simpledouyin.api.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.auth.token.HmacTokenService;
import com.simpledouyin.api.common.ApiResponse;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class BearerAuthenticationFilter extends OncePerRequestFilter {

    private static final String LOGOUT_PATH = "/api/v1/auth/logout";
    private static final String BEARER_PREFIX = "Bearer ";

    private final HmacTokenService tokenService;
    private final ObjectMapper objectMapper;

    public BearerAuthenticationFilter(HmacTokenService tokenService, ObjectMapper objectMapper) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresAuthentication(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            writeUnauthorized(request, response);
            return;
        }

        long userId;
        try {
            userId = tokenService.parseUserId(token);
        } catch (RuntimeException exception) {
            writeUnauthorized(request, response);
            return;
        }

        RequestContext.setCurrentUserId(request, userId);
        filterChain.doFilter(request, response);
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && LOGOUT_PATH.equals(request.getRequestURI());
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        request.setAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE, errorCode.message());
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.failure(errorCode));
    }
}
