package com.example.douyin.data

import com.example.douyin.R
import com.example.douyin.model.Comment
import com.example.douyin.model.VideoPost
import com.example.douyin.network.model.CommentResponse
import com.example.douyin.network.model.VideoPostResponse
import kotlin.math.min

fun VideoPostResponse.toUiPost(): VideoPost {
    val fallbackIndex = (id % 3).toInt()
    val fallbackVideo = when (fallbackIndex) {
        0 -> R.raw.video_mountain
        1 -> R.raw.video_gradient
        else -> R.raw.video_soft_dark
    }
    val fallbackCover = when (fallbackIndex) {
        0 -> R.drawable.cover_mountain
        1 -> R.drawable.cover_gradient
        else -> R.drawable.cover_soft_dark
    }
    val fallbackAvatar = when (fallbackIndex) {
        0 -> R.drawable.avatar_cyan_pink
        1 -> R.drawable.avatar_dark_logo
        else -> R.drawable.avatar_mountain
    }

    return VideoPost(
        id = id.toString(),
        author = author.nickname.ifBlank { author.username },
        handle = "@${author.username}",
        caption = caption,
        topic = "#推荐",
        music = "原声",
        likes = likeCount.safeInt(),
        comments = commentCount.safeInt(),
        shares = 0,
        videoRes = fallbackVideo,
        coverRes = fallbackCover,
        avatarRes = fallbackAvatar,
        videoUrl = videoUrl,
        coverUrl = coverUrl,
        avatarUrl = author.avatarUrl,
        isOwner = viewerState.owner,
        isViewed = viewerState.viewed,
        isLiked = viewerState.liked,
        remoteId = id
    )
}

fun CommentResponse.toUiComment(): Comment {
    return Comment(
        author = author.nickname.ifBlank { author.username },
        content = content,
        time = createdAt.toDisplayTime()
    )
}

private fun Long.safeInt(): Int = min(this, Int.MAX_VALUE.toLong()).toInt()

private fun String.toDisplayTime(): String {
    return replace("T", " ")
        .replace("Z", "")
        .take(16)
}
