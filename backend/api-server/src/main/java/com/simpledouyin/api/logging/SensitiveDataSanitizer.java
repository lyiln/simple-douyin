package com.simpledouyin.api.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SensitiveDataSanitizer {

    private static final String MASK = "***";
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password",
            "passwordhash",
            "token",
            "accesstoken",
            "refreshtoken",
            "authorization",
            "secret"
    );
    private static final Pattern TEXT_SECRET_PATTERN = Pattern.compile(
            "(?i)(password|password_hash|token|access_token|accessToken|refresh_token|refreshToken|authorization|secret)"
                    + "(\\s*[=:]\\s*|\\\"\\s*:\\s*\\\")"
                    + "([^&\\s,}\\\"]+)"
    );

    private final ObjectMapper objectMapper;

    public SensitiveDataSanitizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        try {
            JsonNode root = objectMapper.readTree(value);
            sanitizeNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ignored) {
            return sanitizeText(value);
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitive(field.getKey())) {
                    objectNode.put(field.getKey(), MASK);
                } else {
                    sanitizeNode(field.getValue());
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            arrayNode.forEach(this::sanitizeNode);
        }
    }

    private boolean isSensitive(String key) {
        String normalized = key
                .replace("_", "")
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }

    private String sanitizeText(String value) {
        Matcher matcher = TEXT_SECRET_PATTERN.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + MASK)
            );
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
