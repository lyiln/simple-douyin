package com.example.douyin.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.example.douyin.data.MockRepository
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

    if (isLoggedIn) {
        RealMainScreen(onLoggedOut = { isLoggedIn = false })
    } else {
        AuthScreen(onAuthenticated = { isLoggedIn = true })
    }
}

@Composable
private fun AuthScreen(onAuthenticated: () -> Unit) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("demo") }
    var password by remember { mutableStateOf("Passw0rd!") }
    var nickname by remember { mutableStateOf("Demo User") }
    var baseUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var registerMode by remember { mutableStateOf(false) }
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
            Text("Simple Douyin", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Black)
            Text(
                "Connect to the Spring Boot API server",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 14.sp
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("API Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
            )
            if (registerMode) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    message = null
                    ApiClient.updateBaseUrl(baseUrl)
                    scope.launch {
                        val result = if (registerMode) {
                            ApiRepository.register(
                                username.trim(),
                                password,
                                nickname.ifBlank { username.trim() }
                            )
                        } else {
                            ApiRepository.login(username.trim(), password)
                        }
                        loading = false
                        if (result.isSuccess) {
                            onAuthenticated()
                        } else {
                            message = result.exceptionOrNull()?.message ?: "Authentication failed"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Text(if (registerMode) "Register and enter" else "Login", fontWeight = FontWeight.Bold)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (registerMode) "Already have an account?" else "Need a demo account?",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { registerMode = !registerMode }) {
                    Text(if (registerMode) "Login" else "Register")
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
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<VideoPost>() }
    val myVideos = remember { mutableStateListOf<VideoPost>() }
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

    fun showError(prefix: String, error: Throwable?) {
        toast = "$prefix: ${error?.message ?: "unknown error"}"
    }

    fun refreshMe() {
        scope.launch {
            val result = ApiRepository.getMe()
            result.onSuccess { me = it.profile }
                .onFailure { showError("Load profile failed", it) }
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
                if (posts.isEmpty()) {
                    posts.addAll(MockRepository.initialPosts())
                    toast = "No recommended videos yet. Showing local demo content."
                }
            }.onFailure {
                if (posts.isEmpty()) posts.addAll(MockRepository.initialPosts())
                showError("Load feed failed", it)
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
            }.onFailure { showError("Load my videos failed", it) }
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
                        }.onFailure { showError("Like failed", it) }
                    }
                },
                onComment = { selectedPost = it }
            )
            RealScreen.Publish -> PublishApiScreen(
                posts = posts.ifEmpty { MockRepository.initialPosts() },
                onCancel = { screen = RealScreen.Home },
                onPublished = { post ->
                    posts.add(0, post)
                    myVideos.add(0, post.copy(isOwner = true))
                    refreshMe()
                    screen = RealScreen.Home
                    toast = "Video published"
                },
                onError = { showError("Publish failed", it) }
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
                            toast = "Video deleted"
                        }.onFailure { showError("Delete failed", it) }
                    }
                },
                onLogout = {
                    scope.launch {
                        ApiRepository.logout()
                        onLoggedOut()
                    }
                }
            )
            RealScreen.Health -> HealthApiScreen()
        }

        BottomApiNav(
            current = screen,
            onChange = { screen = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

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
                        }.onFailure { showError("Load comments failed", it) }
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
                            toast = "Comment sent"
                        }.onFailure { showError("Comment failed", it) }
                    }
                }
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
    onNeedMore: () -> Unit,
    onVisible: (VideoPost) -> Unit,
    onLike: (VideoPost) -> Unit,
    onComment: (VideoPost) -> Unit
) {
    if (posts.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) CircularProgressIndicator(color = Color.White)
                Text("No videos", color = Color.White)
                Button(onClick = onRefresh) { Text("Refresh") }
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
        FeedHeader(onRefresh = onRefresh, loading = loading, modifier = Modifier.align(Alignment.TopCenter))
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

    LaunchedEffect(active) {
        if (active) {
            delay(250)
            showPlayer = true
        } else {
            showPlayer = false
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
        PostInfo(post, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 96.dp, bottom = 96.dp))
        ApiActionRail(
            post = post,
            commentCount = commentCount,
            onLike = onLike,
            onComment = onComment,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 96.dp)
        )
    }
}

