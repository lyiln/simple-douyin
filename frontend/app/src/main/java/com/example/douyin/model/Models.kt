package com.example.douyin.model

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes

data class VideoPost(
    val id: String,
    val author: String,
    val handle: String,
    val caption: String,
    val topic: String,
    val music: String,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    @RawRes val videoRes: Int? = null,
    @DrawableRes val coverRes: Int,
    @DrawableRes val avatarRes: Int,
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val avatarUrl: String? = null,
    val isOwner: Boolean = false,
    val isViewed: Boolean = false,
    val isLiked: Boolean = false,
    val remoteId: Long? = id.toLongOrNull()
)

data class Comment(
    val author: String,
    val content: String,
    val time: String
)
