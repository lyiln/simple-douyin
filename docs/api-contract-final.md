# API Contract Final

## 1. 通用约定

固定架构：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

接口前缀：`/api/v1`

| 约定 | 内容 |
| --- | --- |
| 后端技术栈 | Java 17 + Spring Boot + Maven |
| RPC | gRPC |
| 数据库 | MySQL 8 |
| 视频存储 | 后端本地 `uploads/` 目录 |
| 请求格式 | JSON 接口使用 `Content-Type: application/json; charset=utf-8`；发布视频优先使用 `multipart/form-data` |
| 认证方式 | 需登录接口使用 `Authorization: Bearer <accessToken>` |
| 时间格式 | ISO 8601 UTC 字符串 |
| 分页 | `cursor` + `limit`，默认 `limit=10`，最大 30 |
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
| 200 | 0 | 查询、幂等更新、删除、退出成功 |
| 201 | 0 | 注册、发布视频、创建访问记录、创建评论成功 |
| 400 | 40001 | 参数非法 |
| 400 | 40002 | 必填内容为空 |
| 400 | 40003 | 内容超长 |
| 401 | 40101 | 未登录或 token 无效 |
| 403 | 40301 | 无权限，例如删除他人视频 |
| 404 | 40401 | 视频不存在 |
| 404 | 40402 | 用户不存在 |
| 409 | 40901 | 用户名已存在或发布状态冲突 |
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
  "avatarUrl": null
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
    "avatarUrl": null
  },
  "caption": "夜里的山风和星空",
  "videoUrl": "/uploads/videos/2001.mp4",
  "coverUrl": null,
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

### Comment

```json
{
  "id": 3001,
  "videoId": 2001,
  "author": {
    "id": 1001,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": null
  },
  "content": "这个视频很适合演示推荐流。",
  "createdAt": "2026-06-05T08:10:00Z"
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

状态码：`201`、`400`、`409`。

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

状态码：`200`、`400`、`401`、`429`。

### 4.3 退出

```http
POST /api/v1/auth/logout
Authorization: Bearer <token>
```

退出登录简化为客户端删除 token；服务端不维护 `auth_tokens`，该接口返回成功即可。

响应 `200`：

```json
{
  "loggedOut": true
}
```

状态码：`200`、`401`。

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

状态码：`200`、`401`。

## 6. 推荐流

### 6.1 获取推荐视频

```http
GET /api/v1/feeds/recommended/videos?cursor=&limit=10
Authorization: Bearer <token>
```

规则：

| 项 | 内容 |
| --- | --- |
| 调用链 | Spring Boot API Server 必须通过 gRPC 调用 RecommendService |
| 排序 | `like_count desc, created_at desc, id desc` |
| 过滤 | 排除 `video_views` 中当前用户已访问视频 |
| 详情补全 | RecommendService 返回 videoId 列表，API Server 批量查询视频详情和 `viewerState` |

响应 `200`：

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false,
  "strategy": "like_count_desc_exclude_viewed"
}
```

状态码：`200`、`400`、`401`、`500`。

### 6.2 记录访问过的视频

```http
POST /api/v1/videos/{videoId}/views/me
Authorization: Bearer <token>
Content-Type: application/json
```

前端触发时机固定为：切换到视频并开始展示时调用。

请求：

```json
{
  "source": "recommended_feed",
  "watchDurationMs": 0
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

状态码：`200`、`201`、`400`、`401`、`404`。

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

状态码：`200`、`401`、`404`。

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

状态码：`200`、`401`、`404`。

## 8. 视频发布与管理

### 8.1 发布视频

```http
POST /api/v1/videos
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

P0 默认使用 multipart 上传到后端本地 `uploads/` 目录。开发期如需先打通后端流程，可临时接受 `videoUrl` 字段，但最终演示优先使用 multipart。

multipart 字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `caption` | string | 是 | 1 到 200 字 |
| `videoFile` | file | 是 | 视频文件 |
| `coverFile` | file | 否 | 封面文件 |
| `durationMs` | int | 否 | 视频时长 |
| `visibility` | string | 否 | 默认 `public` |

开发期 JSON 简化请求：

```json
{
  "caption": "今天的本地短视频展示",
  "videoUrl": "/uploads/videos/demo.mp4",
  "coverUrl": null,
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
    "videoUrl": "/uploads/videos/2001.mp4",
    "coverUrl": null,
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

状态码：`201`、`400`、`401`、`413`、`500`。

### 8.2 分页查看我的视频

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

状态码：`200`、`400`、`401`。

### 8.3 删除我的视频

```http
DELETE /api/v1/videos/{videoId}
Authorization: Bearer <token>
```

重复删除按幂等处理，固定返回 `200`。

响应 `200`：

```json
{
  "videoId": 2001,
  "deleted": true
}
```

状态码：`200`、`401`、`403`、`404`。

## 9. 健康检查

### 9.1 获取服务健康状态

```http
GET /api/v1/health
```

响应 `200`：

```json
{
  "status": "UP",
  "components": {
    "apiServer": "UP",
    "mysql": "UP",
    "recommendService": "UP"
  }
}
```

状态码：`200`、`500`。

## 10. P0-lite 评论接口

评论是 P0-lite / 低优先级必备场景。实现顺序排在核心推荐、点赞、发布、我的视频、删除和日志之后。

### 10.1 获取视频评论

```http
GET /api/v1/videos/{videoId}/comments?cursor=&limit=20
Authorization: Bearer <token>
```

响应 `200`：

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false,
  "commentCount": 0
}
```

状态码：`200`、`400`、`401`、`404`。

### 10.2 发表评论

```http
POST /api/v1/videos/{videoId}/comments
Authorization: Bearer <token>
Content-Type: application/json
```

请求：

```json
{
  "content": "这个视频很适合演示推荐流。"
}
```

响应 `201`：

```json
{
  "comment": {
    "id": 3001,
    "videoId": 2001,
    "author": {
      "id": 1001,
      "username": "alice",
      "nickname": "Alice",
      "avatarUrl": null
    },
    "content": "这个视频很适合演示推荐流。",
    "createdAt": "2026-06-05T08:10:00Z"
  },
  "commentCount": 1
}
```

状态码：`201`、`400`、`401`、`404`、`429`.

## 11. Bonus / 不作为 P0 的接口

| 接口组 | 状态 |
| --- | --- |
| `POST /api/v1/media-upload-tokens` | Bonus；P0 使用本地 `uploads/` multipart 上传 |
| `GET /api/v1/metrics` | Bonus / 不做 |
| 收藏 `/favorites` | Bonus |
| 关注 `/following` | Bonus |
| 分享 `/shares` | Bonus |
| 消息 `/message-overview` | Bonus |
| 搜索 `/videos?q=` | Bonus |
| 他人主页 `/users/{userId}` | Bonus |
