package com.example.douyin.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 管理 API 客户端和 token 的单例。
 * 使用 Android SharedPreferences 持久化 token。
 */
object ApiClient {

    /** Android 模拟器访问本机后端的默认地址 */
    private const val DEFAULT_BASE_URL = "http://10.0.2.2:8080/"

    private var baseUrl: String = DEFAULT_BASE_URL
    private var token: String? = null
    private var preferences: SharedPreferences? = null
    private var retrofit: Retrofit? = null
    var apiService: ApiService? = null
        private set

    fun init(context: Context, customBaseUrl: String? = null) {
        if (customBaseUrl != null) {
            baseUrl = customBaseUrl
        }
        preferences = context.getSharedPreferences("simple_douyin_prefs", Context.MODE_PRIVATE)
        token = preferences?.getString("access_token", null)
        buildRetrofit()
    }

    fun setToken(newToken: String?) {
        token = newToken
        if (newToken != null) {
            preferences?.edit()?.putString("access_token", newToken)?.apply()
        } else {
            preferences?.edit()?.remove("access_token")?.apply()
        }
        buildRetrofit()
    }

    fun getToken(): String? = token

    fun isLoggedIn(): Boolean = !token.isNullOrBlank()

    private fun buildRetrofit() {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val requestBuilder = chain.request().newBuilder()
            token?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }
            chain.proceed(requestBuilder.build())
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit?.create(ApiService::class.java)
    }

    fun updateBaseUrl(newBaseUrl: String) {
        baseUrl = newBaseUrl
        buildRetrofit()
    }
}
