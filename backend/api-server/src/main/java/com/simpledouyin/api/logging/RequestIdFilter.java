package com.simpledouyin.api.logging;

import com.simpledouyin.api.common.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_ID_LENGTH = 64;
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]+");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null
                && !candidate.isBlank()
                && candidate.length() <= MAX_REQUEST_ID_LENGTH
                && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
