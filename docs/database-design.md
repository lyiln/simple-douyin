# Database Design

## 1. 设计目标

数据库使用 MySQL。表设计服务于课程 P0 功能：账号系统、视频发布、推荐排序、访问过滤、点赞、我的视频分页、删除权限、请求日志。评论表作为可选 Bonus 保留设计。

通用约定：

| 项 | 约定 |
| --- | --- |
| 主键 | 使用 `BIGINT UNSIGNED AUTO_INCREMENT` |
| 时间 | `DATETIME(3)`，由服务端写入 |
| 删除 | 视频使用软删除 `deleted_at`，便于权限审计和日志追踪 |
| 字符集 | `utf8mb4` |
| 外键 | 设计上标明外键；实现时可根据课程技术栈决定是否启用数据库级外键 |

## 2. users

用户账号表。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 用户 ID |
| `username` | `VARCHAR(64)` | NOT NULL, UNIQUE | 登录名 |
| `password_hash` | `VARCHAR(255)` | NOT NULL | 密码哈希，不存明文 |
| `nickname` | `VARCHAR(64)` | NOT NULL | 展示昵称 |
| `avatar_url` | `VARCHAR(512)` | NULL | 头像 URL |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `active` | `active`、`disabled` |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 更新时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_users` | `id` | PRIMARY | 主键 |
| `uk_users_username` | `username` | UNIQUE | 注册去重、登录查询 |

## 3. videos

视频表。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 视频 ID |
| `author_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 作者用户 ID |
| `caption` | `VARCHAR(200)` | NOT NULL | 视频标题/描述 |
| `video_url` | `VARCHAR(512)` | NOT NULL | 视频播放 URL |
| `cover_url` | `VARCHAR(512)` | NULL | 封面 URL |
| `duration_ms` | `INT UNSIGNED` | NULL | 视频时长 |
| `like_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 点赞数，推荐排序关键字段 |
| `view_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 浏览数 |
| `comment_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 评论数，Bonus |
| `visibility` | `VARCHAR(20)` | NOT NULL DEFAULT `public` | `public`、`private` |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `published` | `processing`、`published`、`failed` |
| `created_at` | `DATETIME(3)` | NOT NULL | 发布时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 更新时间 |
| `deleted_at` | `DATETIME(3)` | NULL | 软删除时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_videos` | `id` | PRIMARY | 主键 |
| `idx_videos_author_created` | `author_id, created_at, id` | NORMAL | 我的视频分页 |
| `idx_videos_recommend` | `status, visibility, deleted_at, like_count, created_at, id` | NORMAL | 推荐服务按点赞数排序 |
| `idx_videos_deleted` | `deleted_at` | NORMAL | 过滤已删除视频 |

## 4. video_likes

视频点赞关系表。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 关系 ID |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 点赞用户 |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `videos.id` | 被点赞视频 |
| `created_at` | `DATETIME(3)` | NOT NULL | 点赞时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_video_likes` | `id` | PRIMARY | 主键 |
| `uk_video_likes_user_video` | `user_id, video_id` | UNIQUE | 点赞幂等 |
| `idx_video_likes_video` | `video_id` | NORMAL | 统计某视频点赞 |

计数规则：

| 操作 | 规则 |
| --- | --- |
| 点赞 | 插入成功时 `videos.like_count + 1`；唯一冲突则不加 |
| 取消点赞 | 删除成功时 `videos.like_count - 1`；不存在则不减 |

## 5. video_views

视频访问记录表，用于“访问过不再推荐”。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 访问记录 ID |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 访问用户 |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `videos.id` | 已访问视频 |
| `source` | `VARCHAR(32)` | NOT NULL DEFAULT `recommended_feed` | 来源 |
| `watch_duration_ms` | `INT UNSIGNED` | NULL | 观看时长 |
| `created_at` | `DATETIME(3)` | NOT NULL | 首次访问时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 最近访问时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_video_views` | `id` | PRIMARY | 主键 |
| `uk_video_views_user_video` | `user_id, video_id` | UNIQUE | 访问记录幂等、推荐过滤 |
| `idx_video_views_user_created` | `user_id, created_at` | NORMAL | 用户浏览历史 |
| `idx_video_views_video` | `video_id` | NORMAL | 浏览量分析 |

## 6. upload_objects

