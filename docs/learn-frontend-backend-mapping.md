# 前后端映射学习指南（从 cin 到 HTTP 到 gRPC）

> 本文档面向"只懂 cin/cout 控制台输入输出"的学习者，逐步讲清楚：前端按钮点击后，数据是如何一路传递到后端、再到数据库的，以及后端返回的数据又是如何回到屏幕上的。

## Scope
- 本次分析范围：整个项目的请求流转全链路——从前端 UI 到后端 Controller/Service/Repository、再到 MySQL 与 gRPC Recommend Service，以及认证、统一响应、异常处理等横切机制。
- 用一条"获取推荐视频流"的主链路作为贯穿示例（它同时涉及 HTTP + gRPC + MySQL 三种通信），再用"发表评论"作为对比示例（纯 HTTP + MySQL，不涉及 gRPC）。
- 明确不包含：Bonus 功能（收藏/关注/分享/私信/搜索）、视频上传二进制流的存储细节、部署/运维。

---

## 一、先建立全局认知：项目分了哪几层

把整个系统想象成 4 个房间，数据要依次穿过它们：

```text
┌──────────────────────────┐      ┌──────────────────────────┐
│  ① Android 前端 App       │      │  ② Spring Boot API Server│
│  (Kotlin + Compose UI)    │      │  (Java, Controller/Service│
│                           │ HTTP │   /Repository)            │
│  UI 点击 → Repository ────┼─────►│                           │
│                           │ JSON │                           │
└──────────────────────────┘      └─────────┬────────────────┘
                                            │
                          ┌─────────────────┼─────────────────┐
                          │ gRPC（仅推荐流）  │  JDBC（其他接口）  │
                          ▼                 ▼
              ┌────────────────────┐   ┌────────────────────┐
              │ ③ Recommend Service│   │ ④ MySQL 8          │
              │   (Java + gRPC)    │   │  (sql/schema.sql)  │
              │   只管"推荐排序"    │   │  真正存数据的地方    │
              └─────────┬──────────┘   └────────────────────┘
                        │ JDBC
                        ▼
                   ┌────────────────────┐
                   │ ④ MySQL 8          │
                   └────────────────────┘
```

关键认知点（非常重要，理解了这两条，后面就只是细节）：

1. **前端永远只跟 ② API Server 说话**，用 HTTP + JSON。前端完全不知道 gRPC、不知道 MySQL 的存在。
2. **只有"推荐视频流"这一个功能**会从 ② 跨到 ③ Recommend Service（走 gRPC）；其余所有功能（登录、点赞、评论、发布、删除…）都是 ② 直接读写 ④ MySQL。这是课程硬性要求："主应用访问推荐系统必须用 gRPC"。

> 类比 cin/cout：你以前写的 `cin >> x` 是"进程内函数调用式"的输入。这里换成"网络函数调用"——前端发一个 HTTP 请求 = 调用后端一个函数，后端返回 JSON = 函数返回值。本质还是"传参数 → 执行 → 拿结果"，只是中间隔了网络。

---

## 二、和 cin 类比：一次"网络函数调用"长什么样

先看一个最熟悉的对照：

| 你以前（控制台）        | 这个项目（网络）                          |
|------------------------|------------------------------------------|
| `cin >> username;`     | HTTP 请求体里带 `{"username":"abc"}`      |
| `cout << result;`      | HTTP 响应体里返回 `{"code":0,"data":{...}}`|
| 函数参数                | URL 路径参数(`/videos/123`) + 查询参数(`?limit=10`) + 请求体(`@Body`) |
| 函数返回值              | HTTP 状态码 + JSON 响应体                 |

这个"远程函数调用"的具体形态，在前端由一个叫 **Retrofit** 的库来描述，在后端由 **Spring 的 `@RestController`** 来接收。两边用同一套"契约"对上号。

---

## 三、前端是怎么发起请求的（三层结构）

前端网络代码分三层，从上到下越越接近"HTTP 字节"：

