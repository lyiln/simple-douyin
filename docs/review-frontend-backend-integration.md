# 前后端联调验收结论

**结论：CONDITIONAL PASS**

---

## 1. 环境与启动结果

| 项目 | 结果 |
|------|------|
| MySQL 8 | 可用，schema 初始化成功 |
| 后端 API Server | 可启动（需 `-Dspring-boot.repackage.skip=true` workaround），端口 8080 |
| 后端 gRPC Recommend Service | 未启动，推荐流返回 500 |
| 前端 Android App | 无法构建 — 缺少 Android SDK |
| 后端启动前置条件 | `mvn install -DskipTests -pl backend/recommend-service -Dspring-boot.repackage.skip=true && mvn install -DskipTests -pl backend/api-server` |

---

## 2. 前端 API 到后端接口映射

### 评论接口（核心验证目标）

| 前端调用 | URL | Method | 后端 Controller | 参数匹配 | 返回字段匹配 | Auth |
|----------|-----|--------|-----------------|---------|------------|------|
| `ApiRepository.getComments()` | `GET api/v1/videos/{videoId}/comments` | GET | `CommentController.getComments` | ✅ | ✅ | ✅ |
| `ApiRepository.postComment()` | `POST api/v1/videos/{videoId}/comments` | POST | `CommentController.postComment` | ✅ | ✅ | ✅ |

DTO 字段逐项对比（全部匹配）：

| 后端字段 | 后端 JSON 输出 | 前端字段 | 匹配 |
|----------|---------------|---------|------|
| `CommentResponse.id` | `id` | `CommentResponse.id: Long` | ✅ |
| `CommentResponse.videoId` | `videoId` | `CommentResponse.videoId: Long` | ✅ |
| `CommentResponse.author` | `author{id,username,nickname,avatarUrl}` | `CommentResponse.author: AuthorSummary` | ✅ |
| `CommentResponse.content` | `content` | `CommentResponse.content: String` | ✅ |
| `CommentResponse.createdAt` | `createdAt` | `CommentResponse.createdAt: String` | ✅ |
| `GetCommentsResponse.items` | `items` | `GetCommentsData.items` | ✅ |
| `GetCommentsResponse.nextCursor` | `nextCursor` | `GetCommentsData.nextCursor` | ✅ |
| `GetCommentsResponse.hasMore` | `hasMore` | `GetCommentsData.hasMore` | ✅ |
| `GetCommentsResponse.commentCount` | `commentCount` | `GetCommentsData.commentCount` | ✅ |
| `PostCommentResponse.comment` | `comment` | `PostCommentData.comment` | ✅ |
| `PostCommentResponse.commentCount` | `commentCount` | `PostCommentData.commentCount` | ✅ |

### 其他接口映射

| 前端调用 | URL | 后端存在 | 字段匹配 |
|----------|-----|---------|---------|
| `register` | `POST api/v1/auth/register` | ✅ | ⚠️ 前端缺 `nickname` |
| `login` | `POST api/v1/auth/login` | ✅ | ✅ |
| `logout` | `POST api/v1/auth/logout` | ✅ | ✅ |
| `getMe` | `GET api/v1/me` | ✅ | ✅ |
| `getMyVideos` | `GET api/v1/me/videos` | ✅ | ✅ |
| `publishVideo` | `POST api/v1/videos` | ✅ | ⚠️ 前端 `videoPost` vs 后端 `video` |
| `deleteVideo` | `DELETE api/v1/videos/{videoId}` | ✅ | ✅ |
| `likeVideo` | `PUT api/v1/videos/{videoId}/likes/me` | ✅ | ✅ |
| `unlikeVideo` | `DELETE api/v1/videos/{videoId}/likes/me` | ✅ | ✅ |
| `recordView` | `POST api/v1/videos/{videoId}/views/me` | ✅ | ✅ |
| `getRecommendedVideos` | `GET api/v1/feeds/recommended/videos` | ✅ | ⚠️ 前端 VideoPost model 完全不同 |
| `healthCheck` | `GET api/v1/health` | ✅ | ✅ |

---

## 3. 实际请求验证结果

| 请求 | 状态码 | 响应摘要 | 结论 |
|------|--------|---------|------|
| `GET /api/v1/health` | 200 | apiServer:UP, mysql:UP, recommendService:DOWN | ✅ |
| `POST /api/v1/auth/register` | 200 | user + accessToken | ✅ |
| `POST /api/v1/videos` | 200 | video id=1 | ✅ |
| `GET /api/v1/videos/1/comments` (空) | 200 | items:[], commentCount:0 | ✅ |
| `POST /api/v1/videos/1/comments` | 200 | comment:{id:1,...}, commentCount:1 | ✅ |
| `GET /api/v1/videos/1/comments` (有评论) | 200 | items:[1项], commentCount:1 | ✅ |
| `GET /api/v1/videos/99999/comments` | 404 | code:40401 | ✅ |
| `POST /api/v1/videos/99999/comments` | 404 | code:40401 | ✅ |
| `GET /api/v1/videos/1/comments` (无 token) | 401 | code:40101 | ✅ |
| `POST /api/v1/videos/1/comments` (无 token) | 401 | code:40101 | ✅ |
| `DELETE /api/v1/videos/1` | 200 | deleted:true | ✅ |
| `GET /api/v1/videos/1/comments` (视频已删) | 404 | code:40401 | ✅ |
| `POST /api/v1/videos/1/comments` (视频已删) | 404 | code:40401 | ✅ |
| `GET /api/v1/feeds/recommended/videos` | 500 | gRPC 服务未启动 | ⚠️ 环境问题 |