媒体上传凭证和对象记录表。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 上传对象 ID |
| `owner_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 上传者 |
| `media_type` | `VARCHAR(20)` | NOT NULL | `video`、`cover` |
| `original_name` | `VARCHAR(255)` | NOT NULL | 原始文件名 |
| `content_type` | `VARCHAR(100)` | NOT NULL | MIME 类型 |
| `file_size` | `BIGINT UNSIGNED` | NOT NULL | 文件大小 |
| `object_key` | `VARCHAR(255)` | NOT NULL, UNIQUE | 存储 key |
| `public_url` | `VARCHAR(512)` | NOT NULL | CDN 或静态访问 URL |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `pending` | `pending`、`uploaded`、`used`、`expired` |
| `expires_at` | `DATETIME(3)` | NOT NULL | 上传凭证过期时间 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 更新时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_upload_objects` | `id` | PRIMARY | 主键 |
| `uk_upload_objects_key` | `object_key` | UNIQUE | 对象 key 去重 |
| `idx_upload_objects_owner` | `owner_id, created_at` | NORMAL | 查询当前用户上传资源 |

## 7. comments Bonus

评论表不是 P0，但可作为 Bonus。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 评论 ID |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `videos.id` | 视频 ID |
| `author_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 评论作者 |
| `content` | `VARCHAR(300)` | NOT NULL | 评论内容 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `deleted_at` | `DATETIME(3)` | NULL | 软删除时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_comments` | `id` | PRIMARY | 主键 |
| `idx_comments_video_created` | `video_id, created_at, id` | NORMAL | 评论分页 |

## 8. request_logs

请求日志表，用于满足课程“记录每个用户请求的输入、输出信息”和“记录每个接口耗时信息”。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 日志 ID |
| `request_id` | `VARCHAR(64)` | NOT NULL, UNIQUE | 请求 ID，响应体返回同值 |
| `user_id` | `BIGINT UNSIGNED` | NULL | 未登录请求为空 |
| `method` | `VARCHAR(10)` | NOT NULL | HTTP 方法 |
| `path` | `VARCHAR(255)` | NOT NULL | 接口路径 |
| `query_string` | `VARCHAR(1024)` | NULL | 查询参数 |
| `request_body` | `TEXT` | NULL | 请求体，敏感字段脱敏 |
| `response_body` | `MEDIUMTEXT` | NULL | 响应体，可限制长度 |
| `status_code` | `INT` | NOT NULL | HTTP 状态码 |
| `business_code` | `INT` | NULL | 业务码 |
| `duration_ms` | `INT UNSIGNED` | NOT NULL | 接口耗时 |
| `client_ip` | `VARCHAR(64)` | NULL | 客户端 IP |
| `user_agent` | `VARCHAR(512)` | NULL | UA |
| `error_message` | `VARCHAR(1024)` | NULL | 异常摘要 |
| `created_at` | `DATETIME(3)` | NOT NULL | 日志时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_request_logs` | `id` | PRIMARY | 主键 |
| `uk_request_logs_request_id` | `request_id` | UNIQUE | requestId 查询 |
| `idx_request_logs_user_time` | `user_id, created_at` | NORMAL | 用户请求审计 |
| `idx_request_logs_path_time` | `path, created_at` | NORMAL | 接口耗时和错误分析 |
| `idx_request_logs_created` | `created_at` | NORMAL | 日志清理和查询 |

## 9. auth_tokens 可选

如果使用纯短期 JWT，服务端可不保存 access token。但为了支持退出后服务端失效 token，建议保留 token 表或 refresh token 表。

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | token ID |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK -> `users.id` | 用户 |
| `token_hash` | `VARCHAR(255)` | NOT NULL, UNIQUE | token 哈希 |
| `expires_at` | `DATETIME(3)` | NOT NULL | 过期时间 |
| `revoked_at` | `DATETIME(3)` | NULL | 退出登录失效时间 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |

## 10. 核心查询规则

| 场景 | 查询规则 |
| --- | --- |
| 推荐视频 | `videos.status='published'`、`visibility='public'`、`deleted_at IS NULL`，排除当前用户 `video_views`，按 `like_count DESC, created_at DESC, id DESC` |
| 我的视频分页 | `author_id = current_user.id`、`deleted_at IS NULL`，按 `created_at DESC, id DESC` |
| 删除视频权限 | 查询视频 `author_id`，必须等于当前用户 ID |
| 点赞状态 | 查询 `video_likes` 是否存在 `(current_user.id, video_id)` |
| 访问过滤 | 查询 `video_views` 是否存在 `(current_user.id, video_id)` |