### 第 1 层：UI 层（`DouyinApp.kt`）—— 用户点了什么按钮

这是离用户最近的一层。以"打开评论并发送一条评论"为例（`DouyinApp.kt:163-222`）：

```kotlin
// 用户点"发送"按钮 → onSend 回调被触发
onSend = { text ->
    if (ApiClient.isLoggedIn()) {
        scope.launch {  // 协程：网络请求不能卡主线程（UI 线程）
            val postIdNum = post.id.toLongOrNull()
            if (postIdNum != null) {
                val result = ApiRepository.postComment(postIdNum, text)  // ← 调第 2 层
                if (result.isSuccess) {
                    // 成功后刷新评论列表、弹 toast
                    ...
                } else {
                    toast = "评论失败: ${result.exceptionOrNull()?.message}"
                }
            }
        }
    }
}
```

要点：
- `scope.launch { }` 是 Kotlin 协程。网络是慢操作，必须放到后台线程，否则界面会卡死。
- UI 只调 `ApiRepository.xxx()`，不关心 HTTP 细节——这就是"分层"的好处。

### 第 2 层：Repository 层（`ApiRepository.kt`）—— 业务封装 + 错误处理

`ApiRepository`（单例 object）把每个后端接口包成一个 `suspend fun`，统一做三件事（`ApiRepository.kt:152-161`）：

```kotlin
suspend fun postComment(videoId: Long, content: String): Result<PostCommentData> {
    return apiCall {
        val response = ApiClient.apiService!!.postComment(videoId, PostCommentRequest(content)) // ← 调第 3 层
        requireSuccess(response)            // 1. 检查 HTTP 和业务码
        response.body()!!.data!!            // 2. 拆出 data 字段
    }
}
```

`apiCall`（`ApiRepository.kt:188-200`）是个统一的"try-catch 壳"：把所有异常都转成 `Result.failure(ApiException)`，这样 UI 层只需判断 `result.isSuccess`。

### 第 3 层：Retrofit 接口（`ApiService.kt`）—— HTTP 请求的"声明"

这是最神奇的一层。**你只写一个 interface，Retrofit 会自动帮你生成真正的 HTTP 请求代码。**（`ApiService.kt:65-70`）

```kotlin
interface ApiService {
    @POST("api/v1/videos/{videoId}/comments")      // ← HTTP 方法 + 路径
    @Headers("Content-Type: application/json")
    suspend fun postComment(
        @Path("videoId") videoId: Long,            // ← 路径参数：填进 {videoId}
        @Body request: PostCommentRequest          // ← 请求体：序列化成 JSON
    ): Response<ApiResponseWrapper<PostCommentData>> // ← 返回值：自动反序列化
}
```

每个注解都对应 HTTP 的一个部分，对照表（这是全文最该记住的一张表）：

| Retrofit 注解            | 对应 HTTP 什么                          | 例子                          |
|-------------------------|----------------------------------------|-------------------------------|
| `@GET`/`@POST`/`@PUT`/`@DELETE` | HTTP 方法 + URL                  | `@POST("api/v1/videos")`      |
| `@Path("x")`            | URL 路径里的 `{x}` 占位符                | `/videos/{videoId}` → `/videos/123` |
| `@Query("x")`           | URL 问号后的查询参数                     | `?limit=10`                   |
| `@Body`                 | 请求体（自动用 Gson 转成 JSON）          | `{"content":"好看"}`          |
| `@Headers`              | 请求头                                  | `Content-Type: application/json` |

所以上面那段接口，最终发出去的 HTTP 请求是：

```http
POST /api/v1/videos/123/comments HTTP/1.1
Content-Type: application/json
Authorization: Bearer eyJhbGc...（自动加的，见下一节）

{"content":"好看"}
```

### 横切层：`ApiClient.kt` —— OkHttp + Retrofit 的工厂 + Token 拦截器

