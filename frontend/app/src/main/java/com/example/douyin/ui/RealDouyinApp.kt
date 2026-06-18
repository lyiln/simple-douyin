package com.example.douyin.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.douyin.R
import com.example.douyin.data.ApiRepository
import com.example.douyin.data.PublishAssetRepository
import com.example.douyin.data.toUiComment
import com.example.douyin.data.toUiPost
import com.example.douyin.model.Comment
import com.example.douyin.model.VideoPost
import com.example.douyin.network.ApiClient
import com.example.douyin.network.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun RealDouyinApp() {
    var isLoggedIn by remember { mutableStateOf(ApiClient.isLoggedIn()) }
    val defaultUsername = remember { "user_${System.currentTimeMillis().toString().takeLast(6)}" }
    var authUsername by remember { mutableStateOf(defaultUsername) }
    var authPassword by remember { mutableStateOf("Passw0rd!") }
    var authNickname by remember { mutableStateOf("新用户") }

    if (isLoggedIn) {
        RealMainScreen(onLoggedOut = { isLoggedIn = false })
    } else {
        AuthScreen(
            initialUsername = authUsername,
            initialPassword = authPassword,
            initialNickname = authNickname,
            onAuthenticated = { username, password, nickname ->
                authUsername = username
                authPassword = password
                authNickname = nickname
                isLoggedIn = true
            }
        )
    }
}

