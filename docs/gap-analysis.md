# 简易版抖音课程大作业 Gap Analysis

## 1. 分析范围与结论摘要

本分析基于以下材料：

| 材料 | 路径 | 结论可信度 |
| --- | --- | --- |
| REST API 草稿 | `frontend/docs/API_DESIGN.md` | 来自文档，可直接确认接口覆盖范围 |
| Android 前端源码 | `frontend/app/src/main/java/com/example/douyin/*`、`frontend/app/src/main/AndroidManifest.xml` | 来自源码，可确认 Demo 页面和本地交互 |
| APK 包 | `frontend/app-release.apk(1).1` | 已通过 `unzip -l` 确认包含 dex、资源、mp4、本地图片等；未运行真机交互，只作为源码结论的侧面印证 |
| APK 反编译材料说明 | `frontend/source_only/README.md`、`frontend/assets/README.md` | README 存在，但当前仓库未包含实际反编译目录内容 |

总体结论：

| 维度 | 当前状态 | 需要补充 |
| --- | --- | --- |
| 前端 Demo | 已有 Compose 本地 Demo，包含首页上下滑、点赞、收藏、关注、评论、分享、发布、消息、个人页、搜索 | 缺登录/注册/退出、真实后端接入、我的视频分页、删除自己的视频、访问记录过滤 |
| REST API 草稿 | 已覆盖推荐流、点赞、发布、上传、个人页、作品列表、评论、分享、关注、收藏、消息、搜索等 | 缺账号接口、浏览记录、按点赞数推荐规则、RPC 推荐服务、删除视频权限控制、数据库/日志/监控/安全/测试设计 |
| 后端设计 | 当前仓库未见后端源码、数据库 schema、RPC 服务、日志监控方案 | 需要从零补齐后端核心设计与实现 |
| 课程硬性要求 | 部分前端展示和部分 REST 契约已覆盖 | 后台能力、RPC、权限、日志、测试、文档交付物仍是主要缺口 |

## 2. 当前 API_DESIGN.md 已覆盖的功能

| 功能 | 已覆盖 / 未覆盖 / 需要补充 | 现有接口或位置 | 说明 |
| --- | --- | --- | --- |
| 推荐视频主页接口 | 已覆盖但需要补充 | `GET /api/v1/feeds/recommended/videos` | 有推荐流列表和分页，但未写明“按点赞数最高推荐”、未写明“访问过不再推荐”、未写明由 RPC 推荐服务提供 |
| 视频上下滑动 | 已覆盖在前端，不属于后端接口重点 | 客户端页面映射中对应推荐流 | API 返回列表即可支持上下滑，但 API 不需要单独表达上一条/下一条 |
| 点赞视频 | 已覆盖 | `PUT /videos/{videoId}/likes/me`、`DELETE /videos/{videoId}/likes/me` | 幂等关系资源设计合理，可保留 |
| 发布视频 | 已覆盖 | `POST /media-upload-tokens`、`POST /videos` | 覆盖上传凭证和创建作品，但还要补视频存储落地策略、转码状态、上传完成校验 |
| 查看我的视频分页 | 部分覆盖 | `GET /users/{userId}/videos?cursor=&limit=18` | 有用户作品分页，但课程要求是“我的视频”；建议新增或明确 `GET /me/videos`，避免前端传任意 userId 造成权限语义不清 |
| 删除我的视频 | 未覆盖 | 无 | 需要新增 `DELETE /videos/{videoId}` 或 `DELETE /me/videos/{videoId}`，并要求只能删除自己的视频 |
| 登录 | 未覆盖 | 文档明确写“本版不定义登录注册接口” | 必须新增 |
| 注册 | 未覆盖 | 无 | 必须新增 |
| 退出 / 取消登录 | 未覆盖 | 无 | 必须新增 token 失效或客户端登出约定 |
| 认证 token | 部分覆盖 | 统一 `Authorization: Bearer <token>` | 只有占位说明，没有账号系统、token 签发、刷新、失效、权限模型 |
| 数据库设计 | 未覆盖 | 无 | 必须补表结构、索引、约束、核心查询 |
| 视频存储设计 | 部分覆盖 | `UploadToken`、`POST /media-upload-tokens` | 有对象存储直传思路，但缺 bucket/key 规范、访问 URL、转码/封面、文件大小和类型校验、清理策略 |
| 日志与集成监控 | 未覆盖 | 无 | 必须补输入、输出、耗时、requestId、用户 ID、错误码、监控指标 |
| 安全校验与权限控制 | 部分覆盖 | HTTP 401/403、错误码、Bearer Token 占位 | 缺删除视频只能本人、上传凭证归属、接口鉴权中间件、密码安全、限流策略 |
| RPC 推荐服务 | 未覆盖 | 无 | 课程明确要求“主端访问推荐系统使用 RPC”，当前只有 REST 推荐流 |
| 测试方案 | 部分覆盖 | API_DESIGN.md 第 6 节“联调测试场景” | 有联调场景列表，但缺正式测试文档、单元/集成/接口/权限/推荐规则测试矩阵 |

