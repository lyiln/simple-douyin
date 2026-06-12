package com.simpledouyin.api.comment.model;

import java.time.LocalDateTime;

public class Comment {

    private final long id;
    private final long videoId;
    private final long authorId;
    private final String authorUsername;
    private final String authorNickname;
    private final String authorAvatarUrl;
    private final String content;
    private final LocalDateTime createdAt;

    public Comment(
            long id,
            long videoId,
            long authorId,
            String authorUsername,
            String authorNickname,
            String authorAvatarUrl,
            String content,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.videoId = videoId;
        this.authorId = authorId;
        this.authorUsername = authorUsername;
        this.authorNickname = authorNickname;
        this.authorAvatarUrl = authorAvatarUrl;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long id() {
        return id;
    }

    public long videoId() {
        return videoId;
    }

    public long authorId() {
        return authorId;
    }

    public String authorUsername() {
        return authorUsername;
    }

    public String authorNickname() {
        return authorNickname;
    }

    public String authorAvatarUrl() {
        return authorAvatarUrl;
    }

    public String content() {
        return content;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
