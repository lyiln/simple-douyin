package com.simpledouyin.api.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simpledouyin.api.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String MULTIPART_OMITTED = "[multipart content omitted]";

    private final RequestLogRepository requestLogRepository;
    private final SensitiveDataSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final int maxBodyLength;
    private final int maxQueryLength;
    private final int maxErrorLength;

    public RequestLoggingFilter(
            RequestLogRepository requestLogRepository,
            SensitiveDataSanitizer sanitizer,
            ObjectMapper objectMapper,
            @Value("${app.request-log.max-body-length:16384}") int maxBodyLength,
            @Value("${app.request-log.max-query-length:1024}") int maxQueryLength,
            @Value("${app.request-log.max-error-length:1024}") int maxErrorLength
    ) {
        this.requestLogRepository = requestLogRepository;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.maxBodyLength = maxBodyLength;
        this.maxQueryLength = maxQueryLength;
        this.maxErrorLength = maxErrorLength;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, maxBodyLength);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        long startedAtNanos = System.nanoTime();
        LocalDateTime createdAt = LocalDateTime.now();
        Throwable failure = null;

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long durationMs = Math.max(1L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
            try {
                persistLog(requestWrapper, responseWrapper, durationMs, createdAt, failure);
            } catch (RuntimeException exception) {
                log.warn("Unexpected request logging failure for requestId={}", RequestContext.requestId(), exception);
            } finally {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private void persistLog(
            ContentCachingRequestWrapper request,
            ContentCachingResponseWrapper response,
            long durationMs,
            LocalDateTime createdAt,
            Throwable failure
    ) {
        RequestLogEntry entry = new RequestLogEntry(
                RequestContext.requestId(),
                resolveUserId(request),
                limit(request.getMethod(), 10),
                limit(request.getRequestURI(), 255),
                limit(sanitizer.sanitize(request.getQueryString()), maxQueryLength),
                requestBody(request),
                responseBody(response),
                response.getStatus(),
                businessCode(response),
                durationMs,
                limit(clientIp(request), 64),
                limit(request.getHeader("User-Agent"), 512),
                errorMessage(request, failure),
                createdAt
        );

        try {
            requestLogRepository.save(entry);
        } catch (DataAccessException exception) {
            log.warn("Failed to persist request log for requestId={}", entry.requestId(), exception);
        }
    }

    private Long resolveUserId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestContext.USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String requestBody(ContentCachingRequestWrapper request) {
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return MULTIPART_OMITTED + "; contentLength=" + request.getContentLengthLong();
        }
        return cachedBody(request.getContentAsByteArray(), request.getCharacterEncoding());
    }

    private String responseBody(ContentCachingResponseWrapper response) {
        return cachedBody(response.getContentAsByteArray(), response.getCharacterEncoding());
    }

    private String cachedBody(byte[] content, String characterEncoding) {
        if (content.length == 0) {
            return null;
        }
        Charset charset = StandardCharsets.UTF_8;
        if (StringUtils.hasText(characterEncoding)) {
            try {
                charset = Charset.forName(characterEncoding);
            } catch (Exception ignored) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return limit(sanitizer.sanitize(new String(content, charset)), maxBodyLength);
    }

    private Integer businessCode(ContentCachingResponseWrapper response) {
        byte[] content = response.getContentAsByteArray();
        if (content.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode code = root.get("code");
            return code != null && code.canConvertToInt() ? code.intValue() : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private String errorMessage(HttpServletRequest request, Throwable failure) {
        Object handledError = request.getAttribute(RequestContext.ERROR_MESSAGE_ATTRIBUTE);
        if (handledError != null) {
            return limit(sanitizer.sanitize(handledError.toString()), maxErrorLength);
        }
        if (failure != null) {
            return limit(sanitizer.sanitize(failure.getMessage()), maxErrorLength);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int comma = forwardedFor.indexOf(',');
            return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
        }
        return request.getRemoteAddr();
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