## 3. APK / 前端 Demo 可能已经实现的页面和交互

源码和 APK 内容显示当前是离线 Android Compose Demo。`AndroidManifest.xml` 未声明 `INTERNET` 权限，`build.gradle.kts` 依赖只有 Compose、Lifecycle、Media3，未见 Retrofit、OkHttp、Room、DataStore 等网络或持久化依赖。

| 页面 / 交互 | 已覆盖 / 未覆盖 / 需要补充 | 源码依据 | 说明 |
| --- | --- | --- | --- |
| 启动页 | 已覆盖 | `MainActivity.kt` | 黑底“抖”字启动态，延迟后进入应用 |
| 推荐首页 | 已覆盖 | `DouyinApp.kt` 的 `HomeFeed`、`VideoPage` | 使用 `VerticalPager` 上下滑动本地视频 |
| 本地视频播放 | 已覆盖 | `VideoPlayer.kt` | 使用 Media3 `ExoPlayer` 播放 raw 资源，循环播放 |
| 点赞 | 已覆盖但仅本地 | `liked` map、`ActionRail` | 点击后 UI 计数 +1，没有后端持久化 |
| 收藏 | 已覆盖但属于可选 | `collected` map | 课程未要求收藏，可降级 |
| 关注 | 已覆盖但属于可选 | `following` map、朋友页 | 课程未要求关注，可降级 |
| 评论列表 / 发送评论 | 已覆盖但属于可选 | `CommentSheet`、`MockRepository.commentsFor` | 课程未要求评论，可降级；当前只写内存 |
| 分享弹层 | 已覆盖但属于可选 | `ShareSheet` | 课程未要求分享，可降级；当前只 toast |
| 发布作品 | 已覆盖但仅本地 | `PublishScreen` | 只能选择内置素材，发布后 `posts.add(0, newPost)` 插入本地推荐流 |
| 我的主页 / 作品宫格 | 部分覆盖 | `ProfileScreen` | 展示静态“我”和作品宫格，但不是分页，也没有只看我发布的视频过滤 |
| 删除我的视频 | 未覆盖 | 未发现删除按钮或删除逻辑 | 必须补 |
| 登录 / 注册 / 退出 | 未覆盖 | `AppScreen` 无账号页；源码无 auth 逻辑 | 必须补 |
| 搜索 | 已覆盖但属于可选 | `SearchScreen` | 本地按 caption/author/topic 过滤，课程未要求 |
| 消息页 | 已覆盖但属于可选 | `MessagesScreen`、`ChatPreview` | 本地 mock，会话详情未实现，课程未要求 |
| 推荐规则：按点赞数最高 | 部分覆盖但未形成规则 | `MockRepository.initialPosts()` 初始 3 条点赞数从高到低 | 只是 mock 顺序；发布 0 赞视频会插入顶部，不能证明后端按点赞排序 |
| 访问过不再推荐 | 未覆盖 | 未发现浏览历史状态或接口 | 必须补 |

## 4. 当前材料与课程大作业要求的差距