`ApiClient`（单例）负责"造"出 `ApiService` 实例，并做两件横切的事（`ApiClient.kt:51-79`）：

1. **自动加 Token**：用 OkHttp 的 `Interceptor`，每次请求都自动塞上 `Authorization: Bearer <token>` 头（`ApiClient.kt:56-62`）。这就是为什么 `ApiService` 里从没出现过 token——它在更底层被统一处理了。
2. **Token 持久化**：登录成功后 `setToken()` 把 token 存进 Android `SharedPreferences`，App 重启也不丢（`ApiClient.kt:37-45`）。

```kotlin
val authInterceptor = Interceptor { chain ->
    val requestBuilder = chain.request().newBuilder()
    token?.let { requestBuilder.addHeader("Authorization", "Bearer $it") }
    chain.proceed(requestBuilder.build())
}
```

> 类比 cin：OkHttp 相当于"真正在读键盘的系统调用"，Retrofit 相当于"帮你把 `cin >> name >> age` 这种语法糖展开成多次 read 的封装"。这里 Retrofit 把"接口方法调用"展开成"构造 HTTP 请求 → 发送 → 解析响应"。

---

## 四、后端是怎么接收并处理的（四层结构）

后端也是分层的，和前端是镜像关系。请求穿过的顺序：

```text
HTTP 请求
  → Filter（认证/日志，Servlet 过滤器）     ← 横切，所有请求都过
  → Controller（@RestController，路由分发） ← 第 1 层
  → Service（@Service，业务逻辑）           ← 第 2 层
  → Repository（@Repository，SQL）          ← 第 3 层
  → MySQL
```

### 第 0 层（横切）：认证 Filter —— 拦截请求、验 Token、塞 userId

在请求到达 Controller 之前，先经过 `BearerAuthenticationFilter`（`BearerAuthenticationFilter.java:42-75`）。这是 Spring 的 `OncePerRequestFilter`，每个请求执行一次：

```java
protected void doFilterInternal(request, response, filterChain) {
    if (!requiresAuthentication(request)) {   // 注册/登录/健康检查不需要 token
        filterChain.doFilter(request, response);
        return;
    }
    String token = request.getHeader("Authorization").substring("Bearer ".length());
    long userId = tokenService.parseUserId(token);     // 验签 + 解析出 userId
    RequestContext.setCurrentUserId(request, userId);   // 把 userId 塞进 request 属性
    filterChain.doFilter(request, response);            // 放行给 Controller
}
```

两个关键设计：
1. **白名单制**：`requiresAuthentication()`（`BearerAuthenticationFilter.java:77-91`）显式列出哪些路径需要登录。只有 register/login/health 不需要。
2. **userId 的传递方式**：解析出 userId 后，塞进 `HttpServletRequest` 的 attribute（`RequestContext.java:21-23`）。后续 Controller/Service 通过 `RequestContext.currentUserId(request)` 取出来。这样业务代码不用每个方法都传 userId 参数。

> Token 是自签的 JWT-like 格式（HMAC-SHA256 签名），见 `HmacTokenService.java`。`parseUserId` 会校验签名和过期时间，任何一步失败就抛异常 → Filter 返回 401。

### 第 1 层：Controller —— 路由 + 参数绑定 + 响应包装

Controller 是后端的"前台"，负责把 URL 映射到 Java 方法。它的注解和前端的 Retrofit 是一一对应的（这是契约的两面）：

```java
@RestController                         // 告诉 Spring：这是个处理 HTTP 的类
@RequestMapping("/api/v1")              // 类级别公共前缀
public class CommentController {

    @PostMapping(value = "/videos/{videoId}/comments")  // ← 和前端 @POST 完全对应
    public ResponseEntity<ApiResponse<PostCommentResponse>> postComment(
            HttpServletRequest request,
            @PathVariable long videoId,       // ← 对应前端 @Path
            @RequestBody PostCommentRequest body  // ← 对应前端 @Body
    ) {
        PostCommentResponse response = commentService.postComment(request, videoId, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));   // ← 统一包成 {code,message,data}
    }
}
```