@Composable
private fun FeedHeader(onRefresh: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Recommended", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Loading" else "Refresh") }
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
                Text(if (post.isOwner) "me" else "+", color = if (post.isOwner) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        ActionButton(
            iconRes = if (post.isLiked) R.drawable.ic_feed_like_active else R.drawable.ic_feed_like,
            label = formatCount(post.likes),
            onClick = onLike,
            iconSize = 48
        )
        ActionButton(R.drawable.ic_feed_comment, formatCount(commentCount), onComment, 42)
        ActionButton(R.drawable.ic_feed_share, "Share", {}, 46)
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
    var caption by remember { mutableStateOf("A short video from Android") }
    var loading by remember { mutableStateOf(false) }
    val selected = posts.getOrElse(selectedIndex) { MockRepository.initialPosts().first() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Text("Publish", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(70.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Select a bundled video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(posts.size) { index ->
                    val post = posts[index]
                    Box(
                        modifier = Modifier.width(112.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp)).clickable { selectedIndex = index }
                    ) {
                        NetworkBackedImage(post.coverUrl, post.coverRes, null, Modifier.fillMaxSize(), ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (index == selectedIndex) 0.10f else 0.42f)))
                        if (index == selectedIndex) {
                            AssetIcon(R.drawable.ic_app_check, "Selected", Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Caption") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("Multipart") })
                AssistChip(onClick = {}, label = { Text("uploads/") })
            }
            Button(
                enabled = !loading && caption.isNotBlank(),
                onClick = {
                    loading = true
                    scope.launch {
                        val file = copyRawVideoToCache(
                            context = context,
                            rawRes = selected.videoRes ?: R.raw.publish_video_placeholder
                        )
                        val result = ApiRepository.publishVideoFile(
                            caption = caption.trim(),
                            videoFile = file,
                            durationMs = 7000,
                            visibility = "public"
                        )
                        loading = false
                        result.onSuccess { onPublished(it.video.toUiPost()) }
                            .onFailure { onError(it) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                if (loading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    AssetIcon(R.drawable.ic_app_publish, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Publish to backend", fontWeight = FontWeight.Bold)
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
                        Text(me?.nickname ?: "Loading profile", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("@${me?.username ?: "..."}", color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp)
                    }
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    StatBlock((me?.likedCount ?: 0).toString(), "Likes")
                    StatBlock((me?.videoCount ?: videos.size).toString(), "Videos")
                    StatBlock(videos.size.toString(), "Loaded")
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("My videos", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh, enabled = !loading) { Text(if (loading) "Loading" else "Refresh") }
                }
            }
            items(videos) { post ->
                MyVideoRow(post = post, onDelete = { onDelete(post) })
            }
            if (hasMore) {
                item {
                    FilledTonalButton(onClick = onLoadMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text(if (loading) "Loading" else "Load more")
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
            Text("${formatCount(post.likes)} likes · ${formatCount(post.comments)} comments", color = Color.White.copy(alpha = 0.52f), fontSize = 12.sp)
        }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun HealthApiScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Not checked") }
    var components by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun check() {
        loading = true
        scope.launch {
            val result = ApiRepository.healthCheck()
            loading = false
            result.onSuccess {
                status = it.status
                components = it.components
            }.onFailure {
                status = it.message ?: "Health check failed"
                components = emptyMap()
            }
        }
    }

    LaunchedEffect(Unit) { check() }

    Column(
        Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 82.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Health", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(status, color = if (status == "UP") Color(0xFF30D158) else Color.White.copy(alpha = 0.72f), fontSize = 18.sp)
        components.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(key, color = Color.White, modifier = Modifier.weight(1f))
                Text(value, color = if (value == "UP") Color(0xFF30D158) else Color(0xFFFF7A8A), fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = ::check, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))) {
            Text(if (loading) "Checking" else "Check again")
        }
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
                    Text("$count comments", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) { Text("Close") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                        placeholder = { Text("Comment on ${post.author}") },
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
                        AssetIcon(R.drawable.ic_app_send, "Send comment", Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomApiNav(current: RealScreen, onChange: (RealScreen) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        RealScreen.Home to "Home",
        RealScreen.Publish to "Publish",
        RealScreen.Profile to "Mine",
        RealScreen.Health to "Health"
    )
    Surface(modifier = modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.76f)) {
        Row(
            modifier = Modifier.navigationBarsPadding().height(64.dp).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
    Profile,
    Health
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

private fun Long.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun formatCount(value: Int): String {
    return if (value >= 10000) {
        val v = value / 1000 / 10f
        "${v}w"
    } else {
        value.toString()
    }
}