| 课程硬性要求 | 当前已覆盖 | 当前缺口 | 需要补充 |
| --- | --- | --- | --- |
| 推荐视频：按照点赞数最高推荐 | API 有推荐流；mock 初始顺序像按点赞数 | 无明确排序规则，无后端查询/推荐逻辑 | 推荐服务按 `like_count desc, created_at desc` 返回；写入 API 和 RPC 契约 |
| 用户访问过的视频不再推荐 | 未覆盖 | 无浏览记录表、无访问记录接口、无过滤逻辑 | 新增 `video_views` 表；推荐 RPC 排除当前用户已浏览视频；播放曝光时记录 |
| 视频上下滑动 | 前端已覆盖 | 后端无问题 | 保留推荐流分页即可 |
| 视频点赞 | API 和前端均覆盖 | 缺后端表、唯一索引、事务更新计数 | 实现 `video_likes`、`videos.like_count`，接口幂等 |
| 发布视频 | API 和前端均覆盖 | 缺真实上传、存储、鉴权、数据库 | 实现上传凭证、视频记录、对象存储、状态流转 |
| 查看我的视频，必须支持分页 | API 部分覆盖用户作品分页 | 前端无分页；API 未明确 `/me/videos` | 新增或明确 `GET /me/videos?cursor&limit` |
| 删除我的视频，只能删除自己的视频 | 未覆盖 | 无接口、无权限设计 | 新增删除接口，校验 `video.author_id == current_user.id` |
| 登录 | 未覆盖 | 无接口、无页面、无 token 策略 | 新增 `POST /auth/login` |
| 注册 | 未覆盖 | 无接口、无页面、无密码存储方案 | 新增 `POST /auth/register`，密码哈希 |
| 退出 / 取消登录 | 未覆盖 | 无 token 失效或客户端清理说明 | 新增 `POST /auth/logout` 或约定客户端删除 token；服务端可维护 token 黑名单/refresh token |
| 数据库设计 | 未覆盖 | 无 schema | 补 users、videos、video_likes、video_views、upload_objects、request_logs 等 |
| 视频存储设计 | 部分覆盖 | 只有上传凭证草案 | 补对象存储 key、访问 URL、类型大小限制、上传状态、清理策略 |
| 日志记录每个用户请求输入、输出 | 未覆盖 | 只有 `requestId` 响应字段 | 增加统一日志中间件，记录 userId、path、method、request、response、status |
| 记录每个接口耗时 | 未覆盖 | 无监控方案 | 日志和 metrics 中记录 `durationMs`，按接口聚合 |
| 安全校验：账号系统、权限控制 | 部分覆盖 | 只有 401/403 错误码 | 实现鉴权中间件、密码哈希、权限校验、资源归属检查、限流 |
| 主端访问推荐系统使用 RPC | 未覆盖 | REST 直接暴露推荐流，无 RPC 服务 | 主后端通过 RPC 调用推荐服务；客户端仍可调用 REST 网关 |
| README 部署步骤 | 未覆盖 | 根目录未见 README.md | 最终补 README |
| 测试文档 | 部分覆盖 | 只有联调场景，不是完整测试文档 | 补单元、集成、接口、权限、推荐规则、日志验证 |
| 交付文档 / PPT / 分工 / 评分 / 演示视频 | 未覆盖 | 当前未见这些材料 | 后续按课程提交清单补齐 |

## 5. 必须完成 vs Bonus / 可选功能

### 必须完成

| 模块 | 必须功能 | 当前状态 | 优先级 |
| --- | --- | --- | --- |
| 账号系统 | 注册、登录、退出、token 鉴权 | 未覆盖 | P0 |
| 推荐系统 | 按点赞数最高推荐、过滤访问过的视频、主端通过 RPC 访问推荐服务 | 未覆盖核心规则 | P0 |
| 推荐主页 | 推荐流列表、上下滑动、点赞 | 前端有 Demo；后端待实现 | P0 |
| 我的视频管理 | 发布、分页查看我的视频、删除自己的视频、权限控制 | 发布接口有草案；分页/删除/权限缺失 | P0 |
| 后台基础 | 数据库设计、视频存储设计、安全校验 | 大多缺失 | P0 |
| 日志监控 | 记录每个用户请求输入、输出、耗时 | 未覆盖 | P0 |
| 测试文档 | 核心接口、推荐规则、权限、日志测试 | 部分联调场景 | P1 |
| README / 交付材料 | 部署步骤、需求、设计、测试、PPT、分工、评分、演示视频 | 未覆盖 | P1 |

### Bonus 或可选

