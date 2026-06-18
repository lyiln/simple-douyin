package com.example.douyin.data

import android.util.Log
import com.example.douyin.network.ApiClient
import com.example.douyin.network.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

/**
 * 面向真实后端的 API 数据仓库。
 * 所有网络请求在 IO 线程执行，通过回调返回结果。
 */
object ApiRepository {

    private const val TAG = "ApiRepository"
    private const val DEFAULT_SOURCE = "recommended_feed"
    private const val HTTP_UNAUTHORIZED = 401
    private const val API_UNAUTHORIZED = 40101

    // ======================== Auth ========================

    suspend fun login(username: String, password: String): Result<AuthData> {
        return apiCall {
            val response = ApiClient.apiService!!.login(LoginRequest(username, password))
            requireSuccess(response)
            val data = response.body()!!.data!!
            ApiClient.setToken(data.accessToken)
            data
        }
    }

    suspend fun register(username: String, password: String, nickname: String): Result<AuthData> {
        return apiCall {
            val response = ApiClient.apiService!!.register(RegisterRequest(username, password, nickname))
            requireSuccess(response)
            val data = response.body()!!.data!!
            ApiClient.setToken(data.accessToken)
            data
        }
    }

    suspend fun logout(): Result<Unit> {
        val result = apiCall {
            val response = ApiClient.apiService!!.logout()
            requireSuccess(response)
        }
        ApiClient.setToken(null)
        return if (result.isFailure && isUnauthorized(result.exceptionOrNull())) {
            Result.success(Unit)
        } else {
            result
        }
    }

    fun clearSession() {
        ApiClient.setToken(null)
    }

    fun isUnauthorized(error: Throwable?): Boolean {
        val apiException = error as? ApiException ?: return false
        return apiException.code == HTTP_UNAUTHORIZED || apiException.code == API_UNAUTHORIZED
    }

    // ======================== Me ========================

    suspend fun getMe(): Result<MeData> {
        return apiCall {
            val response = ApiClient.apiService!!.getMe()
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== My Videos ========================

    suspend fun getMyVideos(cursor: String? = null, limit: Int = 10): Result<MyVideosData> {
        return apiCall {
            val response = ApiClient.apiService!!.getMyVideos(cursor, limit)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Feed ========================

    suspend fun getRecommendedVideos(cursor: String? = null, limit: Int = 10): Result<RecommendedFeedData> {
        return apiCall {
            val response = ApiClient.apiService!!.getRecommendedVideos(cursor, limit)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    suspend fun resetRecommendedHistory(): Result<ResetRecommendedHistoryData> {
        return apiCall {
            val response = ApiClient.apiService!!.resetRecommendedHistory()
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    suspend fun resetRecommendedVideoHistory(videoId: Long): Result<ResetRecommendedVideoHistoryData> {
        return apiCall {
            val response = ApiClient.apiService!!.resetRecommendedVideoHistory(videoId)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Publish ========================

    suspend fun publishVideoFile(
        caption: String,
        videoFile: File,
        coverFile: File? = null,
        durationMs: Int? = null,
        visibility: String = "public",
        videoMimeType: String = "video/mp4"
    ): Result<CreateVideoData> {
        return apiCall {
            val videoBody = videoFile.asRequestBody(videoMimeType.toMediaType())
            val videoPart = MultipartBody.Part.createFormData("videoFile", videoFile.name, videoBody)
            val coverPart = coverFile?.let {
                MultipartBody.Part.createFormData(
                    "coverFile",
                    it.name,
                    it.asRequestBody("image/webp".toMediaType())
                )
            }
            val response = ApiClient.apiService!!.publishVideoMultipart(
                caption = caption.textPart(),
                videoFile = videoPart,
                coverFile = coverPart,
                durationMs = durationMs?.toString()?.textPart(),
                visibility = visibility.textPart()
            )
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Delete ========================

    suspend fun deleteVideo(videoId: Long): Result<DeleteVideoData> {
        return apiCall {
            val response = ApiClient.apiService!!.deleteVideo(videoId)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Like / Unlike ========================

    suspend fun likeVideo(videoId: Long): Result<LikeData> {
        return apiCall {
            val response = ApiClient.apiService!!.likeVideo(videoId)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    suspend fun unlikeVideo(videoId: Long): Result<LikeData> {
        return apiCall {
            val response = ApiClient.apiService!!.unlikeVideo(videoId)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== View ========================

    suspend fun recordView(videoId: Long): Result<ViewData> {
        return apiCall {
            val response = ApiClient.apiService!!.recordView(
                videoId,
                ViewRequest(source = DEFAULT_SOURCE)
            )
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Comments ========================

    suspend fun getComments(
        videoId: Long,
        cursor: String? = null,
        limit: Int = 20
    ): Result<GetCommentsData> {
        return apiCall {
            val response = ApiClient.apiService!!.getComments(videoId, cursor, limit)
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    suspend fun postComment(videoId: Long, content: String): Result<PostCommentData> {
        return apiCall {
            val response = ApiClient.apiService!!.postComment(
                videoId,
                PostCommentRequest(content)
            )
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== Health ========================

    suspend fun healthCheck(): Result<HealthData> {
        return apiCall {
            val response = ApiClient.apiService!!.healthCheck()
            requireSuccess(response)
            response.body()!!.data!!
        }
    }

    // ======================== 辅助 ========================

    private fun <T> requireSuccess(response: retrofit2.Response<ApiResponseWrapper<T>>) {
        if (!response.isSuccessful || response.body() == null) {
            val errorBody = response.errorBody()?.string()
            val parsedError = errorBody?.let {
                runCatching { JSONObject(it) }.getOrNull()
            }
            val apiCode = parsedError?.optInt("code", response.code()) ?: response.code()
            val apiMessage = parsedError
                ?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: "HTTP ${response.code()}: ${response.message()}"
            throw ApiException(
                code = apiCode,
                message = apiMessage
            )
        }
        val body = response.body()!!
        if (body.code != 0) {
            throw ApiException(code = body.code, message = body.message)
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: ApiException) {
                if (isUnauthorized(e)) {
                    ApiClient.setToken(null)
                }
                Log.e(TAG, "API error code=${e.code}: ${e.message}")
                Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Network error", e)
                Result.failure(ApiException(code = -1, message = e.message ?: "网络请求失败"))
            }
        }
    }

    private fun String.textPart() = toRequestBody("text/plain".toMediaType())
}

class ApiException(val code: Int, override val message: String) : Exception(message)
