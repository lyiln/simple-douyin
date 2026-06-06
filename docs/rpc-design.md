# RPC Design

## 1. 架构边界

固定架构：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

不引入复杂微服务，只保留两个后端运行单元：

| 组件 | 技术 | 职责 |
| --- | --- | --- |
| Spring Boot API Server | Java 17 + Spring Boot + Maven | 鉴权、REST API、统一响应、日志、视频详情查询、发布/点赞/删除/评论等业务接口 |
| gRPC Recommend Service | Java 17 + gRPC + Maven | 根据课程规则生成推荐 videoId 列表 |

Android 客户端只访问 Spring Boot API Server 的 RESTful HTTP/JSON 接口。Spring Boot API Server 在 `GET /api/v1/feeds/recommended/videos` 内部通过 gRPC 调用 Recommend Service。

API Server 和 Recommend Service 都可以访问 MySQL 8。Recommend Service 返回 videoIds，API Server 负责补齐完整视频详情和 viewerState。

## 2. 调用链

```mermaid
sequenceDiagram
    participant Client as Android Frontend
    participant API as Spring Boot API Server
    participant RPC as gRPC Recommend Service
    participant DB as MySQL 8

    Client->>API: RESTful HTTP/JSON GET /api/v1/feeds/recommended/videos
    API->>API: Verify Bearer token
    API->>RPC: RecommendService.ListRecommendedVideos(userId, cursor, limit)
    RPC->>DB: Query published videos excluding video_views
    DB-->>RPC: videoId list ordered by like_count desc
    RPC-->>API: videoIds, nextCursor, hasMore, strategy
    API->>DB: Batch load VideoPost and viewerState
    API-->>Client: Unified JSON response
```

## 3. gRPC Service

Service：`RecommendService`

Method：`ListRecommendedVideos`

### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `requestId` | string | 是 | API Server 传入，用于日志串联 |
| `userId` | int64 | 是 | 当前登录用户 ID |
| `cursor` | string | 否 | 推荐分页游标 |
| `limit` | int32 | 是 | 返回数量，最大 30 |
| `excludeViewed` | bool | 是 | P0 固定为 true |
| `strategy` | string | 否 | P0 固定 `like_count_desc` |

### Response

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `videoIds` | repeated int64 | 是 | 推荐视频 ID 列表 |
| `nextCursor` | string | 否 | 下一页游标 |
| `hasMore` | bool | 是 | 是否还有更多 |
| `strategy` | string | 是 | 固定 `like_count_desc_exclude_viewed` |
| `debugMessage` | string | 否 | 仅开发环境可返回 |

## 4. 推荐规则

P0 推荐规则必须简单、确定、可测试：

| 步骤 | 规则 |
| --- | --- |
| 1 | 只推荐 `videos.status='published'` 的视频 |
| 2 | 只推荐 `videos.visibility='public'` 的视频 |
| 3 | 排除 `videos.deleted_at IS NOT NULL` 的视频 |
| 4 | 排除当前用户在 `video_views` 表中已访问的视频 |
| 5 | 按 `like_count DESC` 排序 |
| 6 | 点赞数相同按 `created_at DESC, id DESC` 排序 |

推荐游标建议编码以下字段：

| 字段 | 用途 |
| --- | --- |
| `lastLikeCount` | 下一页继续按点赞数分页 |
| `lastCreatedAt` | 点赞相同时稳定排序 |
| `lastVideoId` | 避免重复和漏数据 |

## 5. SQL 设计思路

Recommend Service 查询 MySQL 8，返回 ID 列表，不负责拼完整视频详情。

逻辑表达：

| 条件 | 说明 |
| --- | --- |
| `v.status = 'published'` | 只推荐已发布视频 |
| `v.visibility = 'public'` | 私密视频不推荐 |
| `v.deleted_at IS NULL` | 已删除视频不推荐 |
| `NOT EXISTS video_views(user_id, video_id)` | 访问过不再推荐 |
| `ORDER BY v.like_count DESC, v.created_at DESC, v.id DESC` | 课程指定按点赞数最高推荐 |

## 6. API Server 职责

| 职责 | 说明 |
| --- | --- |
| 鉴权 | REST 层先解析 token，得到 `currentUser.id` |
| 参数校验 | 校验 `cursor`、`limit` |
| gRPC 调用 | 调 `RecommendService.ListRecommendedVideos` |
| 详情补全 | 根据 `videoIds` 批量查询 videos、authors、liked/viewed/owner 状态 |
| 顺序保持 | 返回列表顺序必须与 gRPC 返回 `videoIds` 顺序一致 |
| 日志 | 记录 REST 输入/输出/耗时；gRPC 耗时可写入扩展字段或日志 |
| 降级 | gRPC 失败时返回 500，开发阶段不做复杂降级 |

## 7. 访问记录写入

访问记录不由 Recommend Service 写入。由 Spring Boot API Server 的 REST 接口负责：

| 接口 | 职责 |
| --- | --- |
| `POST /api/v1/videos/{videoId}/views/me` | 新建或更新当前用户访问记录 |

前端触发时机固定为：切换到视频并开始展示时调用。

## 8. 异常处理

| 场景 | API Server 处理 |
| --- | --- |
| gRPC 超时 | 返回 HTTP 500，业务码 `50001`，日志记录 `recommend_grpc_timeout` |
| gRPC 返回空列表 | 返回空 `items`、`hasMore=false` |
| cursor 非法 | REST 层或 gRPC 层返回 `40001` |
| 数据库无对应视频详情 | API Server 跳过缺失视频并记录警告；如果全部缺失返回空列表 |

## 9. 测试重点

| 测试 | 验收 |
| --- | --- |
| 排序测试 | 多条视频按 `like_count desc` 返回 |
| 过滤测试 | `video_views` 中已有记录的视频不返回 |
| 分页测试 | 第一页和第二页无重复 |
| gRPC 调用测试 | REST 推荐接口必须经过 `RecommendService.ListRecommendedVideos` |
| 异常测试 | gRPC 不可用时 REST 返回 500 并有日志 |