| 功能 | 当前接口 / 页面 | 建议 |
| --- | --- | --- |
| 收藏 | `PUT/DELETE /videos/{videoId}/favorites/me`、前端收藏按钮 | 降级为可选，先不影响课程核心验收 |
| 关注 / 朋友页 | `/me/following/*`、`FriendsScreen` | 降级为可选，除非团队想展示更完整 Demo |
| 评论 | `/videos/{videoId}/comments`、评论弹层 | 可选；课程未强制 |
| 分享 | `POST /videos/{videoId}/shares`、分享弹层 | 可选；课程未强制 |
| 消息 | `GET /me/message-overview`、消息页 | 可选；课程未强制 |
| 搜索 | `GET /videos?q=`、搜索页 | 可选；课程未强制 |
| 他人主页 / 公开资料 | `GET /users/{userId}` | 可选；核心只需要当前用户和我的视频 |
| 私信、举报、资料编辑 | API_DESIGN.md 已明确未覆盖 | 可不做，避免偏离主线 |

## 6. 当前 API_DESIGN.md 中可以保留的接口

| 接口 | 保留级别 | 调整建议 |
| --- | --- | --- |
| `GET /feeds/recommended/videos` | 必须保留 | 明确排序为 `like_count desc`，排除当前用户已访问视频；说明服务端内部调用推荐 RPC |
| `PUT /videos/{videoId}/likes/me` | 必须保留 | 补数据库唯一索引、幂等、计数更新事务 |
| `DELETE /videos/{videoId}/likes/me` | 必须保留 | 同上 |
| `POST /media-upload-tokens` | 必须保留 | 作为视频存储设计入口；补归属校验和上传完成校验 |
| `POST /videos` | 必须保留 | 用于发布视频；补作者取当前登录用户，不允许客户端传作者 |
| `GET /users/{userId}/videos` | 可以保留 | 用于公开作品列表；我的视频建议另增 `GET /me/videos` |
| `GET /me` | 可以保留 | 账号体系完成后用于个人页 |
| 统一响应、错误码、分页约定 | 可以保留 | 加入日志字段、认证失败、权限失败的统一处理约定 |

## 7. 应降级为可选的接口

| 接口 | 原因 | 建议 |
| --- | --- | --- |
| `PUT/DELETE /me/following/users/{userId}` | 课程未要求关注系统 | 可放二期或演示增强 |
| `GET /me/following/videos`、`GET /me/following/users` | 朋友页不是硬性要求 | 可放二期 |
| `PUT/DELETE /videos/{videoId}/favorites/me` | 收藏不是硬性要求 | 可放二期 |
| `GET/POST /videos/{videoId}/comments` | 评论不是硬性要求 | 可选展示 |
| `POST /videos/{videoId}/shares` | 分享不是硬性要求 | 可选展示 |
| `GET /me/message-overview` | 消息不是硬性要求 | 可放弃或仅前端 mock |
| `GET /videos?q=` | 搜索不是硬性要求 | 可放二期 |
| `GET /users/{userId}` | 他人主页不是硬性要求 | 非核心 |

## 8. 缺失的核心接口清单

| 接口 | 方法 | 必须性 | 说明 |
| --- | --- | --- | --- |
| `/api/v1/auth/register` | `POST` | 必须 | 注册账号，参数至少包括用户名/手机号/邮箱之一、密码、昵称 |
| `/api/v1/auth/login` | `POST` | 必须 | 登录并返回 access token，可选 refresh token |
| `/api/v1/auth/logout` | `POST` | 必须 | 退出登录；最小实现可约定客户端删除 token，较完整实现服务端失效 refresh token |
| `/api/v1/me/videos` | `GET` | 必须 | 当前用户自己的视频分页列表，`cursor` + `limit` |
| `/api/v1/videos/{videoId}` 或 `/api/v1/me/videos/{videoId}` | `DELETE` | 必须 | 删除视频；后端校验作者必须是当前用户 |
| `/api/v1/videos/{videoId}/views/me` | `POST` | 必须 | 记录当前用户看过该视频；也可由推荐流曝光/播放完成事件触发 |
| `/api/v1/internal/recommendations` | RPC 而非 REST | 必须 | 主后端调用推荐服务，入参 userId、limit、cursor，出参 videoId 列表和 nextCursor |
| `/api/v1/health` 或 `/actuator/health` | `GET` | 建议必须 | 集成监控/部署验收用 |
| `/api/v1/metrics` 或监控采集端点 | `GET` | 建议必须 | 暴露接口耗时、错误率等指标；具体取决于技术栈 |

