package com.example.douyin.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.douyin.R
import com.example.douyin.data.ApiRepository
import com.example.douyin.data.MockRepository
import com.example.douyin.model.AppScreen
import com.example.douyin.model.Comment
import com.example.douyin.model.VideoPost
import com.example.douyin.network.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DouyinApp() {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<VideoPost>().apply { addAll(MockRepository.initialPosts()) } }
    val liked = remember { mutableStateMapOf<String, Boolean>() }
    val collected = remember { mutableStateMapOf<String, Boolean>() }
    val following = remember { mutableStateMapOf<String, Boolean>() }
    val localComments = remember {
        mutableStateMapOf<String, MutableList<Comment>>().apply {
            posts.forEach { put(it.id, MockRepository.commentsFor(it.id).toMutableList()) }
        }
    }
    // API 加载的真实评论（key = videoId 字符串）
    val apiComments = remember { mutableStateMapOf<String, List<Comment>>() }
    val apiCommentCounts = remember { mutableStateMapOf<String, Long>() }
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var selectedPost by remember { mutableStateOf<VideoPost?>(null) }
    var sharePost by remember { mutableStateOf<VideoPost?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            AppScreen.Home -> HomeFeed(
                posts = posts,
                liked = liked,
                collected = collected,
                following = following,
                commentCount = { post ->
                    // 优先 API 评论数，否则本地
                    apiCommentCounts[post.id]?.toInt()
                        ?: apiComments[post.id]?.size
                        ?: localComments[post.id]?.size
                        ?: post.comments
                },
                onOpenComments = { selectedPost = it },
                onOpenShare = { sharePost = it },
                onOpenSearch = { screen = AppScreen.Search },
                onFollowToggle = { post ->
                    following[post.handle] = !(following[post.handle] ?: false)
                    toast = if (following[post.handle] == true) "已关注 ${post.author}" else "已取消关注"
                },
                onLikeToggle = { post ->
                    liked[post.id] = !(liked[post.id] ?: false)
                },
                onCollectToggle = { post ->
                    collected[post.id] = !(collected[post.id] ?: false)
                    toast = if (collected[post.id] == true) "已收藏" else "已取消收藏"
                }
            )
            AppScreen.Friends -> FriendsScreen(posts, following) { screen = AppScreen.Search }
            AppScreen.Publish -> PublishScreen(
                posts = posts,
                onPublish = { newPost ->
                    posts.add(0, newPost)
                    localComments[newPost.id] = mutableListOf(
                        Comment("系统通知", "作品已加入本地推荐流。", "刚刚")
                    )
                    screen = AppScreen.Home
                    toast = "已发布到本地推荐流"
                },
                onCancel = { screen = AppScreen.Home }
            )
            AppScreen.Messages -> MessagesScreen()
            AppScreen.Profile -> ProfileScreen(posts)
            AppScreen.Search -> SearchScreen(posts, onBack = { screen = AppScreen.Home })
        }

        if (screen != AppScreen.Publish && screen != AppScreen.Search) {
            BottomNav(
                current = screen,
                onChange = { screen = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        toast?.let { message ->
            ToastPill(
                text = message,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp)
            )
            LaunchedEffect(message) {
                delay(1400)
                if (toast == message) toast = null
            }
        }

        selectedPost?.let { post ->
            // 优先使用 API 评论，否则用本地 mock
            val comments = apiComments[post.id] ?: localComments[post.id].orEmpty()
            CommentSheet(
                post = post,
                comments = comments,
                onDismiss = { selectedPost = null },
                onSend = { text ->
                    if (ApiClient.isLoggedIn()) {
                        // 真实 API 发表评论
                        scope.launch {
                            val postIdNum = post.id.toLongOrNull()
                            if (postIdNum != null) {
                                val result = ApiRepository.postComment(postIdNum, text)
                                if (result.isSuccess) {
                                    val data = result.getOrNull()
                                    // 重新加载评论列表
                                    val commentsResult = ApiRepository.getComments(postIdNum)
                                    if (commentsResult.isSuccess) {
                                        val commentsData = commentsResult.getOrNull()
                                        val mapped = commentsData?.items?.map { c ->
                                            Comment(c.author.nickname, c.content, c.createdAt)
                                        } ?: emptyList()
                                        apiComments[post.id] = mapped
                                        apiCommentCounts[post.id] = commentsData?.commentCount ?: 0
                                    }
                                    toast = "评论已发表"
                                } else {
                                    toast = "评论失败: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    } else {
                        // 本地 mock 评论
                        localComments.getOrPut(post.id) { mutableListOf() }.add(
                            0,
                            Comment("我", text, "刚刚")
                        )
                        toast = "评论已添加（本地模式）"
                    }
                }
            )
            // 打开评论时，加载 API 评论
            LaunchedEffect(post.id) {
                if (ApiClient.isLoggedIn()) {
                    val postIdNum = post.id.toLongOrNull()
                    if (postIdNum != null && !apiComments.containsKey(post.id)) {
                        val result = ApiRepository.getComments(postIdNum)
                        if (result.isSuccess) {
                            val data = result.getOrNull()
                            val mapped = data?.items?.map { c ->
                                Comment(c.author.nickname, c.content, c.createdAt)
                            } ?: emptyList()
                            apiComments[post.id] = mapped
                            apiCommentCounts[post.id] = data?.commentCount ?: 0
                        }
                    }
                }
            }
        }

        sharePost?.let { post ->
            ShareSheet(
                post = post,
                onDismiss = { sharePost = null },
                onShare = { target ->
                    sharePost = null
                    toast = "$target 已记录为本地分享反馈"
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeFeed(
    posts: List<VideoPost>,
    liked: MutableMap<String, Boolean>,
    collected: MutableMap<String, Boolean>,
    following: MutableMap<String, Boolean>,
    commentCount: (VideoPost) -> Int,
    onOpenComments: (VideoPost) -> Unit,
    onOpenShare: (VideoPost) -> Unit,
    onOpenSearch: () -> Unit,
    onFollowToggle: (VideoPost) -> Unit,
    onLikeToggle: (VideoPost) -> Unit,
    onCollectToggle: (VideoPost) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { posts.size })
    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val post = posts[page]
            VideoPage(
                post = post,
                isActive = pagerState.currentPage == page,
                isLiked = liked[post.id] ?: false,
                isCollected = collected[post.id] ?: false,
                isFollowing = following[post.handle] ?: false,
                commentCount = commentCount(post),
                onLike = { onLikeToggle(post) },
                onCollect = { onCollectToggle(post) },
                onFollow = { onFollowToggle(post) },
                onComment = { onOpenComments(post) },
                onShare = { onOpenShare(post) }
            )
        }
        FeedTopBar(
            onSearch = onOpenSearch,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun VideoPage(
    post: VideoPost,
    isActive: Boolean,
    isLiked: Boolean,
    isCollected: Boolean,
    isFollowing: Boolean,
    commentCount: Int,
    onLike: () -> Unit,
    onCollect: () -> Unit,
    onFollow: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit
) {
    var showPlayer by remember(post.id) { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (isActive) {
            delay(350)
            showPlayer = true
        } else {
            showPlayer = false
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(post.coverRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        if (showPlayer) {
            LocalVideoPlayer(
                videoRes = post.videoRes,
                isActive = isActive,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.76f)
                    )
                )
        )
        PostInfo(
            post = post,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 96.dp, bottom = 94.dp)
        )
        ActionRail(
            post = post,
            isLiked = isLiked,
            isCollected = isCollected,
            isFollowing = isFollowing,
            commentCount = commentCount,
            onLike = onLike,
            onCollect = onCollect,
            onFollow = onFollow,
            onComment = onComment,
            onShare = onShare,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 92.dp)
        )
    }
}

@Composable
private fun FeedTopBar(onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(48.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("朋友", color = Color.White.copy(alpha = 0.68f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("推荐", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.padding(top = 4.dp).width(22.dp).height(3.dp).background(Color.White, RoundedCornerShape(8.dp)))
            }
            Spacer(Modifier.width(24.dp))
            Text("同城", color = Color.White.copy(alpha = 0.38f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
            OriginalIcon(
                iconRes = R.drawable.ic_app_search,
                contentDescription = "搜索",
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun PostInfo(post: VideoPost, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = post.author,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = post.caption,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = post.topic,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OriginalIcon(
                iconRes = R.drawable.ic_app_music,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                post.music,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionRail(
    post: VideoPost,
    isLiked: Boolean,
    isCollected: Boolean,
    isFollowing: Boolean,
    commentCount: Int,
    onLike: () -> Unit,
    onCollect: () -> Unit,
    onFollow: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Image(
                painter = painterResource(post.avatarRes),
                contentDescription = post.author,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onFollow,
                modifier = Modifier
                    .size(28.dp)
                    .offset(y = 14.dp)
            ) {
                OriginalIcon(
                    iconRes = if (isFollowing) R.drawable.ic_app_followed else R.drawable.ic_app_follow,
                    contentDescription = if (isFollowing) "已关注" else "关注",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        ActionButton(
            iconRes = if (isLiked) R.drawable.ic_feed_like_active else R.drawable.ic_feed_like,
            label = formatCount(post.likes + if (isLiked) 1 else 0),
            onClick = onLike,
            iconSize = 48
        )
        ActionButton(
            iconRes = R.drawable.ic_feed_comment,
            label = formatCount(commentCount),
            onClick = onComment,
            iconSize = 42
        )
        ActionButton(
            iconRes = if (isCollected) R.drawable.ic_feed_collect_active else R.drawable.ic_feed_collect,
            label = if (isCollected) "已收藏" else "收藏",
            onClick = onCollect,
            iconSize = 48
        )
        ActionButton(
            iconRes = R.drawable.ic_feed_share,
            label = formatCount(post.shares),
            onClick = onShare,
            iconSize = 46
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF232326), Color(0xFF050505))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            OriginalIcon(
                iconRes = R.drawable.ic_app_music,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    iconSize: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(50.dp)) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(iconSize.dp),
                contentScale = ContentScale.Fit
            )
        }
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomNav(current: AppScreen, onChange: (AppScreen) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        NavItem(AppScreen.Home, "首页"),
        NavItem(AppScreen.Friends, "朋友"),
        NavItem(AppScreen.Publish, ""),
        NavItem(AppScreen.Messages, "消息"),
        NavItem(AppScreen.Profile, "我")
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                if (item.screen == AppScreen.Publish) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .clickable { onChange(AppScreen.Publish) },
                        contentAlignment = Alignment.Center
                    ) {
                        OriginalIcon(
                            iconRes = R.drawable.ic_app_navbar_plus,
                            contentDescription = "发布",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .clickable { onChange(item.screen) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            item.label,
                            color = if (current == item.screen) Color.White else Color.White.copy(alpha = 0.62f),
                            fontSize = 16.sp,
                            fontWeight = if (current == item.screen) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsScreen(posts: List<VideoPost>, following: MutableMap<String, Boolean>, onSearch: () -> Unit) {
    Scaffold(
        containerColor = Color.Black,
        topBar = { SimpleTopBar("朋友", "本地关注动态", onSearch) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text("正在关注", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(posts) { post ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painterResource(post.avatarRes),
                                contentDescription = post.author,
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(post.author, color = Color.White, fontSize = 12.sp, maxLines = 1)
                            AssistChip(
                                onClick = { following[post.handle] = true },
                                label = { Text(if (following[post.handle] == true) "已关注" else "关注") }
                            )
                        }
                    }
                }
            }
            items(posts) { post ->
                FeedPreviewCard(post)
            }
        }
    }
}

@Composable
private fun FeedPreviewCard(post: VideoPost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(94.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            Image(painterResource(post.coverRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            OriginalIcon(
                iconRes = R.drawable.ic_app_play_center,
                contentDescription = "播放",
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(post.author, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(post.caption, color = Color.White.copy(alpha = 0.78f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text("${formatCount(post.likes)} 赞 · ${formatCount(post.comments)} 评论", color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun PublishScreen(
    posts: List<VideoPost>,
    onPublish: (VideoPost) -> Unit,
    onCancel: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("今天的本地短视频展示") }
    var topic by remember { mutableStateOf("#校园大作业 #Compose") }
    val selected = posts.getOrElse(selectedIndex) { posts.first() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    OriginalIcon(
                        iconRes = R.drawable.ic_app_close,
                        contentDescription = "取消发布",
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text("发布作品", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("选择内置视频素材", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(posts.size) { index ->
                    val post = posts[index]
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedIndex = index }
                    ) {
                        Image(painterResource(post.coverRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (index == selectedIndex) 0.12f else 0.38f)))
                        if (index == selectedIndex) {
                            OriginalIcon(
                                iconRes = R.drawable.ic_app_check,
                                contentDescription = "已选择",
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp)
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("话题") },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text("不接后端") },
                    leadingIcon = {
                        OriginalIcon(
                            iconRes = R.drawable.ic_app_check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("本地展示") },
                    leadingIcon = {
                        OriginalIcon(
                            iconRes = R.drawable.ic_app_play,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            Button(
                onClick = {
                    onPublish(
                        selected.copy(
                            id = "local-${System.currentTimeMillis()}",
                            author = "我",
                            handle = "@local_me",
                            caption = title.ifBlank { "本地发布作品" },
                            topic = topic.ifBlank { "#本地作品" },
                            likes = 0,
                            comments = 0,
                            shares = 0
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE2C55))
            ) {
                OriginalIcon(
                    iconRes = R.drawable.ic_app_publish,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("发布到本地推荐流", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MessagesScreen() {
    Scaffold(
        containerColor = Color.Black,
        topBar = { SimpleTopBar("消息", "本地 mock 会话", onSearch = {}) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickMessageAction(R.drawable.ic_app_message, "互动消息", Modifier.weight(1f))
                    QuickMessageAction(R.drawable.ic_app_friend, "朋友动态", Modifier.weight(1f))
                    QuickMessageAction(R.drawable.ic_app_creation_notice, "创作通知", Modifier.weight(1f))
                }
            }
            items(MockRepository.chatPreviews()) { chat ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Image(painterResource(chat.avatarRes), null, Modifier.size(54.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(chat.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(chat.time, color = Color.White.copy(alpha = 0.42f), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(chat.message, color = Color.White.copy(alpha = 0.64f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (chat.unread > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFFE2C55)), contentAlignment = Alignment.Center) {
                            Text(chat.unread.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMessageAction(iconRes: Int, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OriginalIcon(
            iconRes = iconRes,
            contentDescription = label,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun ProfileScreen(posts: List<VideoPost>) {
    Scaffold(containerColor = Color.Black) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(bottom = 78.dp).verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                Image(painterResource(R.drawable.cover_gradient), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                Row(
                    Modifier.align(Alignment.BottomStart).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(painterResource(R.drawable.avatar_dark_logo), null, Modifier.size(78.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("我", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("抖音号: local_me", color = Color.White.copy(alpha = 0.64f), fontSize = 13.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatBlock("12", "获赞")
                StatBlock("3", "关注")
                StatBlock("8", "粉丝")
            }
            FilledTonalButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("编辑资料")
            }
            Spacer(Modifier.height(20.dp))
            Text("作品", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
            Spacer(Modifier.height(12.dp))
            posts.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    row.forEach { post ->
                        Box(Modifier.weight(1f).aspectRatio(9f / 13f).background(Color.DarkGray)) {
                            Image(painterResource(post.coverRes), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(formatCount(post.likes), color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomStart).padding(7.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.56f), fontSize = 12.sp)
    }
}

@Composable
private fun SearchScreen(posts: List<VideoPost>, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, posts) {
        if (query.isBlank()) posts else posts.filter {
            it.caption.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true) ||
                it.topic.contains(query, ignoreCase = true)
        }
    }
    Scaffold(containerColor = Color.Black) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        OriginalIcon(
                            iconRes = R.drawable.ic_app_search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    placeholder = { Text("搜索视频、作者、话题") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
                TextButton(onClick = onBack) { Text("取消") }
            }
            Spacer(Modifier.height(16.dp))
            Text("本地结果 ${results.size}", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(results) { FeedPreviewCard(it) }
            }
        }
    }
}

@Composable
private fun CommentSheet(
    post: VideoPost,
    comments: List<Comment>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.72f),
            color = Color(0xFF151518),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(64.dp))
                    Text(
                        "${comments.size} 条评论",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.width(64.dp)) {
                        Text("关闭")
                    }
                }
                Spacer(Modifier.height(14.dp))
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
                        placeholder = { Text("写评论给 ${post.author}") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                    )
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSend(text.trim())
                                text = ""
                            }
                        }
                    ) {
                        OriginalIcon(
                            iconRes = R.drawable.ic_app_send,
                            contentDescription = "发送评论",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareSheet(post: VideoPost, onDismiss: () -> Unit, onShare: (String) -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            color = Color(0xFF151518),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分享 ${post.author} 的作品", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    listOf("复制链接", "微信", "朋友圈", "保存本地").forEach { target ->
                        Column(
                            modifier = Modifier.width(72.dp).clickable { onShare(target) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_feed_share),
                                    contentDescription = target,
                                    modifier = Modifier.size(30.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(target, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SimpleTopBar(title: String, subtitle: String, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp)
        }
        IconButton(onClick = onSearch) {
            OriginalIcon(
                iconRes = R.drawable.ic_app_search,
                contentDescription = "搜索",
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = {}) {
            OriginalIcon(
                iconRes = R.drawable.ic_app_more,
                contentDescription = "更多",
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(100.dp),
        shadowElevation = 4.dp
    ) {
        Text(text, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
private fun OriginalIcon(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}

private data class NavItem(val screen: AppScreen, val label: String)

private fun formatCount(value: Int): String {
    return if (value >= 10000) {
        val v = value / 1000 / 10f
        "${v}万"
    } else {
        value.toString()
    }
}