---

## 4. 评论功能闭环判断

| 环节 | 判断 | 证据 |
|------|------|------|
| 推荐流到评论入口 | **前端未接通** | `DouyinApp.kt:80` 始终用 `MockRepository.initialPosts()`，mock 视频 id 为字符串，`toLongOrNull()` 返回 null，评论入口永远不会调 API |
| 评论列表是否真实请求后端 | **条件性接通** | 仅当 `isLoggedIn()` 且 `post.id.toLongOrNull() != null` 时才调 API，当前永不满足 |
| 发布评论是否真实请求后端 | **条件性接通** | 同上，fallback 到本地 `localComments` |
| 认证 token 是否正确传递 | **机制正确，路径断开** | OkHttp interceptor 正确添加 `Authorization: Bearer`，但无 Login UI |
| 404 场景是否被后端处理 | **后端正确** | curl 验证已删除视频返回 404 |
| 404 场景是否被前端处理 | **未验证** | 前端评论请求不会到达 404 路径 |
| 是否存在 mock 掩盖问题 | **是** | 三层 fallback 使 UI 看起来完整但零网络请求 |

---

## 5. 生产构建风险

| 项目 | 判断 |
|------|------|
| API base URL | 硬编码 `http://10.0.2.2:8080/`（仅 Android emulator 正确） |
| 是否依赖开发代理 | 否 — Retrofit 直连 |
| `CreateVideoData.videoPost` vs 后端 `video` | 字段名不匹配，反序列化失败 |
| `AuthRequest` 缺 `nickname` | 注册接口不匹配 |
| `VideoPost` model 与后端 `VideoPostResponse` | 结构完全不同，无转换层 |

---

## 6. P0/P1/P2 问题

### P0：阻塞提交

**P0-1: 前端推荐流 100% mock，评论入口永远不调 API**

- 文件：`DouyinApp.kt:80`, `DouyinApp.kt:206-221`
- 问题：mock 视频的 `id` 是 `"mountain-night"` 等字符串，`toLongOrNull()` 返回 null，评论 API 永远不会被调用
- 影响：课程演示时若检查 Network 请求，发现评论和推荐流完全前端自闭环
- 最小修复：mock 视频使用数字 id 字符串（如 `"1"`, `"2"`, `"3"`），或在 App 启动时调 `ApiRepository.getRecommendedVideos()` 获取真实数据
- 验证方式：Logcat 过滤 `ApiRepository`，确认有 HTTP 请求

### P1：提交前建议修复

**P1-1: 前端无 Login UI，无法获得 token**

- 文件：`DouyinApp.kt:207`, `ApiClient.kt:49`
- 问题：无登录页面，`isLoggedIn()` 永远为 false，评论 API 被跳过
- 最小修复：添加简单登录 UI 或硬编码测试 token

**P1-2: `CreateVideoData.videoPost` vs 后端 `video` 字段名不匹配**

- 文件：`ApiModels.kt:92` vs `CreateVideoResponse.java:4`
- 问题：Gson 反序列化时 `videoPost` 为 null
- 最小修复：`val videoPost` → `val video` 或加 `@SerializedName("video")`

**P1-3: `AuthRequest` 缺 `nickname` 字段**

- 文件：`ApiModels.kt:12-15`
- 问题：后端要求 `nickname` 必填，前端不发
- 最小修复：给 `AuthRequest` 添加 `nickname` 字段

### P2：可选优化

**P2-1: `spring-boot-maven-plugin` 生成 fat-jar 导致构建顺序问题**

- 文件：`pom.xml`（parent）
- 问题：fat-jar 覆盖普通 jar，`api-server` 无法解析 proto 类
- 建议：添加 `<classifier>exec</classifier>` 保留原始 jar

**P2-2: `VideoPost` model 与后端 `VideoPostResponse` 结构完全不同**

- 文件：`Models.kt` vs `ApiModels.kt`
- 建议：若未来接真实推荐流，需添加映射函数和网络播放支持

---

## 7. 最终建议

1. 评论 API 在协议层与后端完全匹配，后端 curl 验证全部通过
2. 前端 UI 实际不调用后端 — 三重 fallback（mock 数据 + 字符串 id + 无 token）
3. 提交前最应该手动确认：Android emulator 启动后 Logcat 是否有 `ApiRepository` 请求
4. 若评分涉及实际联调展示，P0-1 和 P1-1 必须修复
