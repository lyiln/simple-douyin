package com.example.douyin.network

import com.example.douyin.network.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ======================== Auth ========================

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponseWrapper<AuthData>>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponseWrapper<AuthData>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiResponseWrapper<Void>>

    // ======================== User ========================

    @GET("api/v1/me")
    suspend fun getMe(): Response<ApiResponseWrapper<MeData>>

    // ======================== Videos ========================

    @GET("api/v1/me/videos")
    suspend fun getMyVideos(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponseWrapper<MyVideosData>>

    @POST("api/v1/videos")
    @Headers("Content-Type: application/json")
    suspend fun publishVideo(@Body request: PublishVideoRequest): Response<ApiResponseWrapper<CreateVideoData>>

    @Multipart
    @POST("api/v1/videos")
    suspend fun publishVideoMultipart(
        @Part("caption") caption: RequestBody,
        @Part videoFile: MultipartBody.Part,
        @Part coverFile: MultipartBody.Part? = null,
        @Part("durationMs") durationMs: RequestBody? = null,
        @Part("visibility") visibility: RequestBody? = null
    ): Response<ApiResponseWrapper<CreateVideoData>>

    @DELETE("api/v1/videos/{videoId}")
    suspend fun deleteVideo(@Path("videoId") videoId: Long): Response<ApiResponseWrapper<DeleteVideoData>>

    // ======================== Likes ========================

    @PUT("api/v1/videos/{videoId}/likes/me")
    suspend fun likeVideo(@Path("videoId") videoId: Long): Response<ApiResponseWrapper<LikeData>>

    @DELETE("api/v1/videos/{videoId}/likes/me")
    suspend fun unlikeVideo(@Path("videoId") videoId: Long): Response<ApiResponseWrapper<LikeData>>

    // ======================== Views ========================

    @POST("api/v1/videos/{videoId}/views/me")
    suspend fun recordView(
        @Path("videoId") videoId: Long,
        @Body request: ViewRequest
    ): Response<ApiResponseWrapper<ViewData>>

    // ======================== Comments ========================

    @GET("api/v1/videos/{videoId}/comments")
    suspend fun getComments(
        @Path("videoId") videoId: Long,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponseWrapper<GetCommentsData>>

    @POST("api/v1/videos/{videoId}/comments")
    @Headers("Content-Type: application/json")
    suspend fun postComment(
        @Path("videoId") videoId: Long,
        @Body request: PostCommentRequest
    ): Response<ApiResponseWrapper<PostCommentData>>

    // ======================== Feed ========================

    @GET("api/v1/feeds/recommended/videos")
    suspend fun getRecommendedVideos(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ApiResponseWrapper<RecommendedFeedData>>

    // ======================== Health ========================

    @GET("api/v1/health")
    suspend fun healthCheck(): Response<ApiResponseWrapper<HealthData>>
}
