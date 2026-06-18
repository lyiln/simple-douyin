package com.example.douyin.network.model

/** 后端统一响应外层 */
data class ApiResponseWrapper<T>(
    val code: Int,
    val message: String,
    val data: T?,
    val requestId: String?
)

/** 注册/登录请求 */
data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String
)

/** 注册/登录响应 data */
data class AuthData(
    val user: UserInfo,
    val accessToken: String,
    val expiresIn: Long
)

/** 用户信息 */
data class UserInfo(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String?
)

/** GET /api/v1/me 响应 */
data class UserProfile(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val videoCount: Long,
    val likedCount: Long
)

data class MeData(
    val profile: UserProfile
)

/** 作者摘要 */
data class AuthorSummary(
    val id: Long,
    val username: String,
    val nickname: String,
    val avatarUrl: String?
)

/** 视频帖（后端返回） */
data class VideoPostResponse(
    val id: Long,
    val author: AuthorSummary,
    val caption: String,
    val videoUrl: String,
    val coverUrl: String?,
    val durationMs: Int?,
    val likeCount: Long,
    val viewCount: Long,
    val commentCount: Long,
    val visibility: String,
    val status: String,
    val createdAt: String,
    val viewerState: ViewerState
)

/** 观看者状态 */
data class ViewerState(
    val liked: Boolean,
    val viewed: Boolean,
    val owner: Boolean
)

/** 我的视频列表响应 */
data class MyVideosData(
    val items: List<VideoPostResponse>,
    val nextCursor: String?,
    val hasMore: Boolean
)

/** 发布视频响应 */
data class CreateVideoData(
    val video: VideoPostResponse
)

/** 点赞响应 */
data class LikeData(
    val videoId: Long,
    val liked: Boolean,
    val likeCount: Long
)

/** 访问记录请求 */
data class ViewRequest(
    val source: String = "recommended_feed",
    val watchDurationMs: Int? = null
)

/** 访问记录响应 */
data class ViewData(
    val videoId: Long,
    val viewed: Boolean,
    val viewCount: Long
)

/** 删除视频响应 */
data class DeleteVideoData(
    val videoId: Long,
    val deleted: Boolean
)

/** 评论响应 */
data class CommentResponse(
    val id: Long,
    val videoId: Long,
    val author: AuthorSummary,
    val content: String,
    val createdAt: String
)

/** 发表评论请求 */
data class PostCommentRequest(
    val content: String
)

/** 发表评论响应 */
data class PostCommentData(
    val comment: CommentResponse,
    val commentCount: Long
)

/** 评论列表响应 */
data class GetCommentsData(
    val items: List<CommentResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val commentCount: Long
)

/** 健康检查响应 */
data class HealthData(
    val status: String,
    val components: Map<String, String>
)

/** 推荐流响应 */
data class RecommendedFeedData(
    val items: List<VideoPostResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val strategy: String?
)

/** 重置推荐历史响应 */
data class ResetRecommendedHistoryData(
    val reset: Boolean,
    val clearedCount: Long
)
