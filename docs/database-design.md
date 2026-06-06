# Database Design

## 1. 设计目标

数据库使用 MySQL 8。表设计服务于课程 Core P0 功能：账号系统、视频发布、推荐排序、访问过滤、点赞、我的视频分页、删除权限、请求日志。`comments` 是 P0-lite / 低优先级必备表，先写入 schema 规划，业务实现排在核心 P0 后。

数据库初始化先提供 `sql/schema.sql`。如果后续 Spring Boot Maven 项目结构自然适合 Flyway，可以同时补 Flyway migration，但不要为了 Flyway 增加复杂度。

通用约定：

| 项 | 约定 |
| --- | --- |
| 主键 | 使用 `BIGINT UNSIGNED AUTO_INCREMENT` |
| 时间 | `DATETIME(3)`，由服务端写入 |
| 删除 | 视频使用软删除 `deleted_at` |
| 字符集 | `utf8mb4` |
| 外键 | 文档标明逻辑外键；实际实现优先使用索引和代码校验 |
| 视频存储 | `videos.video_url` 指向本地 `uploads/` 目录下可访问路径 |

## 2. P0 表清单

| 表 | 优先级 | 用途 |
| --- | --- | --- |
| `users` | Core P0 | 账号、密码哈希、用户资料 |
| `videos` | Core P0 | 视频信息、作者、点赞数、状态、软删除 |
| `video_likes` | Core P0 | 点赞关系和幂等 |
| `video_views` | Core P0 | 访问记录和推荐过滤 |
| `request_logs` | Core P0 | 请求输入、输出、耗时 |
| `comments` | P0-lite | 评论列表和发表评论 |

当前不做：

| 表 | 原因 |
| --- | --- |
| `auth_tokens` | 退出登录简化为客户端删除 token |
| `upload_objects` | P0 使用本地 `uploads/`，不做上传凭证和预签名 URL |

## 3. users

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 用户 ID |
| `username` | `VARCHAR(64)` | NOT NULL, UNIQUE | 登录名 |
| `password_hash` | `VARCHAR(255)` | NOT NULL | 密码哈希 |
| `nickname` | `VARCHAR(64)` | NOT NULL | 展示昵称 |
| `avatar_url` | `VARCHAR(512)` | NULL | 头像 URL |
| `status` | `VARCHAR(20)` | NOT NULL DEFAULT `active` | `active`、`disabled` |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `updated_at` | `DATETIME(3)` | NOT NULL | 更新时间 |

索引：

| 名称 | 字段 | 类型 |
| --- | --- | --- |
| `pk_users` | `id` | PRIMARY |
| `uk_users_username` | `username` | UNIQUE |

## 4. videos

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 视频 ID |
| `author_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `users.id` | 作者用户 ID |
| `caption` | `VARCHAR(200)` | NOT NULL | 视频标题/描述 |
| `video_url` | `VARCHAR(512)` | NOT NULL | 本地 `uploads/` 可访问路径或开发期 URL |
| `cover_url` | `VARCHAR(512)` | NULL | 封面路径 |
| `duration_ms` | `INT UNSIGNED` | NULL | 视频时长 |
| `like_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 点赞数，推荐排序关键字段 |
| `view_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 浏览数 |
| `comment_count` | `INT UNSIGNED` | NOT NULL DEFAULT 0 | 评论数 |
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
| `idx_videos_recommend` | `status, visibility, deleted_at, like_count, created_at, id` | NORMAL | 推荐服务排序 |
| `idx_videos_deleted` | `deleted_at` | NORMAL | 过滤已删除视频 |

## 5. video_likes

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 关系 ID |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `users.id` | 点赞用户 |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `videos.id` | 被点赞视频 |
| `created_at` | `DATETIME(3)` | NOT NULL | 点赞时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_video_likes` | `id` | PRIMARY | 主键 |
| `uk_video_likes_user_video` | `user_id, video_id` | UNIQUE | 点赞幂等 |
| `idx_video_likes_video` | `video_id` | NORMAL | 视频点赞查询 |

## 6. video_views

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 访问记录 ID |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `users.id` | 访问用户 |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `videos.id` | 已访问视频 |
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
| `idx_video_views_video` | `video_id` | NORMAL | 视频浏览分析 |

## 7. request_logs

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 日志 ID |
| `request_id` | `VARCHAR(64)` | NOT NULL, UNIQUE | 请求 ID |
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

## 8. comments P0-lite

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | PK | 评论 ID |
| `video_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `videos.id` | 视频 ID |
| `author_id` | `BIGINT UNSIGNED` | NOT NULL, logical FK -> `users.id` | 评论作者 |
| `content` | `VARCHAR(300)` | NOT NULL | 评论内容 |
| `created_at` | `DATETIME(3)` | NOT NULL | 创建时间 |
| `deleted_at` | `DATETIME(3)` | NULL | 软删除时间 |

索引：

| 名称 | 字段 | 类型 | 用途 |
| --- | --- | --- | --- |
| `pk_comments` | `id` | PRIMARY | 主键 |
| `idx_comments_video_created` | `video_id, created_at, id` | NORMAL | 评论分页 |
| `idx_comments_author_created` | `author_id, created_at` | NORMAL | 用户评论查询 |

## 9. 核心查询规则

| 场景 | 查询规则 |
| --- | --- |
| 推荐视频 | `videos.status='published'`、`visibility='public'`、`deleted_at IS NULL`，排除当前用户 `video_views`，按 `like_count DESC, created_at DESC, id DESC` |
| 我的视频分页 | `author_id = current_user.id`、`deleted_at IS NULL`，按 `created_at DESC, id DESC` |
| 删除视频权限 | 查询视频 `author_id`，必须等于当前用户 ID；重复删除返回 200 |
| 点赞状态 | 查询 `video_likes` 是否存在 `(current_user.id, video_id)` |
| 访问过滤 | 查询 `video_views` 是否存在 `(current_user.id, video_id)` |
| 评论分页 | 查询 `comments.video_id = video_id` 且 `deleted_at IS NULL`，按 `created_at DESC, id DESC` |
