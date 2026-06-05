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
    @RawRes val videoRes: Int,
    @DrawableRes val coverRes: Int,
    @DrawableRes val avatarRes: Int
)

data class Comment(
    val author: String,
    val content: String,
    val time: String
)

data class ChatPreview(
    val title: String,
    val message: String,
    val time: String,
    @DrawableRes val avatarRes: Int,
    val unread: Int = 0
)

enum class AppScreen {
    Home,
    Friends,
    Publish,
    Messages,
    Profile,
    Search
}
