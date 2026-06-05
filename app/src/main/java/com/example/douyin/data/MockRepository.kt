package com.example.douyin.data

import com.example.douyin.R
import com.example.douyin.model.ChatPreview
import com.example.douyin.model.Comment
import com.example.douyin.model.VideoPost

object MockRepository {
    fun initialPosts(): List<VideoPost> = listOf(
        VideoPost(
            id = "mountain-night",
            author = "山野记录员",
            handle = "@mountain_notes",
            caption = "夜里的山风和星空，适合循环看七秒。",
            topic = "#风景 #治愈 #校园大作业",
            music = "山谷回声 - 原声",
            likes = 32800,
            comments = 524,
            shares = 94,
            videoRes = R.raw.video_mountain,
            coverRes = R.drawable.cover_mountain,
            avatarRes = R.drawable.avatar_cyan_pink
        ),
        VideoPost(
            id = "city-gradient",
            author = "城市灵感站",
            handle = "@daily_city",
            caption = "把今天的碎片剪成一个短视频。",
            topic = "#城市 #剪辑 #生活感",
            music = "霓虹节拍 - 本地素材",
            likes = 12600,
            comments = 188,
            shares = 63,
            videoRes = R.raw.video_gradient,
            coverRes = R.drawable.cover_gradient,
            avatarRes = R.drawable.avatar_dark_logo
        ),
        VideoPost(
            id = "campus-work",
            author = "前端展示小组",
            handle = "@compose_demo",
            caption = "只做前端展示：刷视频、点赞、评论、分享、发布本地作品。",
            topic = "#Android #Compose #短视频平台",
            music = "Compose Demo - 原声",
            likes = 9800,
            comments = 76,
            shares = 31,
            videoRes = R.raw.video_soft_dark,
            coverRes = R.drawable.cover_soft_dark,
            avatarRes = R.drawable.avatar_mountain
        )
    )

    fun commentsFor(postId: String): List<Comment> = when (postId) {
        "mountain-night" -> listOf(
            Comment("路过的同学", "这个封面氛围感很适合短视频首页。", "刚刚"),
            Comment("剪辑课代表", "滑动切换和自动播放演示很直观。", "2分钟前"),
            Comment("山里有风", "本地视频也能把流程讲清楚。", "5分钟前")
        )
        "city-gradient" -> listOf(
            Comment("夜跑计划", "这个页面像真的 feed 了。", "1分钟前"),
            Comment("UI观察员", "底部操作区和信息区层次清楚。", "9分钟前")
        )
        else -> listOf(
            Comment("答辩助手", "无后端版本适合课堂展示。", "刚刚"),
            Comment("Compose用户", "发布后插入 feed 这个闭环不错。", "3分钟前")
        )
    }

    fun chatPreviews(): List<ChatPreview> = listOf(
        ChatPreview("系统通知", "你的本地作品已发布到推荐流", "15:20", R.drawable.avatar_dark_logo, 1),
        ChatPreview("前端展示小组", "今天主要演示刷视频和发布流程", "14:12", R.drawable.avatar_cyan_pink),
        ChatPreview("山野记录员", "新素材已经加入本地 mock 数据", "昨天", R.drawable.avatar_mountain)
    )
}
