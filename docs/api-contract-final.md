# API Contract Final

## 1. 通用约定

接口前缀：`/api/v1`

保留 `frontend/docs/API_DESIGN.md` 中有价值的 RESTful 约定：

| 约定 | 内容 |
| --- | --- |
| 请求格式 | JSON 接口使用 `Content-Type: application/json; charset=utf-8` |
| 认证方式 | 需登录接口使用 `Authorization: Bearer <accessToken>` |
| 时间格式 | ISO 8601 UTC 字符串，如 `2026-06-05T08:30:00Z` |
| 分页 | `cursor` + `limit`，默认 `limit=10`，最大 30 |
| 关系资源 | 点赞、浏览记录使用当前用户关系资源，如 `/videos/{videoId}/likes/me` |
| 统一响应 | HTTP 状态码表达请求级结果，业务 `code` 表达应用级错误 |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "req_202606060100000001"
}
```

失败响应：

```json
{
  "code": 40101,
  "message": "unauthorized",
  "data": null,
  "requestId": "req_202606060100000002"
}
```

## 2. 状态码和业务码

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 查询、幂等更新、删除成功 |
| 201 | 0 | 注册、发布视频、创建访问记录成功 |
| 400 | 40001 | 参数非法 |
| 400 | 40002 | 必填内容为空 |
| 400 | 40003 | 内容超长 |
| 401 | 40101 | 未登录或 token 无效 |
| 403 | 40301 | 无权限，例如删除他人视频 |
| 404 | 40401 | 视频不存在 |
| 404 | 40402 | 用户不存在 |
| 409 | 40901 | 用户名已存在或上传资源未完成 |
| 413 | 41301 | 视频文件过大 |
| 429 | 42901 | 请求过于频繁 |
| 500 | 50001 | 服务端异常 |

## 3. 核心数据模型

### UserSummary

```json
{
  "id": 1001,
  "username": "alice",
  "nickname": "Alice",
  "avatarUrl": "https://cdn.example.com/avatars/1001.webp"
}
```

### ViewerState

```json
{
  "liked": false,
  "viewed": false,
  "owner": false
}
```

### VideoPost

```json
{
  "id": 2001,
  "author": {
    "id": 1001,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": "https://cdn.example.com/avatars/1001.webp"
  },
  "caption": "夜里的山风和星空",
  "videoUrl": "https://cdn.example.com/videos/2001.mp4",
  "coverUrl": "https://cdn.example.com/covers/2001.webp",
  "durationMs": 7000,
  "likeCount": 32800,
  "viewCount": 120000,
  "commentCount": 0,
  "visibility": "public",
  "status": "published",
  "createdAt": "2026-06-05T08:00:00Z",
  "viewerState": {
    "liked": false,
    "viewed": false,
    "owner": false
  }
}
```

## 4. 账号接口

### 4.1 注册

```http
POST /api/v1/auth/register
Content-Type: application/json
```

请求：

```json
{
  "username": "alice",
  "password": "Passw0rd!",
  "nickname": "Alice"
}
```

响应 `201`：

```json
{
  "user": {
    "id": 1001,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": null
  },
  "accessToken": "jwt_or_session_token",
  "expiresIn": 7200
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 201 | 0 | 注册成功 |
| 400 | 40001 | 用户名或密码格式非法 |
| 400 | 40002 | 用户名、密码或昵称为空 |
| 409 | 40901 | 用户名已存在 |

### 4.2 登录

```http
POST /api/v1/auth/login
Content-Type: application/json
```

请求：

```json
{
  "username": "alice",
  "password": "Passw0rd!"
}
```

响应 `200`：

```json
{
  "user": {
    "id": 1001,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": null
  },
  "accessToken": "jwt_or_session_token",
  "expiresIn": 7200
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 登录成功 |
| 400 | 40002 | 用户名或密码为空 |
| 401 | 40101 | 用户名或密码错误 |
| 429 | 42901 | 登录尝试过于频繁 |

### 4.3 退出

```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

请求：无 body。

响应 `200`：

```json
{
  "loggedOut": true
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 退出成功 |
| 401 | 40101 | token 无效 |

## 5. 当前用户

### 5.1 获取当前用户信息

```http
GET /api/v1/me
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "profile": {
    "id": 1001,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": null,
    "videoCount": 3,
    "likedCount": 12
  }
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 查询成功 |
| 401 | 40101 | 未登录 |

## 6. 推荐流

### 6.1 获取推荐视频

```http
GET /api/v1/feeds/recommended/videos?cursor=&limit=10
Authorization: Bearer <token>
```

规则：

| 项 | 内容 |
| --- | --- |
| 调用链 | API Server 必须通过 RPC 调用 RecommendService |
| 排序 | `like_count desc, created_at desc` |
| 过滤 | 排除 `video_views` 中当前用户已访问视频 |
| 详情补全 | RecommendService 返回 videoId 列表，API Server 批量查询视频详情和 `viewerState` |

响应 `200`：

```json
{
  "items": [
    {
      "id": 2001,
      "author": {
        "id": 1001,
        "username": "alice",
        "nickname": "Alice",
        "avatarUrl": null
      },
      "caption": "夜里的山风和星空",
      "videoUrl": "https://cdn.example.com/videos/2001.mp4",
      "coverUrl": "https://cdn.example.com/covers/2001.webp",
      "durationMs": 7000,
      "likeCount": 32800,
      "viewCount": 120000,
      "commentCount": 0,
      "visibility": "public",
      "status": "published",
      "createdAt": "2026-06-05T08:00:00Z",
      "viewerState": {
        "liked": false,
        "viewed": false,
        "owner": false
      }
    }
  ],
  "nextCursor": "eyJsaWtlQ291bnQiOjMyODAwLCJpZCI6MjAwMX0",
  "hasMore": true,
  "strategy": "like_count_desc_exclude_viewed"
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 查询成功，可为空列表 |
| 400 | 40001 | `cursor` 或 `limit` 非法 |
| 401 | 40101 | 未登录 |
| 500 | 50001 | RPC 推荐服务不可用或服务端异常 |

### 6.2 记录访问过的视频

```http
POST /api/v1/videos/{videoId}/views/me
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "source": "recommended_feed",
  "watchDurationMs": 1200
}
```

响应 `201` 或重复访问时 `200`：

```json
{
  "videoId": 2001,
  "viewed": true,
  "viewCount": 120001
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 已存在访问记录，幂等成功 |
| 201 | 0 | 新建访问记录成功 |
| 400 | 40001 | 参数非法 |
| 401 | 40101 | 未登录 |
| 404 | 40401 | 视频不存在 |

## 7. 点赞

### 7.1 点赞视频

```http
PUT /api/v1/videos/{videoId}/likes/me
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "videoId": 2001,
  "liked": true,
  "likeCount": 32801
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 点赞成功或已点赞，幂等成功 |
| 401 | 40101 | 未登录 |
| 404 | 40401 | 视频不存在 |

### 7.2 取消点赞

```http
DELETE /api/v1/videos/{videoId}/likes/me
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "videoId": 2001,
  "liked": false,
  "likeCount": 32800
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 取消成功或原本未点赞，幂等成功 |
| 401 | 40101 | 未登录 |
| 404 | 40401 | 视频不存在 |

## 8. 视频发布与管理

### 8.1 申请媒体上传凭证

```http
POST /api/v1/media-upload-tokens
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "files": [
    {
      "mediaType": "video",
      "fileName": "demo.mp4",
      "contentType": "video/mp4",
      "fileSize": 257280
    },
    {
      "mediaType": "cover",
      "fileName": "demo.webp",
      "contentType": "image/webp",
      "fileSize": 66084
    }
  ]
}
```

响应 `201`：

```json
{
  "uploads": [
    {
      "uploadId": "up_2001_video",
      "mediaType": "video",
      "objectKey": "videos/2026/06/06/up_2001_video.mp4",
      "uploadUrl": "https://storage.example.com/upload-url",
      "publicUrl": "https://cdn.example.com/videos/2001.mp4",
      "expiresAt": "2026-06-06T01:15:00Z"
    }
  ]
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 201 | 0 | 创建上传凭证成功 |
| 400 | 40001 | 文件类型非法 |
| 401 | 40101 | 未登录 |
| 413 | 41301 | 文件过大 |

### 8.2 发布视频

```http
POST /api/v1/videos
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "caption": "今天的本地短视频展示",
  "videoUploadId": "up_2001_video",
  "coverUploadId": "up_2001_cover",
  "durationMs": 7000,
  "visibility": "public"
}
```

响应 `201`：

```json
{
  "video": {
    "id": 2001,
    "author": {
      "id": 1001,
      "username": "alice",
      "nickname": "Alice",
      "avatarUrl": null
    },
    "caption": "今天的本地短视频展示",
    "videoUrl": "https://cdn.example.com/videos/2001.mp4",
    "coverUrl": "https://cdn.example.com/covers/2001.webp",
    "durationMs": 7000,
    "likeCount": 0,
    "viewCount": 0,
    "commentCount": 0,
    "visibility": "public",
    "status": "published",
    "createdAt": "2026-06-06T01:10:00Z",
    "viewerState": {
      "liked": false,
      "viewed": false,
      "owner": true
    }
  }
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 201 | 0 | 发布成功 |
| 400 | 40002 | 标题为空 |
| 400 | 40003 | 标题超长 |
| 401 | 40101 | 未登录 |
| 409 | 40901 | 上传资源不存在、未完成或不属于当前用户 |

### 8.3 分页查看我的视频

```http
GET /api/v1/me/videos?cursor=&limit=18
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false
}
```

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 查询成功 |
| 400 | 40001 | 分页参数非法 |
| 401 | 40101 | 未登录 |

### 8.4 删除我的视频

```http
DELETE /api/v1/videos/{videoId}
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "videoId": 2001,
  "deleted": true
}
```

权限规则：

| 规则 | 结果 |
| --- | --- |
| `videos.author_id == currentUser.id` | 允许删除 |
| `videos.author_id != currentUser.id` | 返回 403 |
| 视频已删除 | 可返回 200 幂等成功，或 404；建议 200 幂等成功 |

状态码：

| HTTP | 业务码 | 场景 |
| --- | --- | --- |
| 200 | 0 | 删除成功或已删除 |
| 401 | 40101 | 未登录 |
| 403 | 40301 | 删除他人视频 |
| 404 | 40401 | 视频不存在 |

## 9. 评论接口 Bonus

评论不是课程 P0。若团队时间允许，可保留 API_DESIGN.md 中：

| 接口 | 状态 |
| --- | --- |
| `GET /videos/{videoId}/comments` | Bonus |
| `POST /videos/{videoId}/comments` | Bonus |

## 10. 主线不实现的接口

| 接口组 | 原因 |
| --- | --- |
| 收藏 `/favorites` | 非课程必须 |
| 关注 `/following` | 非课程必须 |
| 分享 `/shares` | 非课程必须 |
| 消息 `/message-overview` | 非课程必须 |
| 搜索 `/videos?q=` | 非课程必须 |
| 他人主页 `/users/{userId}` | 非课程必须 |
