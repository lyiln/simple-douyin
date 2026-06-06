package com.simpledouyin.api.video.service;

import com.simpledouyin.api.video.dto.AuthorSummary;
import com.simpledouyin.api.video.dto.VideoPostResponse;
import com.simpledouyin.api.video.dto.ViewerStateResponse;
import com.simpledouyin.api.video.model.VideoPost;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
public class VideoPostAssembler {

    public VideoPostResponse toResponse(VideoPost post, long viewerId) {
        return new VideoPostResponse(
                post.id(),
                new AuthorSummary(
                        post.authorId(),
                        post.authorUsername(),
                        post.authorNickname(),
                        post.authorAvatarUrl()
                ),
                post.caption(),
                post.videoUrl(),
                post.coverUrl(),
                post.durationMs(),
                post.likeCount(),
                post.viewCount(),
                post.commentCount(),
                post.visibility(),
                post.status(),
                post.createdAt().atOffset(ZoneOffset.UTC).toString(),
                new ViewerStateResponse(
                        post.liked(),
                        post.viewed(),
                        post.authorId() == viewerId
                )
        );
    }
}
