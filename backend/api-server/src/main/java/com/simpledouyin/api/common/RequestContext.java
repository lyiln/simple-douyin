package com.simpledouyin.api.common;

import org.slf4j.MDC;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String USER_ID_ATTRIBUTE = RequestContext.class.getName() + ".userId";
    public static final String ERROR_MESSAGE_ATTRIBUTE = RequestContext.class.getName() + ".errorMessage";

    private RequestContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    public static void setCurrentUserId(HttpServletRequest request, long userId) {
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
    }

    public static Long currentUserId(HttpServletRequest request) {
        Object value = request.getAttribute(USER_ID_ATTRIBUTE);
        if (value instanceof Long userId) {
            return userId;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
