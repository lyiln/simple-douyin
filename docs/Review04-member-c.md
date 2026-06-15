# PR #3 Code Review 报告 — 成员C 评论闭环 + 前端联调

**分支:** `feature/T23-T27-comments-frontend`  
**日期:** 2026-06-14  
**审查人:** 成员A  
**变更范围:** 评论模块（T23-T25）、前端联调（T26-T27）、验收文档（T28-T31）  
**变更文件:** 27 个（+1970 / −80 行）

---

## 变更清单

### 后端新增

| 文件 | 说明 |
|------|------|
| `comment/controller/CommentController.java` | 2 个端点：GET/POST `/videos/{id}/comments` |
| `comment/service/CommentService.java` | 评论业务逻辑 + 游标分页 |
| `comment/repository/CommentRepository.java` | SQL 操作（INSERT、分页查询、COUNT） |
| `comment/model/Comment.java` | 评论领域模型 |
| `comment/model/CommentPageCursor.java` | 游标模型 |
| `comment/dto/CommentResponse.java` | 评论响应 DTO |
| `comment/dto/GetCommentsResponse.java` | 评论列表响应 |
| `comment/dto/PostCommentRequest.java` | 发表评论请求 |
| `comment/dto/PostCommentResponse.java` | 发表评论响应 |
| `comment/controller/CommentControllerTest.java` | 18 个测试用例 |

### 后端修改

| 文件 | 变更 |
|------|------|
| `auth/security/BearerAuthenticationFilter.java` | +评论路径鉴权、+FEED_PATH、import 重排 |

### 前端新增/修改

| 文件 | 说明 |
|------|------|
| `network/ApiClient.kt` | Retrofit 客户端单例 + token 管理 |
| `network/ApiService.kt` | API 接口定义（14 个端点） |
| `network/model/ApiModels.kt` | 全部 DTO 模型 |
| `data/ApiRepository.kt` | 数据仓库层 |
| `ui/DouyinApp.kt` | 评论功能接入真实 API |
| `MainActivity.kt` | ApiClient 初始化 |
| `build.gradle.kts` | +Retrofit、OkHttp、Gson、Coroutines |
| `AndroidManifest.xml` | +INTERNET 权限、cleartext |

---

## 一、严重问题

### 🔴 严重 | 前端 `ViewData.success` 与后端 `ViewResponse.viewed` 字段名不匹配

**文件:** [ApiModels.kt:118-122](frontend/app/src/main/java/com/example/douyin/network/model/ApiModels.kt)

```kotlin
data class ViewData(
    val videoId: Long,
    val success: Boolean,   // ❌ 后端返回 "viewed"，不是 "success"
    val viewCount: Long,
    val created: Boolean
)
```

后端 `ViewResponse` 序列化输出：
```json
{"videoId": 1, "viewed": true, "viewCount": 5}
```

Gson 反序列化时 `success` 字段在 JSON 中不存在，默认为 `false`。**`recordView()` 在前端静默失效** — 无论后端返回什么，前端始终认为操作失败。

**修复：**
```kotlin
val viewed: Boolean,   // 与后端字段名一致
```

---

### 🔴 严重 | 前端 `HealthData` 结构与后端完全不匹配

**文件:** [ApiModels.kt:151-155](frontend/app/src/main/java/com/example/douyin/network/model/ApiModels.kt)

```kotlin
data class HealthData(
    val apiServer: String,          // ❌ 后端返回嵌套结构
    val mysql: String,
    val grpcRecommendService: String
)
```

后端返回的实际 JSON：
```json
{
  "status": "UP",
  "components": {
    "apiServer": "UP",
    "mysql": "UP",
    "recommendService": "DOWN"
  }
}
```

字段完全无法映射，所有组件状态为 null。**前端健康检查完全失效。**

**修复：**
```kotlin
data class HealthData(
    val status: String,
    val components: Map<String, String>
)
```

---

### 🔴 严重 | `comment_count` 计数器漂移（与成员A PR #2 同样的问题）

**文件:** [CommentRepository.java:100-113](backend/api-server/src/main/java/com/simpledouyin/api/comment/repository/CommentRepository.java)

```java
// create() — 两步独立 SQL
jdbcTemplate.update(INSERT_COMMENT_SQL, videoId, authorId, content);   // ①
jdbcTemplate.update(INCREMENT_COMMENT_COUNT_SQL, videoId);             // ②
```

`countByVideoId()` 使用 `SELECT COUNT(*)`（正确），但 `VideoPostResponse.commentCount` 从 `videos.comment_count` 列读取（可能漂移）。两套数据源长期不一致。

**修复：** `comment_count` 改为实时 `SELECT COUNT(*)` 子查询（与成员A方案一致）

---

### 🔴 严重 | TOCTOU 竞态 — `videoExists()` 和 `create()` 之间视频可能被软删除

**文件:** [CommentService.java:60-66](backend/api-server/src/main/java/com/simpledouyin/api/comment/service/CommentService.java)

```java
if (!commentRepository.videoExists(videoId)) {  // ① 检查
    throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
}
Comment comment = commentRepository.create(...);  // ② 插入 — 可能在 ① 和 ② 之间视频被删除
```