Controller 的职责很薄：**只做"翻译"——HTTP ↔ Java 对象**，真正的业务逻辑全部下沉到 Service。注意三个特点：
- 返回值统一包成 `ApiResponse<T>`（下一节详述）。
- 几乎不写业务判断（`if/else` 很少），直接转发给 Service。
- 不直接碰数据库。

### 第 2 层：Service —— 业务逻辑、校验、事务编排

Service 是真正"干活"的地方。以发表评论为例（`CommentService.java:47-76`）：

```java
@Transactional                                   // ← 开数据库事务
public PostCommentResponse postComment(request, videoId, body) {
    if (videoId <= 0) throw new BusinessException(INVALID_PARAMETER, ...);  // 1. 参数校验
    long currentUserId = currentUserId(request);                              // 2. 取登录用户
    String content = validateContent(body.content());                         // 3. 业务校验（长度等）

    Comment comment;
    try {
        comment = commentRepository.create(videoId, currentUserId, content);  // 4. 写库（调第 3 层）
    } catch (IllegalStateException e) {
        throw new BusinessException(VIDEO_NOT_FOUND);                         // 5. 异常翻译
    }

    long commentCount = commentRepository.countByVideoId(videoId);            // 6. 再查一次计数
    return new PostCommentResponse(toResponse(comment), commentCount);        // 7. 组装返回
}
```

Service 的典型职责清单（评论 Service 全都体现了）：
1. **参数 & 业务校验**：`videoId <= 0`、内容为空、超长等。校验失败抛 `BusinessException`。
2. **取当前用户**：从 `RequestContext` 拿 Filter 塞进去的 userId。
3. **调 Repository 读写数据库**。
4. **异常翻译**：把底层 `IllegalStateException` / `DuplicateKeyException` 翻译成对前端有意义的 `BusinessException(ErrorCode.XXX)`。
5. **事务边界**：`@Transactional` 保证多个写操作要么全成功要么全回滚。
6. **组装 DTO**：把数据库模型转成给前端看的响应对象（`toResponse`）。

### 第 3 层：Repository —— 纯 SQL，用 JdbcTemplate

Repository 只管"和数据库说话"，用 Spring 的 `JdbcTemplate` 手写 SQL（没用 MyBatis、没用 JPA）。`CommentRepository.create()`（`CommentRepository.java:86-106`）：

```java
public Comment create(long videoId, long authorId, String content) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    int affectedRows = jdbcTemplate.update(connection -> {
        PreparedStatement statement = connection.prepareStatement(INSERT_COMMENT_SQL, RETURN_GENERATED_KEYS);
        statement.setLong(1, videoId);
        statement.setLong(2, authorId);
        statement.setString(3, content);
        statement.setLong(4, videoId);
        return statement;
    }, keyHolder);
    if (affectedRows == 0) {
        throw new IllegalStateException("video not found or deleted: " + videoId);
    }
    ...
}
```

对应的 SQL 很有讲究（`CommentRepository.java:24-29`）——用 `INSERT ... SELECT FROM videos WHERE deleted_at IS NULL` 在一条语句里完成"校验视频存在 + 插入评论"，避免先查后插的竞态（TOCTOU）：

```sql
INSERT INTO comments (video_id, author_id, content, created_at)
SELECT ?, ?, ?, CURRENT_TIMESTAMP(3)
FROM videos
WHERE id = ? AND deleted_at IS NULL
```

> 这层就是你熟悉的"执行 SQL 拿结果"，和 cin 读数据本质一样，只是数据源从键盘换成了 MySQL。`?` 是参数占位符（防 SQL 注入，绝不要字符串拼接 SQL）。

---

## 五、两条横切机制（贯穿所有接口）

### 机制 A：统一响应包装 `ApiResponse<T>`

所有接口返回值都是这个壳（`ApiResponse.java:3-9`）：