## 9. 缺失的数据库设计

建议至少补以下表和关键约束：

| 表 | 必须字段 | 关键索引 / 约束 | 用途 |
| --- | --- | --- | --- |
| `users` | `id`、`username`、`password_hash`、`nickname`、`avatar_url`、`created_at`、`updated_at` | `username` 唯一 | 注册、登录、作者信息 |
| `videos` | `id`、`author_id`、`caption`、`video_url`、`cover_url`、`like_count`、`view_count`、`status`、`visibility`、`created_at`、`deleted_at` | `(author_id, created_at)`、`(like_count, created_at)` | 视频发布、推荐排序、我的视频 |
| `video_likes` | `user_id`、`video_id`、`created_at` | `(user_id, video_id)` 唯一、`video_id` 索引 | 点赞关系和幂等 |
| `video_views` | `user_id`、`video_id`、`created_at`、`source` | `(user_id, video_id)` 唯一、`user_id` 索引 | “访问过不再推荐” |
| `upload_objects` | `id`、`owner_id`、`media_type`、`object_key`、`public_url`、`status`、`expires_at`、`created_at` | `owner_id` 索引 | 视频/封面上传凭证和归属校验 |
| `auth_tokens` 或 `refresh_tokens` | `id`、`user_id`、`token_hash`、`expires_at`、`revoked_at` | `user_id` 索引 | 退出登录、刷新 token，可按实现取舍 |
| `request_logs` | `request_id`、`user_id`、`method`、`path`、`request_body`、`response_body`、`status_code`、`duration_ms`、`created_at` | `created_at`、`user_id`、`path` 索引 | 满足日志记录输入、输出、耗时 |

推荐 SQL 查询核心规则：

| 场景 | 查询规则 |
| --- | --- |
| 推荐视频 | 从 `videos` 取 `status='published'`、`visibility='public'`、`deleted_at is null`，排除 `video_views` 中当前用户已看过的视频，按 `like_count desc, created_at desc` 排序 |
| 我的作品分页 | `where author_id = current_user.id and deleted_at is null order by created_at desc limit n` |
| 删除视频 | 先查 `author_id`，不是当前用户则返回 403；建议软删除 `deleted_at` |
| 点赞 | `video_likes` 唯一索引保证幂等；新增/删除关系时同步更新 `videos.like_count` |

## 10. 缺失的 RPC 推荐服务设计

课程要求“主端访问推荐系统使用 RPC”。建议架构如下：

| 组件 | 职责 | 说明 |
| --- | --- | --- |
| Android 客户端 | 调用 REST 网关 | 不直接调用 RPC |
| 主后端 / API 网关 | 处理鉴权、日志、HTTP 响应；调用推荐 RPC | `GET /feeds/recommended/videos` 内部转 RPC |
| 推荐服务 | 根据用户、浏览记录、点赞数返回推荐视频 ID | 独立服务，RPC 协议可用 gRPC、Dubbo、Thrift 或课程指定框架 |
| 数据库 | 存 users、videos、likes、views | 推荐服务可直连只读库或通过 repository 访问 |

