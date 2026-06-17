package com.example.douyin.data

import com.example.douyin.R
import com.example.douyin.model.VideoPost

object PublishAssetRepository {
    fun assets(): List<VideoPost> = listOf(
        VideoPost(
            id = "asset-mountain-night",
            author = "山野记录员",
            handle = "@mountain_notes",
            caption = "夜里的山风和星空。",
            topic = "#风景 #短视频",
            music = "山谷回声 - 原声",
            likes = 0,
            comments = 0,
            shares = 0,
            videoRes = R.raw.video_mountain,
            coverRes = R.drawable.cover_mountain,
            avatarRes = R.drawable.avatar_cyan_pink
        ),
        VideoPost(
            id = "asset-city-life",
            author = "城市记录",
            handle = "@daily_city",
            caption = "把今天的生活片段剪成短视频。",
            topic = "#城市 #生活",
            music = "霓虹节拍 - 原声",
            likes = 0,
            comments = 0,
            shares = 0,
            videoRes = R.raw.video_gradient,
            coverRes = R.drawable.cover_gradient,
            avatarRes = R.drawable.avatar_dark_logo
        ),
        VideoPost(
            id = "asset-campus",
            author = "校园影像",
            handle = "@campus_video",
            caption = "校园里的片刻光影。",
            topic = "#校园 #记录",
            music = "校园短片 - 原声",
            likes = 0,
            comments = 0,
            shares = 0,
            videoRes = R.raw.video_soft_dark,
            coverRes = R.drawable.cover_soft_dark,
            avatarRes = R.drawable.avatar_mountain
        )
    )
}