```java
public record ApiResponse<T>(int code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> success(T data) { return new ApiResponse<>(0, "ok", data, ...); }
    public static ApiResponse<Void> failure(ErrorCode errorCode) { ... }
}
```

- `code=0` 表示成功；非 0 是业务错误码（定义在 `ErrorCode`）。
- `requestId` 用于全链路日志追踪。
- 前端 `ApiRepository.requireSuccess()` 就是在检查这个 `code != 0`（`ApiRepository.kt:175-186`）。

### 机制 B：全局异常处理 `GlobalExceptionHandler`

Controller/Service 不用写 `try-catch` 返回错误响应，只要抛 `BusinessException(ErrorCode.XXX)`。`GlobalExceptionHandler`（`@RestControllerAdvice`）会统一接住所有异常，翻译成对应的 HTTP 状态码 + `ApiResponse.failure()`（`GlobalExceptionHandler.java:19-29`）：

```java
@ExceptionHandler(BusinessException.class)
public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex, ...) {
    ErrorCode errorCode = ex.errorCode();
    return ResponseEntity.status(errorCode.httpStatus())
                         .body(ApiResponse.failure(errorCode, ex.getMessage()));
}
```

这样业务代码只管"抛异常"，"异常 → HTTP 响应"的映射集中在一处维护。

---

## 六、完整端到端示例：推荐视频流（HTTP → gRPC → MySQL 三通信全齐）

这是项目里最复杂的一条链路，理解了它，其他接口都是它的简化版。

### 调用链全景图

```text
[前端] DouyinApp / ViewModel
   └─ ApiRepository.getRecommendedVideos(cursor, limit)        ApiRepository.kt:71
       └─ ApiService.getRecommendedVideos(cursor, limit)        ApiService.kt:74-78
           └─ Retrofit+OkHttp 发 HTTP GET
═════════════════════════════════════════════════════════════════ HTTP/JSON 边界
[后端 API Server]
   ① BearerAuthenticationFilter.doFilterInternal                BearerAuthenticationFilter.java:42
      └─ HmacTokenService.parseUserId(token) → userId
      └─ RequestContext.setCurrentUserId(request, userId)
   ② FeedController.recommendedVideos(cursor, limit)            FeedController.java:23
      └─ FeedService.listRecommended(request, cursor, limit)    FeedService.java:42
         ├─ RequestContext.currentUserId(request) → userId
         ├─ grpcClient.listRecommended(requestId, userId, cursor, limit)  ← gRPC 调用
─────────────────────────────────────────────────────────────── gRPC 边界
[Recommend Service]
   ③ RecommendServiceImpl.listRecommendedVideos                 RecommendServiceImpl.java:29
      ├─ 校验 userId / limit
      ├─ CursorCodec.decode(cursor)
      └─ repository.findRecommended(userId, cursor, limit+1)   RecommendRepository.java:55
─────────────────────────────────────────────────────────────── JDBC 边界
[MySQL]  执行推荐 SQL（like_count DESC, created_at DESC, id DESC，
         排除已看过的 video_views）                            RecommendRepository.java:24-47
─────────────────────────────────────────────────────────────── 返回 video_ids[]
   ③ 组装 ListRecommendedVideosResponse{video_ids, next_cursor, has_more}
═════════════════════════════════════════════════════════════════ 回到 API Server
   ② FeedService 拿到 video_ids[]，对每个 id：
      └─ videoRepository.findPostById(videoId, userId)          VideoRepository.java:228
         └─ JDBC 查 videos JOIN users + 子查询计数              VideoRepository.java:53-86
      └─ videoPostAssembler.toResponse(post, userId)            （组装带 viewerState 的 DTO）
   ② 返回 RecommendedFeedResponse{items, nextCursor, hasMore, strategy}
   ② 包成 ApiResponse.success(...) 返回
═════════════════════════════════════════════════════════════════ HTTP 响应回前端
[前端] Retrofit 反序列化成 ApiResponseWrapper<RecommendedFeedData>
   └─ ApiRepository 拆出 data
   └─ UI 把 items 渲染成视频列表
```

