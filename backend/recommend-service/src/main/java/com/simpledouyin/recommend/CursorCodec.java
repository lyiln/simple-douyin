package com.simpledouyin.recommend;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {
    }

    public static String encode(Cursor cursor) {
        String raw = cursor.likeCount() + SEPARATOR
                + cursor.createdAt().toString() + SEPARATOR
                + cursor.videoId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        String raw = new String(
                Base64.getUrlDecoder().decode(cursor),
                StandardCharsets.UTF_8
        );
        String[] parts = raw.split("\\" + SEPARATOR, -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid cursor format");
        }
        return new Cursor(
                Long.parseLong(parts[0]),
                LocalDateTime.parse(parts[1]),
                Long.parseLong(parts[2])
        );
    }

    public record Cursor(long likeCount, LocalDateTime createdAt, long videoId) {
    }
}
