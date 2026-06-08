package com.simpledouyin.api.auth.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
public class HmacTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expiresInSeconds;
    private final Clock clock;

    @Autowired
    public HmacTokenService(
            ObjectMapper objectMapper,
            @Value("${app.auth.token.secret}") String secret,
            @Value("${app.auth.token.expires-in-seconds:7200}") long expiresInSeconds
    ) {
        this(objectMapper, secret, expiresInSeconds, Clock.systemUTC());
    }

    public HmacTokenService(
            ObjectMapper objectMapper,
            String secret,
            long expiresInSeconds,
            Clock clock
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Token secret must not be blank");
        }
        if (expiresInSeconds <= 0) {
            throw new IllegalArgumentException("Token expiration must be positive");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expiresInSeconds = expiresInSeconds;
        this.clock = clock;
    }

    public IssuedToken issue(long userId) {
        Instant issuedAt = clock.instant();
        long expiresAt = issuedAt.plusSeconds(expiresInSeconds).getEpochSecond();
        String payload = json(Map.of(
                "sub", Long.toString(userId),
                "iat", issuedAt.getEpochSecond(),
                "exp", expiresAt
        ));
        String unsignedToken = HEADER + "." + encode(payload);
        return new IssuedToken(unsignedToken + "." + sign(unsignedToken), expiresInSeconds);
    }

    public long parseUserId(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid token");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        byte[] expectedSignature = BASE64_DECODER.decode(sign(unsignedToken));
        byte[] actualSignature = BASE64_DECODER.decode(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        try {
            JsonNode payload = objectMapper.readTree(BASE64_DECODER.decode(parts[1]));
            if (payload.path("exp").asLong(0) <= clock.instant().getEpochSecond()) {
                throw new IllegalArgumentException("Token expired");
            }
            return Long.parseLong(payload.path("sub").asText());
        } catch (IOException | NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid token payload", exception);
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return BASE64_ENCODER.encodeToString(
                    mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign token", exception);
        }
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to build token payload", exception);
        }
    }

    private static String encode(String value) {
        return BASE64_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