### 这条链路的几个关键设计点

1. **为什么推荐流要拆成两次查询（先 gRPC 拿 id，再查详情）？**
   - Recommend Service 只负责"排序 + 去重"，返回的是 `video_ids[]`（`recommend.proto:23`），非常轻量。
   - 详情（作者、点赞数、是否已赞）由 API Server 的 `VideoRepository.findPostById` 查（`VideoRepository.java:53-86`）。这样推荐逻辑的变更不会影响详情组装，职责分离。

2. **gRPC 的"契约"是 proto 文件**（`recommend.proto`），作用和前端的 Retrofit 接口一样——让两边对上号。区别是 gRPC 用二进制 + HTTP/2，比 JSON 快，但人读不了。

3. **推荐 SQL 的核心**（`RecommendRepository.java:24-47`）：
   - 实时 `COUNT(*)` 算 like_count（不用表里的冗余字段，避免计数漂移）。
   - `NOT EXISTS (video_views ...)` 排除"用户已经看过的视频"——这就是课程要求"已访问视频不再推荐"的实现。
   - 游标分页用 `(like_count, created_at, id)` 三元组，保证翻页稳定不重复。

4. **gRPC 客户端是单例 Bean**（`GrpcConfig.java:25-36`），`ManagedChannel` 复用连接，不会每次请求重建。

---

## 七、对比示例：发表评论（纯 HTTP + MySQL，无 gRPC）

和上面推荐流相比，评论链路少了 gRPC 那一跳，更接近"普通 CRUD"：

```text
[前端] CommentSheet onSend
   └─ ApiRepository.postComment(videoId, content)              ApiRepository.kt:152
       └─ ApiService.postComment(videoId, PostCommentRequest)  ApiService.kt:65
           └─ HTTP POST /api/v1/videos/{videoId}/comments  Body:{"content":"..."}
───────────────────────────────────────────────────────────────
[后端] BearerAuthenticationFilter（验 token → userId）
   └─ CommentController.postComment(videoId, body)             CommentController.java:48
      └─ CommentService.postComment(request, videoId, body)    CommentService.java:47
         ├─ 校验 videoId / content
         └─ commentRepository.create(videoId, userId, content) CommentRepository.java:86
            └─ INSERT INTO comments ... SELECT FROM videos ... （一条 SQL 兼顾校验+插入）
         └─ commentRepository.countByVideoId(videoId)
         └─ return PostCommentResponse(comment, commentCount)
   └─ GlobalExceptionHandler 兜底（如有 BusinessException 自动转错误响应）
───────────────────────────────────────────────────────────────
[前端] 拿到 PostCommentData → 重新 getComments 刷新列表 → UI 更新
```

记住这个对比：**推荐流 = HTTP + gRPC + MySQL（三跳）；评论/点赞/发布/删除 = HTTP + MySQL（两跳）**。课程之所以强制推荐流走 gRPC，就是为了考核"微服务间通信"这个知识点。

---

## 八、数据库表速查（`sql/schema.sql`）

| 表 | 作用 | 关键字段 | 谁在写 | 谁在读 |
|---|---|---|---|---|
| `users` | 用户账号 | id, username, password_hash, nickname | 注册 | 登录、所有 JOIN 取作者 |
| `videos` | 视频主表 | id, author_id, caption, video_url, deleted_at（软删除） | 发布、软删除 | 推荐、详情、我的视频 |
| `video_likes` | 点赞关系 | (user_id, video_id) UNIQUE | 点赞/取消 | 实时 COUNT 算 like_count |
| `video_views` | 观看记录 | (user_id, video_id) UNIQUE | 上报观看 | **推荐去重**（NOT EXISTS） |
| `comments` | 评论 | video_id, author_id, content, deleted_at | 发评论 | 评论列表 |
| `request_logs` | 请求日志 | request_id, method, path, duration_ms | 日志 Filter | 运维排查 |