@Composable
private fun AuthScreen(
    initialUsername: String,
    initialPassword: String,
    initialNickname: String,
    onAuthenticated: (String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var nickname by remember { mutableStateOf(initialNickname) }
    var registerMode by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("简版抖音", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(
                "登录后进入推荐视频流",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            if (registerMode) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }
            Button(
                enabled = !loading,
                onClick = {
                    val normalizedUsername = username.trim()
                    val normalizedNickname = nickname.trim()
                    when {
                        normalizedUsername.isBlank() -> message = "请输入用户名"
                        password.isBlank() -> message = "请输入密码"
                        registerMode && normalizedNickname.isBlank() -> message = "请输入昵称"
                        else -> {
                            loading = true
                            message = null
                            scope.launch {
                                val effectiveNickname = normalizedNickname.ifBlank { normalizedUsername }
                                val result = if (registerMode) {
                                    ApiRepository.register(
                                        normalizedUsername,
                                        password,
                                        effectiveNickname
                                    )
                                } else {
                                    ApiRepository.login(normalizedUsername, password)
                                }
                                loading = false
                                if (result.isSuccess) {
                                    onAuthenticated(normalizedUsername, password, effectiveNickname)
                                } else {
                                    message = localizedErrorMessage(result.exceptionOrNull())
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(if (registerMode) "注册并进入" else "登录", fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (registerMode) "已有账号？" else "还没有账号？",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { registerMode = !registerMode }) {
                    Text(if (registerMode) "去登录" else "去注册")
                }
            }
            message?.let {
                Text(it, color = Color(0xFFFF7A8A), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RealMainScreen(onLoggedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<VideoPost>() }
    val myVideos = remember { mutableStateListOf<VideoPost>() }
    val publishSources = remember { PublishAssetRepository.assets() }
    val commentsByPost = remember { mutableStateMapOf<String, List<Comment>>() }
    val commentCounts = remember { mutableStateMapOf<String, Int>() }
    val recordedViews = remember { mutableStateMapOf<String, Boolean>() }
    var screen by remember { mutableStateOf(RealScreen.Home) }
    var selectedPost by remember { mutableStateOf<VideoPost?>(null) }
    var me by remember { mutableStateOf<UserProfile?>(null) }
    var feedCursor by remember { mutableStateOf<String?>(null) }
    var feedHasMore by remember { mutableStateOf(false) }
    var myCursor by remember { mutableStateOf<String?>(null) }
    var myHasMore by remember { mutableStateOf(false) }
    var loadingFeed by remember { mutableStateOf(false) }
    var loadingMine by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showExitConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedPost == null) {
        if (screen == RealScreen.Home) {
            showExitConfirm = true
        } else {
            screen = RealScreen.Home
        }
    }

    fun showError(prefix: String, error: Throwable?) {
        toast = "$prefix：${localizedErrorMessage(error)}"
    }

    fun refreshMe() {
        scope.launch {
            val result = ApiRepository.getMe()
            result.onSuccess { me = it.profile }
                .onFailure { showError("加载个人信息失败", it) }
        }
    }

    fun loadFeed(reset: Boolean) {
        if (loadingFeed) return
        loadingFeed = true
        scope.launch {
            val result = ApiRepository.getRecommendedVideos(if (reset) null else feedCursor, 10)
            loadingFeed = false
            result.onSuccess { data ->
                if (reset) posts.clear()
                val mapped = data.items.map { it.toUiPost() }
                posts.addAll(mapped)
                mapped.forEach { post -> commentCounts[post.id] = post.comments }
                feedCursor = data.nextCursor
                feedHasMore = data.hasMore
            }.onFailure {
                showError("加载推荐失败", it)
            }
        }
    }

    fun resetFeedHistory() {
        if (loadingFeed) return
        loadingFeed = true
        scope.launch {
            val resetResult = ApiRepository.resetRecommendedHistory()
            resetResult.onFailure {
                loadingFeed = false
                showError("重置推荐失败", it)
            }.onSuccess {
                posts.clear()
                feedCursor = null
                feedHasMore = true
                recordedViews.clear()

                val result = ApiRepository.getRecommendedVideos(null, 10)
                loadingFeed = false
                result.onSuccess { data ->
                    val mapped = data.items.map { it.toUiPost() }
                    posts.addAll(mapped)
                    mapped.forEach { post -> commentCounts[post.id] = post.comments }
                    feedCursor = data.nextCursor
                    feedHasMore = data.hasMore
                    toast = "推荐已重置"
                }.onFailure {
                    showError("加载推荐失败", it)
                }
            }
        }
    }

    fun loadMyVideos(reset: Boolean) {
        if (loadingMine) return
        loadingMine = true
        scope.launch {
            val result = ApiRepository.getMyVideos(if (reset) null else myCursor, 9)
            loadingMine = false
            result.onSuccess { data ->
                if (reset) myVideos.clear()
                myVideos.addAll(data.items.map { it.toUiPost() })
                myCursor = data.nextCursor
                myHasMore = data.hasMore
            }.onFailure { showError("加载我的作品失败", it) }
        }
    }

    fun recordView(post: VideoPost) {
        val videoId = post.remoteId ?: return
        if (recordedViews[post.id] == true) return
        recordedViews[post.id] = true
        scope.launch {
            ApiRepository.recordView(videoId)
        }
    }

    LaunchedEffect(Unit) {
        refreshMe()
        loadFeed(reset = true)
        loadMyVideos(reset = true)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            RealScreen.Home -> HomeApiFeed(
                posts = posts,
                loading = loadingFeed,
                hasMore = feedHasMore,
                commentCounts = commentCounts,
                onRefresh = { loadFeed(reset = true) },
                onReset = { resetFeedHistory() },
                onNeedMore = { loadFeed(reset = false) },
                onVisible = ::recordView,
                onLike = { post ->
                    val videoId = post.remoteId ?: return@HomeApiFeed
                    scope.launch {
                        val result = if (post.isLiked) {
                            ApiRepository.unlikeVideo(videoId)
                        } else {
                            ApiRepository.likeVideo(videoId)
                        }
                        result.onSuccess { data ->
                            replacePost(posts, post.id) {
                                it.copy(likes = data.likeCount.toSafeInt(), isLiked = data.liked)
                            }
                        }.onFailure { showError("点赞失败", it) }
                    }
                },
                onComment = { selectedPost = it }
            )
            RealScreen.Publish -> PublishApiScreen(
                posts = publishSources,
                onCancel = { screen = RealScreen.Home },
                onPublished = { post ->
                    posts.add(0, post)
                    myVideos.add(0, post.copy(isOwner = true))
                    refreshMe()
                    screen = RealScreen.Home
                    toast = "发布成功"
                },
                onError = { showError("发布失败", it) }
            )
            RealScreen.Profile -> ProfileApiScreen(
                me = me,
                videos = myVideos,
                loading = loadingMine,
                hasMore = myHasMore,
                onRefresh = {
                    refreshMe()
                    loadMyVideos(reset = true)
                },
                onLoadMore = { loadMyVideos(reset = false) },
                onDelete = { post ->
                    val videoId = post.remoteId ?: return@ProfileApiScreen
                    scope.launch {
                        val result = ApiRepository.deleteVideo(videoId)
                        result.onSuccess {
                            myVideos.removeAll { it.id == post.id }
                            posts.removeAll { it.id == post.id }
                            refreshMe()
                            toast = "已删除作品"
                        }.onFailure { showError("删除失败", it) }
                    }
                },
                onLogout = {
                    scope.launch {
                        ApiRepository.logout()
                        onLoggedOut()
                    }
                }
            )
        }

        if (screen != RealScreen.Publish) {
            BottomApiNav(
                current = screen,
                onChange = { screen = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        toast?.let { text ->
            ToastPill(text, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp))
            LaunchedEffect(text) {
                delay(1800)
                if (toast == text) toast = null
            }
        }

        selectedPost?.let { post ->
            CommentApiSheet(
                post = post,
                comments = commentsByPost[post.id].orEmpty(),
                count = commentCounts[post.id] ?: post.comments,
                onDismiss = { selectedPost = null },
                onLoad = {
                    val videoId = post.remoteId ?: return@CommentApiSheet
                    scope.launch {
                        val result = ApiRepository.getComments(videoId)
                        result.onSuccess { data ->
                            commentsByPost[post.id] = data.items.map { it.toUiComment() }
                            commentCounts[post.id] = data.commentCount.toSafeInt()
                            replacePost(posts, post.id) { it.copy(comments = data.commentCount.toSafeInt()) }
                        }.onFailure { showError("加载评论失败", it) }
                    }
                },
                onSend = { text ->
                    val videoId = post.remoteId ?: return@CommentApiSheet
                    scope.launch {
                        val result = ApiRepository.postComment(videoId, text)
                        result.onSuccess { data ->
                            val current = commentsByPost[post.id].orEmpty()
                            commentsByPost[post.id] = listOf(data.comment.toUiComment()) + current
                            commentCounts[post.id] = data.commentCount.toSafeInt()
                            replacePost(posts, post.id) { it.copy(comments = data.commentCount.toSafeInt()) }
                            toast = "评论已发送"
                        }.onFailure { showError("评论失败", it) }
                    }
                }
            )
        }

        if (showExitConfirm) {
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("退出应用") },
                text = { Text("确定要退出简版抖音吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirm = false
                            context.findActivity()?.finish()
                        }
                    ) {
                        Text("退出")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text("取消")
                    }
                },
                containerColor = Color(0xFF1F1F1F),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeApiFeed(
    posts: List<VideoPost>,
    loading: Boolean,
    hasMore: Boolean,
    commentCounts: Map<String, Int>,
    onRefresh: () -> Unit,
    onReset: () -> Unit,
    onNeedMore: () -> Unit,
    onVisible: (VideoPost) -> Unit,
    onLike: (VideoPost) -> Unit,
    onComment: (VideoPost) -> Unit
) {
    if (posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) CircularProgressIndicator(color = Color.White)
                Text("暂无推荐视频", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRefresh, enabled = !loading) { Text("刷新") }
                    FilledTonalButton(onClick = onReset, enabled = !loading) { Text("重置") }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { posts.size })

    LaunchedEffect(pagerState.currentPage, posts.size) {
        posts.getOrNull(pagerState.currentPage)?.let(onVisible)
        if (hasMore && pagerState.currentPage >= posts.lastIndex - 1) {
            onNeedMore()
        }
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val post = posts[page]
            VideoApiPage(
                post = post,
                active = pagerState.currentPage == page,
                commentCount = commentCounts[post.id] ?: post.comments,
                onLike = { onLike(post) },
                onComment = { onComment(post) }
            )
        }
        FeedHeader(
            onRefresh = onRefresh,
            onReset = onReset,
            loading = loading,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun VideoApiPage(
    post: VideoPost,
    active: Boolean,
    commentCount: Int,
    onLike: () -> Unit,
    onComment: () -> Unit
) {
    var showPlayer by remember(post.id) { mutableStateOf(false) }
    var userPaused by remember(post.id) { mutableStateOf(false) }
    var playbackProgress by remember(post.id) { mutableStateOf(VideoProgressState()) }
    var seekRequest by remember(post.id) { mutableStateOf<VideoSeekRequest?>(null) }

    LaunchedEffect(active, post.id) {
        if (active) {
            userPaused = false
            delay(250)
            showPlayer = true
        } else {
            userPaused = false
            showPlayer = false
            playbackProgress = VideoProgressState()
            seekRequest = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        NetworkBackedImage(
            url = post.coverUrl,
            fallbackRes = post.coverRes,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (showPlayer) {
            LocalVideoPlayer(
                videoRes = post.videoRes,
                videoUrl = post.videoUrl,
                isActive = active,
                isPaused = userPaused,
                seekRequest = seekRequest,
                onProgress = { playbackProgress = it },
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.58f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 88.dp, bottom = 96.dp)
                .pointerInput(active, post.isLiked) {
                    detectTapGestures(
                        onTap = {
                            if (active && showPlayer) {
                                userPaused = !userPaused
                            }
                        },
                        onDoubleTap = {
                            if (active && !post.isLiked) {
                                onLike()
                            }
                        }
                    )
                }
        )
        if (showPlayer && userPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                AssetIcon(R.drawable.ic_app_play_center, null, Modifier.size(74.dp))
            }
        }
        PostInfo(post, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 96.dp, bottom = 96.dp))
        ApiActionRail(
            post = post,
            commentCount = commentCount,
            onLike = onLike,
            onComment = onComment,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp)
        )
        VideoProgressBar(
            progress = playbackProgress,
            onSeek = { positionMs ->
                seekRequest = VideoSeekRequest(System.nanoTime(), positionMs)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 68.dp)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun VideoProgressBar(
    progress: VideoProgressState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSeekable = progress.isSeekable
    val durationMs = progress.durationMs
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val displayedFraction = if (dragging) dragFraction else progress.progressFraction

    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(isSeekable, durationMs) {
                if (!isSeekable) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragging = true
                        dragFraction = (offset.x / width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (durationMs > 0L) {
                            onSeek((dragFraction * durationMs).toLong().coerceIn(0L, durationMs))
                        }
                        dragging = false
                    },
                    onDragCancel = {
                        dragging = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackWidth = size.width
            val strokeWidth = if (dragging) 4.dp.toPx() else 2.dp.toPx()
            val progressX = (trackWidth * displayedFraction).coerceIn(0f, trackWidth)
            val bufferedX = (trackWidth * progress.bufferedFraction).coerceIn(0f, trackWidth)

            drawLine(
                color = Color.White.copy(alpha = 0.26f),
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            if (bufferedX > 0f) {
                drawLine(
                    color = Color.White.copy(alpha = 0.42f),
                    start = Offset(0f, centerY),
                    end = Offset(bufferedX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            if (progressX > 0f) {
                drawLine(
                    color = Color.White,
                    start = Offset(0f, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            if (isSeekable || dragging) {
                drawCircle(
                    color = Color.White,
                    radius = if (dragging) 6.dp.toPx() else 3.dp.toPx(),
                    center = Offset(progressX, centerY)
                )
            }
        }
    }
}

@Composable
private fun FeedHeader(onRefresh: () -> Unit, onReset: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("推荐", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "加载中" else "刷新") }
        TextButton(onClick = onReset, enabled = !loading) { Text("重置") }
    }
}

@Composable
private fun PostInfo(post: VideoPost, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(post.author, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(post.caption, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(post.topic, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssetIcon(R.drawable.ic_app_music, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(post.music, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ApiActionRail(
    post: VideoPost,
    commentCount: Int,
    onLike: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(contentAlignment = Alignment.BottomCenter) {
            NetworkBackedImage(
                url = post.avatarUrl,
                fallbackRes = post.avatarRes,
                contentDescription = post.author,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .size(24.dp)
                    .offset(y = 12.dp)
                    .clip(CircleShape)
                    .background(if (post.isOwner) Color.White else Color(0xFFFE2C55)),
                contentAlignment = Alignment.Center
            ) {
                Text(if (post.isOwner) "我" else "+", color = if (post.isOwner) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        ActionButton(
            iconRes = if (post.isLiked) R.drawable.ic_feed_like_active else R.drawable.ic_feed_like,
            label = formatCount(post.likes),
            onClick = onLike,
            iconSize = 48
        )
        ActionButton(R.drawable.ic_feed_comment, formatCount(commentCount), onComment, 42)
    }
}

@Composable
private fun PublishApiScreen(
    posts: List<VideoPost>,
    onCancel: () -> Unit,
    onPublished: (VideoPost) -> Unit,
    onError: (Throwable?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableIntStateOf(0) }
    var pickedVideo by remember { mutableStateOf<PickedVideo?>(null) }
    var caption by remember { mutableStateOf("来自安卓客户端的视频") }
    var loading by remember { mutableStateOf(false) }
    val selected = posts.getOrElse(selectedIndex) { PublishAssetRepository.assets().first() }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // 部分系统文件提供方只授予临时读取权限，当前会话内仍可复制上传。
        }
        pickedVideo = PickedVideo(
            uri = uri,
            name = resolveVideoDisplayName(context, uri),
            mimeType = context.contentResolver.getType(uri) ?: "video/mp4"
        )
    }
    fun publish() {
        if (loading || caption.isBlank()) return
        loading = true
        scope.launch {
            val trimmedCaption = caption.trim()
            val customVideo = pickedVideo
            val file = if (customVideo != null) {
                copyPickedVideoToCache(context, customVideo)
            } else {
                val localVideoRes = selected.videoRes ?: R.raw.publish_video_placeholder
                copyRawVideoToCache(
                    context = context,
                    rawRes = localVideoRes
                )
            }
            val result = ApiRepository.publishVideoFile(
                caption = trimmedCaption,
                videoFile = file,
                durationMs = if (customVideo == null) 7000 else null,
                visibility = "public",
                videoMimeType = customVideo?.mimeType ?: "video/mp4"
            )
            loading = false
            result.onSuccess { onPublished(it.video.toUiPost()) }
                .onFailure { onError(it) }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::publish, enabled = !loading && caption.isNotBlank()) { Text("发布") }
                Text("发布作品", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                TextButton(onClick = onCancel, modifier = Modifier.width(92.dp)) { Text("取消") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("选择视频", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            FilledTonalButton(
                onClick = { videoPicker.launch(arrayOf("video/*")) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                AssetIcon(R.drawable.ic_app_publish, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("从本机选择视频", fontWeight = FontWeight.Bold)
            }
            pickedVideo?.let { video ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "已选择：${video.name}",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickedVideo = null }, enabled = !loading) {
                        Text("改用内置素材")
                    }
                }
            }
            Text("内置素材", color = Color.White.copy(alpha = 0.70f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(posts.size) { index ->
                    val post = posts[index]
                    Box(
                        modifier = Modifier.width(112.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).clickable {
                            selectedIndex = index
                            pickedVideo = null
                        }
                    ) {
                        NetworkBackedImage(post.coverUrl, post.coverRes, null, Modifier.fillMaxSize(), ContentScale.Crop)
                        val assetSelected = pickedVideo == null && index == selectedIndex
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (assetSelected) 0.10f else 0.42f)))
                        if (assetSelected) {
                            AssetIcon(R.drawable.ic_app_check, "已选中", Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("标题") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
            )
            Button(
                enabled = !loading && caption.isNotBlank(),
                onClick = ::publish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    AssetIcon(R.drawable.ic_app_publish, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("发布", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProfileApiScreen(
    me: UserProfile?,
    videos: List<VideoPost>,
    loading: Boolean,
    hasMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDelete: (VideoPost) -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(containerColor = Color.Black) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NetworkBackedImage(
                        url = me?.avatarUrl,
                        fallbackRes = R.drawable.avatar_dark_logo,
                        contentDescription = me?.nickname,
                        modifier = Modifier.size(76.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(me?.nickname ?: "加载中", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("@${me?.username ?: "..."}", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp)
                    }
                    TextButton(onClick = onLogout) { Text("退出") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatBlock((me?.likedCount ?: 0).toString(), "获赞")
                    StatBlock((me?.videoCount ?: videos.size).toString(), "作品")
                    StatBlock(videos.size.toString(), "已加载")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("我的作品", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "加载中" else "刷新") }
                }
            }
            if (videos.isEmpty() && !loading) {
                item {
                    Text(
                        "暂无作品",
                        color = Color.White.copy(alpha = 0.58f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(videos) { post ->
                MyVideoRow(post = post, onDelete = { onDelete(post) })
            }
            if (hasMore) {
                item {
                    FilledTonalButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "加载中" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun MyVideoRow(post: VideoPost, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NetworkBackedImage(
            url = post.coverUrl,
            fallbackRes = post.coverRes,
            contentDescription = null,
            modifier = Modifier.width(82.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(post.caption, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("${formatCount(post.likes)} 赞 · ${formatCount(post.comments)} 评论", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp)
        }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

@Composable
private fun CommentApiSheet(
    post: VideoPost,
    comments: List<Comment>,
    count: Int,
    onDismiss: () -> Unit,
    onLoad: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { onLoad() }
    BackHandler(onBack = onDismiss)

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.46f)), contentAlignment = Alignment.BottomCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.72f),
            color = Color(0xFF151518),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(64.dp))
                    Text("${count} 条评论", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) { Text("关闭") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (comments.isEmpty()) {
                        item {
                            Text(
                                "暂无评论",
                                color = Color.White.copy(alpha = 0.58f),
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    items(comments) { comment ->
                        Row {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                Text(comment.author.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(comment.author, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
                                Text(comment.content, color = Color.White, fontSize = 15.sp)
                                Text(comment.time, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp)
                            }
                        }
                    }
                }
                Row(Modifier.navigationBarsPadding().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("说点什么...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    IconButton(onClick = {
                        val content = text.trim()
                        if (content.isNotEmpty()) {
                            text = ""
                            onSend(content)
                        }
                    }) {
                        AssetIcon(R.drawable.ic_app_send, "发送评论", Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomApiNav(current: RealScreen, onChange: (RealScreen) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        RealScreen.Home to "首页",
        RealScreen.Publish to "发布",
        RealScreen.Profile to "我的"
    )
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.76f)) {
        Row(
            modifier = Modifier.navigationBarsPadding().height(64.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (screen, label) ->
                val active = current == screen
                Column(
                    modifier = Modifier.width(70.dp).clickable { onChange(screen) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, color = if (active) Color.White else Color.White.copy(alpha = 0.56f), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(iconRes: Int, label: String, onClick: () -> Unit, iconSize: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(50.dp)) {
            AssetIcon(iconRes, label, Modifier.size(iconSize.dp))
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NetworkBackedImage(
    url: String?,
    @DrawableRes fallbackRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var image by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(url) {
        image = null
        val resolved = ApiClient.resolveUrl(url) ?: return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            try {
                val connection = URL(resolved).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (_: Exception) {
                null
            }
        }
    }

    val loaded = image
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        Image(painter = painterResource(fallbackRes), contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    }
}

@Composable
private fun AssetIcon(@DrawableRes iconRes: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    Image(painter = painterResource(iconRes), contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
    }
}

@Composable
private fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Color.White.copy(alpha = 0.92f), shape = RoundedCornerShape(100.dp), shadowElevation = 4.dp) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

private enum class RealScreen {
    Home,
    Publish,
    Profile
}

private data class PickedVideo(
    val uri: Uri,
    val name: String,
    val mimeType: String
)

private fun localizedErrorMessage(error: Throwable?): String {
    val raw = error?.message?.trim().orEmpty()
    if (raw.isBlank()) return "未知错误"
    return when (raw.lowercase()) {
        "internal server error" -> "服务器内部错误"
        "invalid parameter" -> "参数不正确"
        "unauthorized" -> "请重新登录"
        "username already exists" -> "用户名已存在"
        "network request failed" -> "网络请求失败"
        else -> raw
    }
}

private fun replacePost(posts: MutableList<VideoPost>, id: String, update: (VideoPost) -> VideoPost) {
    val index = posts.indexOfFirst { it.id == id }
    if (index >= 0) posts[index] = update(posts[index])
}

private suspend fun copyRawVideoToCache(
    context: Context,
    @RawRes rawRes: Int
): File = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "upload-${rawRes}-${System.currentTimeMillis()}.mp4")
    context.resources.openRawResource(rawRes).use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file
}

private suspend fun copyPickedVideoToCache(
    context: Context,
    video: PickedVideo
): File = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, video.name.safeUploadFileName())
    context.contentResolver.openInputStream(video.uri).use { input ->
        requireNotNull(input) { "无法读取所选视频" }
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file
}

private fun resolveVideoDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return "本机视频-${System.currentTimeMillis()}.mp4"
}

private fun String.safeUploadFileName(): String {
    val sanitized = trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "picked-video.mp4" }
    return if (sanitized.contains(".")) sanitized else "$sanitized.mp4"
}

private fun Long.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun formatCount(value: Int): String {
    return if (value >= 10000) {
        val v = value / 1000 / 10f
        "${v}w"
    } else {
        value.toString()
    }
}