推荐 RPC 契约建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userId` | string / long | 当前用户 ID |
| `cursor` | string | 推荐分页游标 |
| `limit` | int | 返回数量 |
| `excludeViewed` | boolean | 默认为 true |
| `sortBy` | enum | 当前课程固定 `LIKE_COUNT_DESC` |

返回：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `videoIds` | list | 推荐结果 ID，主后端再批量查视频详情 |
| `nextCursor` | string | 下一页游标 |
| `hasMore` | boolean | 是否还有更多 |
| `strategy` | string | 例如 `like_count_desc_exclude_viewed`，便于日志和答辩说明 |

必须写清楚：

| 缺口 | 补充内容 |
| --- | --- |
| 排序规则 | `like_count desc`，同点赞数按 `created_at desc` |
| 浏览过滤 | 排除 `video_views` 中当前用户已访问视频 |
| 访问记录写入时机 | 推荐页视频开始播放、滑过曝光、或播放超过阈值时写入；课程最小实现可在客户端切到某视频时调用记录接口 |
| 无可推荐视频 | 返回空列表，前端展示“暂无更多推荐” |
| RPC 失败 | 主后端记录错误日志；可返回 500 或降级为空列表，需在设计中明确 |

## 11. 缺失的日志、安全、权限控制设计

| 领域 | 当前覆盖 | 缺口 | 建议补充 |
| --- | --- | --- | --- |
| 请求日志 | 只有响应体 `requestId` 字段 | 未定义日志格式和采集点 | 全局中间件记录 `requestId`、`userId`、method、path、query、body、status、response、durationMs、error |
| 输入输出记录 | 未覆盖 | 课程明确要求每个用户请求输入、输出 | 对敏感字段脱敏，如密码、token、上传签名 |
| 接口耗时 | 未覆盖 | 课程明确要求记录每个接口耗时 | 中间件计算开始/结束时间，写日志并上报 metrics |
| 集成监控 | 未覆盖 | 无健康检查、指标、告警 | 补 health endpoint、接口 QPS、P95/P99、错误率、数据库耗时 |
| 账号安全 | 未覆盖 | 无密码哈希、token 设计 | 密码使用 bcrypt/argon2；JWT 或 session token；token 过期 |
| 权限控制 | 部分错误码 | 无具体规则 | 登录态接口统一鉴权；删除视频、上传完成、查看我的视频必须绑定当前用户 |
| 删除视频权限 | 未覆盖 | 高风险缺口 | `video.author_id != current_user.id` 返回 403，不允许客户端传 userId 绕过 |
| 限流 | 只列 429 错误码 | 无策略 | 登录、注册、评论、上传、点赞可做基础限流 |

## 12. 后续开发优先级

| 优先级 | 模块 | 目标 | 建议产出 |
| --- | --- | --- | --- |
| P0 | 需求收敛 | 把课程硬性要求从 API_DESIGN.md 中独立出来 | 更新需求文档，标明必做/可选 |
| P0 | 数据库 schema | 先定 users、videos、likes、views、uploads、logs | 技术设计文档 + migration |
| P0 | 账号系统 | 注册、登录、退出、鉴权中间件 | Auth API + 安全设计 |
| P0 | 视频发布与我的视频 | 上传凭证、发布、分页查看、删除权限 | REST API + 权限测试 |
| P0 | 推荐 RPC | REST 网关调用推荐 RPC，按点赞排序并排除已访问 | RPC proto/interface + 推荐服务实现 |
| P0 | 日志监控 | 每个请求输入、输出、耗时 | 中间件 + 日志样例 + 监控截图 |
| P1 | 前端联调 | 替换 `MockRepository` 为真实 API 数据源 | 网络层、登录页、我的视频分页/删除 |
| P1 | 测试方案 | 覆盖核心接口、推荐规则、权限、日志 | 测试文档、接口测试用例 |
| P1 | README 和交付物 | 部署步骤、演示视频、PPT、团队材料 | README.md、答辩 PPT、团队分工和评分表 |
| P2 | 可选增强 | 收藏、关注、评论、分享、消息、搜索 | 视时间选择实现，不阻塞主线 |

## 13. 下一步模块规划建议

| 阶段 | 后端模块 | 前端模块 | 文档 / 测试 |
| --- | --- | --- | --- |
| 第 1 阶段 | 建库、账号、鉴权、统一响应、日志中间件 | 增加登录/注册/退出入口 | 需求文档、数据库设计、接口鉴权测试 |
| 第 2 阶段 | 视频上传、发布、我的视频分页、删除权限 | 发布页接真实上传；个人页只展示我的视频并分页；增加删除 | 视频存储设计、权限测试 |
| 第 3 阶段 | 推荐 RPC、浏览记录、按点赞数排序推荐 | 推荐页拉取真实推荐流；切换视频时记录浏览 | RPC 设计、推荐规则测试 |
| 第 4 阶段 | 点赞幂等、计数一致性、异常处理 | 点赞接真实接口，刷新 viewerState | 接口测试、并发/幂等测试 |
| 第 5 阶段 | 监控指标、部署脚本、README | 联调修正、演示稳定性 | 测试文档、答辩 PPT、演示视频 |

最小可交付闭环建议：先完成账号登录 -> 发布视频 -> 我的公开视频分页 -> 删除自己的视频 -> 推荐页按点赞排序并过滤已访问 -> 点赞更新排序基础数据 -> 日志记录输入/输出/耗时。收藏、关注、评论、分享、消息、搜索不要抢 P0 时间。