两个特别注意的设计：
- **软删除**：`videos` / `comments` 都用 `deleted_at IS NULL` 表示"未删除"，从不 `DELETE`。好处是数据可追溯，坏处是所有查询都要带 `deleted_at IS NULL`。
- **没有冗余计数器**：`videos.like_count` 字段虽然存在但永远是 0（schema 注释里也说了）。所有地方都用 `SELECT COUNT(*) FROM video_likes` 实时算，换取强一致性。课程数据量小，性能可接受。

---

## 九、给学习者的建议阅读顺序

1. 先读 `ApiService.kt`（前端契约，一张表看完所有接口）。
2. 再读任意一个 Controller（如 `CommentController.java`），对照注解理解 HTTP ↔ Java。
3. 跟着 Service（`CommentService.java`）看业务流程。
4. 跟着 Repository（`CommentRepository.java`）看 SQL。
5. 最后挑战推荐流全链路（第六节），把 gRPC 串起来。
6. 横切机制（Filter / ApiResponse / GlobalExceptionHandler）随时回查。

---

## Confirmed Facts（关键已确认事实）

| Fact | Evidence | Source Type | Confidence |
|---|---|---|---|
| 前端只用 HTTP/JSON 调 API Server，不直连 gRPC/MySQL | `ApiService.kt` 全部是 Retrofit 注解；AGENTS.md 明确规定 | Source Code | Confirmed |
| 只有推荐流走 gRPC，其他接口 API Server 直连 MySQL | `FeedService.java:48` 调 `grpcClient`；`CommentService.java:64` 直接调 `commentRepository` | Source Code | Confirmed |
| Token 通过 OkHttp Interceptor 自动加到每个请求头 | `ApiClient.kt:56-62` authInterceptor | Source Code | Confirmed |
| userId 由 Filter 解析后塞进 request attribute，Service 取用 | `BearerAuthenticationFilter.java:73` + `RequestContext.java:21-34` | Source Code | Confirmed |
| 所有响应统一包成 `{code,message,data,requestId}` | `ApiResponse.java:3-9`；前端 `ApiResponseWrapper` 结构一致 | Source Code | Confirmed |
| 推荐去重依赖 `video_views` 表的 NOT EXISTS | `RecommendRepository.java:32-37` | Source Code | Confirmed |
| like_count 实时 COUNT，不维护冗余计数器 | `RecommendRepository.java:24-27`；schema.sql 注释 line 37-39 | Source Code + Database Schema | Confirmed |
| 评论插入用 `INSERT...SELECT FROM videos WHERE deleted_at IS NULL` 防 TOCTOU | `CommentRepository.java:24-29` | Source Code | Confirmed |

## Open Questions
- 视频上传的二进制流（`uploads/` 目录 + `LocalUploadStorageService`）本次未深入，发布接口 `POST /api/v1/videos` 当前走 JSON（`videoUrl` 字段），是否还有 multipart 上传通道未确认。需要时再读 `video/storage/` 与 `VideoController`。
- 前端 `MainActivity.kt` 初始化 `ApiClient.init()` 的时机与 baseUrl 配置入口未细读，需结合真机调试确认。
- 请求日志 `RequestLoggingFilter` 如何把请求/响应体记录到 `request_logs` 表未展开（只读了 schema），如需排查接口可再读 `logging/` 包。

## Recommended Next Step
- `review-agent`：如果你想把某条链路（如点赞、发布）也做成像第六节那样的"端到端调用链图"做代码审查，可以指定接口让我继续画。
- `task-planning-flow`：如果想动手改某个接口（例如加一个新字段），用它来拆任务。
- 直接提问：针对上面任何一节（比如"gRPC 的 ManagedChannel 生命周期"、"游标分页为什么用三元组"）深入追问，我可以基于已读文件继续解释。