**修复：** `INSERT_COMMENT_SQL` 加子查询守卫：

```sql
INSERT INTO comments (video_id, author_id, content, created_at)
SELECT ?, ?, ?, CURRENT_TIMESTAMP(3)
FROM videos WHERE id = ? AND deleted_at IS NULL
```

---

## 二、中等问题

### 🟡 中等 | `Comment.java` 使用传统类而非 record，风格不一致

**文件:** [Comment.java](backend/api-server/src/main/java/com/simpledouyin/api/comment/model/Comment.java)（67行）

项目中其他模型全部使用 record（如 `CommentPageCursor`、`LikeResponse`、`ViewResponse`）。Comment 使用传统 POJO 类 + 手动 getter（68行 vs 1行record），风格不统一。

**修复：** 改为 `public record Comment(long id, long videoId, long authorId, ...) {}`

---

### 🟡 中等 | 合并冲突风险 — `BearerAuthenticationFilter` import 重排

**文件:** [BearerAuthenticationFilter.java](backend/api-server/src/main/java/com/simpledouyin/api/auth/security/BearerAuthenticationFilter.java)

该分支对 import 语句做了字母序重排。成员A 分支（`feature/member-a-like-view-health`）也修改了同一文件。无论哪个先合并，后合并的都会产生冲突。

**建议：** 统一 import 排序风格，协调合并顺序

---

### 🟡 中等 | 死代码 — `isVideoDeletePath()` 从未被调用

**文件:** [BearerAuthenticationFilter.java:100](backend/api-server/src/main/java/com/simpledouyin/api/auth/security/BearerAuthenticationFilter.java)

```java
private boolean isVideoDeletePath(String path) {
    return isVideoActionPath(path, "");
}
```

该方法在过滤器中定义但从未被调用。

---

## 三、轻微问题

### 🟢 低 | 前端 `recordView()` 不发送 `watchDurationMs`

**文件:** [ApiRepository.kt:120-125](frontend/app/src/main/java/com/example/douyin/data/ApiRepository.kt)

```kotlin
suspend fun recordView(videoId: Long): Result<ViewData> {
    ...
    ViewRequest(source = DEFAULT_SOURCE)  // watchDurationMs 未传
}
```

后端支持 `watchDurationMs` 字段但前端始终不发送。

---

### 🟢 低 | 软删除评论时 `comment_count` 不会递减

`comments` 表有 `deleted_at` 列，但无对应的 `comment_count` 递减逻辑。

---

## 四、做得好的部分

- ✅ **游标分页** — 与视频列表风格一致，`(created_at, id)` 避免重复和跨页丢失
- ✅ **测试覆盖** — `CommentControllerTest` 18 个用例，覆盖正常/异常/权限/日志
- ✅ **输入校验** — `CommentService` 对 content 长度、limit 范围、videoId 边界做了完整校验
- ✅ **前端架构** — `ApiClient` token 持久化、OkHttp 拦截器自动注入、Retrofit 封装清晰
- ✅ **schema.sql** — 新增 `comments` 表定义，索引合理
- ✅ **鉴权路径** — 评论接口正确加入 `requiresAuthentication()` 白名单

---

## 五、合并评估

| 检查项 | 状态 |
|--------|------|
| 编译通过 | ✅ (api-server 97 tests PASS) |
| 测试通过 | ✅ (0 failures) |
| 前端 model 与后端 API 契约一致 | ✅ 已修复 |
| 无计数器漂移风险 | ✅ comment_count 改为实时 COUNT(*) |
| 无 TOCTOU 竞态 | ✅ INSERT 加子查询守卫 |
| 代码风格与项目一致 | ✅ Comment 已改 record |
| 无 `frontend/` 之外的无关变更 | ✅ |
| 无合并冲突 | ✅ rebase 后无冲突 |

---

## 六、总体评价

**所有问题已修复。** 97 个可运行测试全部通过（0 failures, 0 errors）。建议合并。

**结论：建议合并。**

---

## 七、问题汇总（已全部修复）

| 等级 | 文件 | 问题 | 状态 |
|------|------|------|------|
| 🔴 严重 | `ApiModels.kt:118` | `ViewData.success` 应为 `viewed` | ✅ 已修复 |
| 🔴 严重 | `ApiModels.kt:151` | `HealthData` 结构完全错误 | ✅ 已修复 |
| 🔴 严重 | `CommentRepository.java:100` | `comment_count` 计数器漂移 | ✅ 已修复（改为实时 COUNT(*)） |
| 🔴 严重 | `CommentService.java:60` | TOCTOU 竞态 | ✅ 已修复（INSERT 加子查询守卫） |
| 🟡 中等 | `Comment.java` | 应使用 record | ✅ 已修复 |
| 🟡 中等 | `BearerAuthenticationFilter.java` | 合并冲突风险 | ✅ 已解决（rebase 后无冲突） |
| 🟡 中等 | `BearerAuthenticationFilter.java:100` | 死代码 | ✅ 已删除 |
