# 安卓短视频平台 REST API 设计文档

版本：v1  
接口前缀：`/api/v1`  
适用客户端：当前 Android Compose 短视频 Demo 后续接入真实后端  
更新时间：2026-06-05

## 1. 文档目标

当前 Android 端继续使用 `MockRepository` 和本地状态完成推荐流、朋友页、发布、点赞、收藏、关注、评论、分享、消息预览、个人主页和搜索，暂不接入真实网络请求。本接口文档将这些本地数据和交互转换为后端 v1 REST API 契约，方便后端按资源边界开发并在后续联调时替换 mock 数据层。

本版不包含登录注册、私信会话详情、私信发送、举报审核、推荐算法后台配置和个人资料编辑。业务接口统一预留 Bearer Token，后端开发阶段可先提供测试 token。

## 2. 通用约定

### 2.1 Base URL

```text
https://api.example.com/api/v1
```

开发环境可替换为：

```text
http://47.95.238.140:18090/api/v1
```

### 2.2 REST 资源风格

- URL 使用名词和复数资源集合，不在路径中使用动作词，例如不使用 `/like`、`/follow`、`/search/videos`。
- 使用 HTTP 方法表达操作语义：
  - `GET`：读取资源或资源集合。
  - `POST`：创建新资源，例如视频、评论、分享记录、上传凭证。
  - `PUT`：创建或替换一个可确定 URI 的关系资源，例如当前用户对某视频的点赞关系。
  - `DELETE`：删除一个可确定 URI 的关系资源。
- 当前用户相关资源统一放在 `/me` 下，例如 `/me`、`/me/following/users`、`/me/following/videos`。
- 用户与视频之间的互动状态建模为关系资源，例如 `/videos/{videoId}/likes/me` 和 `/videos/{videoId}/favorites/me`。
- 查询和搜索优先使用集合过滤参数表达，例如 `GET /videos?q=风景`，而不是单独动作式搜索路径。

### 2.3 请求格式

- 普通接口使用 `Content-Type: application/json; charset=utf-8`。
- 响应统一使用 JSON。
- 时间字段统一使用 ISO 8601 UTC 字符串，例如 `2026-06-05T08:30:00Z`。
- 计数字段使用整数原值返回，由客户端负责格式化为 `1.2万` 等展示文案。
- 本地资源字段替换规则：
  - `videoRes` -> `videoUrl`
  - `coverRes` -> `coverUrl`
  - `avatarRes` -> `avatarUrl`

### 2.4 认证

除公开浏览接口可临时放开外，建议业务接口统一支持：

```http
Authorization: Bearer <token>
```

本版不定义登录注册接口。后端可在联调阶段提供固定测试 token，并根据 token 解析当前用户 `viewer`，用于返回 `liked`、`collected`、`following` 等状态。

### 2.5 统一响应体

HTTP 状态码用于表达请求级结果，响应体中的 `code` 用于表达业务错误码。客户端应优先根据 HTTP 状态码处理认证、权限、限流和服务端异常，再读取业务 `code` 处理具体业务原因。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "req_202606050830000001"
}
```

失败响应：

```json
{
  "code": 40001,
  "message": "invalid parameter: limit",
  "data": null,
  "requestId": "req_202606050830000002"
}
```

### 2.6 分页约定

列表接口统一使用游标分页。

请求参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cursor` | string | 否 | 上一页返回的 `nextCursor`，首次请求不传 |
| `limit` | int | 否 | 每页条数，默认 10，最大 30 |

分页响应结构：

```json
{
  "items": [],
  "nextCursor": "cursor_eyJpZCI6InYxMjMifQ",
  "hasMore": true
}
```

### 2.7 HTTP 状态码

| 状态码 | 使用场景 |
| --- | --- |
| `200` | 查询成功、幂等更新成功 |
| `201` | 创建成功，例如发布作品、发送评论 |
| `400` | 请求参数错误 |
| `401` | 未登录或 token 无效 |
| `403` | 无权限 |
| `404` | 资源不存在 |
| `409` | 状态冲突，例如上传资源未完成 |
| `413` | 上传文件过大 |
| `429` | 请求过于频繁 |
| `500` | 服务端异常 |

### 2.8 业务错误码

| code | message 示例 | 说明 |
| --- | --- | --- |
| `0` | `ok` | 成功 |
| `40001` | `invalid parameter` | 参数格式、范围、枚举值错误 |
| `40002` | `content is empty` | 内容为空 |
| `40003` | `content too long` | 内容超长 |
| `40101` | `unauthorized` | 未登录或 token 无效 |
| `40301` | `forbidden` | 无权限访问 |
| `40401` | `video not found` | 视频不存在 |
| `40402` | `user not found` | 用户不存在 |
| `40901` | `media upload not completed` | 媒体文件尚未上传完成 |
| `42901` | `too many requests` | 请求频率过高 |
| `50001` | `internal server error` | 服务端异常 |

## 3. 核心数据模型

### 3.1 UserSummary

用于视频作者、关注列表、评论作者、消息头像等轻量用户展示。

```json
{
  "id": "user_mountain_notes",
  "nickname": "山野记录员",
  "handle": "@mountain_notes",
  "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
  "bio": "记录山风、星空和校园日常"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 用户唯一 ID |
| `nickname` | string | 是 | 昵称，对应当前 `author` |
| `handle` | string | 是 | 抖音号或展示账号，对应当前 `handle` |
| `avatarUrl` | string | 是 | 头像 URL |
| `bio` | string | 否 | 简介 |

### 3.2 ViewerState

当前登录用户相对某条视频或某个作者的状态。

```json
{
  "liked": false,
  "collected": true,
  "following": false
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `liked` | boolean | 是 | 当前用户是否已点赞该视频 |
| `collected` | boolean | 是 | 当前用户是否已收藏该视频 |
| `following` | boolean | 是 | 当前用户是否已关注作者 |

### 3.3 VideoPost

用于首页推荐流、朋友动态、搜索结果和个人作品列表。

```json
{
  "id": "video_mountain_night",
  "author": {
    "id": "user_mountain_notes",
    "nickname": "山野记录员",
    "handle": "@mountain_notes",
    "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
    "bio": "记录山风、星空和校园日常"
  },
  "caption": "夜里的山风和星空，适合循环看七秒。",
  "topic": "#风景 #治愈 #校园大作业",
  "music": "山谷回声 - 原声",
  "videoUrl": "https://cdn.example.com/videos/mountain_night.mp4",
  "coverUrl": "https://cdn.example.com/covers/mountain_night.webp",
  "durationMs": 7000,
  "width": 1080,
  "height": 1920,
  "likeCount": 32800,
  "commentCount": 524,
  "shareCount": 94,
  "collectCount": 1200,
  "visibility": "public",
  "status": "published",
  "createdAt": "2026-06-05T08:00:00Z",
  "viewerState": {
    "liked": false,
    "collected": false,
    "following": false
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 视频唯一 ID |
| `author` | `UserSummary` | 是 | 作者信息 |
| `caption` | string | 是 | 标题/描述，对应当前 `caption` |
| `topic` | string | 是 | 话题文本，对应当前 `topic` |
| `music` | string | 是 | 音乐或原声展示文案 |
| `videoUrl` | string | 是 | 视频播放 URL |
| `coverUrl` | string | 是 | 封面 URL |
| `durationMs` | int | 否 | 视频时长，毫秒 |
| `width` | int | 否 | 视频宽度 |
| `height` | int | 否 | 视频高度 |
| `likeCount` | int | 是 | 点赞数 |
| `commentCount` | int | 是 | 评论数 |
| `shareCount` | int | 是 | 分享数 |
| `collectCount` | int | 是 | 收藏数 |
| `visibility` | string | 是 | `public`、`private` |
| `status` | string | 是 | `processing`、`published`、`failed` |
| `createdAt` | string | 是 | 发布时间 |
| `viewerState` | `ViewerState` | 是 | 当前用户状态 |

### 3.4 Comment

```json
{
  "id": "comment_10001",
  "videoId": "video_mountain_night",
  "author": {
    "id": "user_visitor",
    "nickname": "路过的同学",
    "handle": "@visitor",
    "avatarUrl": "https://cdn.example.com/avatars/visitor.webp"
  },
  "content": "这个封面氛围感很适合短视频首页。",
  "createdAt": "2026-06-05T08:10:00Z"
}
```

### 3.5 ChatPreview

本版只覆盖消息首页预览。

```json
{
  "id": "chat_system_notice",
  "type": "system",
  "title": "系统通知",
  "message": "你的本地作品已发布到推荐流",
  "avatarUrl": "https://cdn.example.com/avatars/system.webp",
  "unread": 1,
  "updatedAt": "2026-06-05T07:20:00Z"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 会话或通知入口 ID |
| `type` | string | 是 | `interaction`、`friend_activity`、`creator_notice`、`system`、`direct` |
| `title` | string | 是 | 标题 |
| `message` | string | 是 | 最新消息摘要 |
| `avatarUrl` | string | 是 | 图标或头像 URL |
| `unread` | int | 是 | 未读数 |
| `updatedAt` | string | 是 | 最新更新时间 |

### 3.6 UserProfile

```json
{
  "id": "user_local_me",
  "nickname": "我",
  "handle": "@local_me",
  "avatarUrl": "https://cdn.example.com/avatars/local_me.webp",
  "coverUrl": "https://cdn.example.com/covers/profile_gradient.webp",
  "bio": "",
  "likedCount": 12,
  "followingCount": 3,
  "followerCount": 8,
  "videoCount": 3,
  "viewerState": {
    "following": false
  }
}
```

### 3.7 UploadToken

预签名直传使用。后端返回对象存储上传地址，客户端直传成功后再创建视频记录。

```json
{
  "uploadId": "upload_20260605_0001",
  "mediaType": "video",
  "objectKey": "videos/2026/06/05/upload_20260605_0001.mp4",
  "uploadUrl": "https://oss.example.com/bucket/videos/2026/06/05/upload_20260605_0001.mp4?signature=xxx",
  "headers": {
    "Content-Type": "video/mp4"
  },
  "publicUrl": "https://cdn.example.com/videos/2026/06/05/upload_20260605_0001.mp4",
  "expiresAt": "2026-06-05T08:45:00Z"
}
```

## 4. 接口详情

### 4.1 获取推荐视频集合

获取首页「推荐」垂直视频流。

```http
GET /api/v1/feeds/recommended/videos?cursor=&limit=10
Authorization: Bearer <token>
```

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `cursor` | string | 否 | 分页游标 |
| `limit` | int | 否 | 每页数量，默认 10，最大 30 |

响应 `data`：

```json
{
  "items": [
    {
      "id": "video_mountain_night",
      "author": {
        "id": "user_mountain_notes",
        "nickname": "山野记录员",
        "handle": "@mountain_notes",
        "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
        "bio": "记录山风、星空和校园日常"
      },
      "caption": "夜里的山风和星空，适合循环看七秒。",
      "topic": "#风景 #治愈 #校园大作业",
      "music": "山谷回声 - 原声",
      "videoUrl": "https://cdn.example.com/videos/mountain_night.mp4",
      "coverUrl": "https://cdn.example.com/covers/mountain_night.webp",
      "durationMs": 7000,
      "width": 1080,
      "height": 1920,
      "likeCount": 32800,
      "commentCount": 524,
      "shareCount": 94,
      "collectCount": 1200,
      "visibility": "public",
      "status": "published",
      "createdAt": "2026-06-05T08:00:00Z",
      "viewerState": {
        "liked": false,
        "collected": false,
        "following": false
      }
    }
  ],
  "nextCursor": "cursor_video_mountain_night",
  "hasMore": true
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | `limit` 超出范围或 `cursor` 非法 |
| `40101` | token 无效 |

### 4.2 获取当前用户关注作者的视频集合

获取朋友页使用的关注作者视频列表。

```http
GET /api/v1/me/following/videos?cursor=&limit=10
Authorization: Bearer <token>
```

响应 `data` 与推荐流一致，返回 `VideoPost` 分页列表。若当前用户未关注任何作者，返回空列表：

```json
{
  "items": [],
  "nextCursor": null,
  "hasMore": false
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | 分页参数错误 |
| `40101` | 未登录 |

### 4.3 获取当前用户关注用户集合

朋友页顶部「正在关注」横向列表使用。

```http
GET /api/v1/me/following/users?cursor=&limit=20
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "items": [
    {
      "id": "user_mountain_notes",
      "nickname": "山野记录员",
      "handle": "@mountain_notes",
      "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
      "bio": "记录山风、星空和校园日常"
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |

### 4.4 创建关注关系

在当前用户的关注用户集合中创建指定用户关系。该接口必须幂等：重复创建同一关注关系不重复增加关注数。

```http
PUT /api/v1/me/following/users/{userId}
Authorization: Bearer <token>
```

Path 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | string | 是 | 被关注用户 ID |

请求 body：无

响应 `data`：

```json
{
  "userId": "user_mountain_notes",
  "following": true,
  "followingCount": 4,
  "followerCount": 1025
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40402` | 用户不存在 |
| `40001` | 不能关注自己 |

### 4.5 删除关注关系

从当前用户的关注用户集合中删除指定用户关系。该接口必须幂等：关系不存在时调用仍返回成功。

```http
DELETE /api/v1/me/following/users/{userId}
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "userId": "user_mountain_notes",
  "following": false,
  "followingCount": 3,
  "followerCount": 1024
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40402` | 用户不存在 |

### 4.6 创建视频点赞关系

在视频点赞集合中创建当前用户的点赞关系。该接口必须幂等：重复创建同一点赞关系不重复累加。

```http
PUT /api/v1/videos/{videoId}/likes/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": "video_mountain_night",
  "liked": true,
  "likeCount": 32801
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40401` | 视频不存在 |

### 4.7 删除视频点赞关系

从视频点赞集合中删除当前用户的点赞关系。该接口必须幂等：关系不存在时调用仍返回成功。

```http
DELETE /api/v1/videos/{videoId}/likes/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": "video_mountain_night",
  "liked": false,
  "likeCount": 32800
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40401` | 视频不存在 |

### 4.8 创建视频收藏关系

在视频收藏集合中创建当前用户的收藏关系。该接口必须幂等：重复创建同一收藏关系不重复累加。

```http
PUT /api/v1/videos/{videoId}/favorites/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": "video_mountain_night",
  "collected": true,
  "collectCount": 1201
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40401` | 视频不存在 |

### 4.9 删除视频收藏关系

从视频收藏集合中删除当前用户的收藏关系。该接口必须幂等：关系不存在时调用仍返回成功。

```http
DELETE /api/v1/videos/{videoId}/favorites/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "videoId": "video_mountain_night",
  "collected": false,
  "collectCount": 1200
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |
| `40401` | 视频不存在 |

### 4.10 获取视频评论

评论弹层打开时调用。

```http
GET /api/v1/videos/{videoId}/comments?cursor=&limit=20
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "items": [
    {
      "id": "comment_10001",
      "videoId": "video_mountain_night",
      "author": {
        "id": "user_visitor",
        "nickname": "路过的同学",
        "handle": "@visitor",
        "avatarUrl": "https://cdn.example.com/avatars/visitor.webp"
      },
      "content": "这个封面氛围感很适合短视频首页。",
      "createdAt": "2026-06-05T08:10:00Z"
    }
  ],
  "nextCursor": "cursor_comment_10001",
  "hasMore": false,
  "commentCount": 524
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | 分页参数错误 |
| `40401` | 视频不存在 |

### 4.11 发送评论

评论输入框发送按钮使用。

```http
POST /api/v1/videos/{videoId}/comments
Authorization: Bearer <token>
Content-Type: application/json
```

请求 body：

```json
{
  "content": "评论已添加"
}
```

参数说明：

| 参数 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `content` | string | 是 | 去除首尾空白后 1 到 300 字 |

响应 `data`：

```json
{
  "comment": {
    "id": "comment_10002",
    "videoId": "video_mountain_night",
    "author": {
      "id": "user_local_me",
      "nickname": "我",
      "handle": "@local_me",
      "avatarUrl": "https://cdn.example.com/avatars/local_me.webp"
    },
    "content": "评论已添加",
    "createdAt": "2026-06-05T08:30:00Z"
  },
  "commentCount": 525
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40002` | 评论内容为空 |
| `40003` | 评论内容超长 |
| `40101` | 未登录 |
| `40401` | 视频不存在 |
| `42901` | 评论过于频繁 |

### 4.12 记录分享

分享弹层选择「复制链接」「微信」「朋友圈」「保存本地」后调用，用于增加分享数或记录分享行为。

```http
POST /api/v1/videos/{videoId}/shares
Authorization: Bearer <token>
Content-Type: application/json
```

请求 body：

```json
{
  "channel": "copy_link"
}
```

参数说明：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `channel` | string | 是 | `copy_link`、`wechat`、`moments`、`save_local` |

响应 `data`：

```json
{
  "videoId": "video_mountain_night",
  "channel": "copy_link",
  "shareCount": 95,
  "shareUrl": "https://h5.example.com/videos/video_mountain_night"
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | `channel` 非法 |
| `40101` | 未登录 |
| `40401` | 视频不存在 |

### 4.13 申请媒体上传凭证

发布作品前调用。客户端拿到预签名 URL 后直接上传视频或封面到对象存储。

```http
POST /api/v1/media-upload-tokens
Authorization: Bearer <token>
Content-Type: application/json
```

请求 body：

```json
{
  "files": [
    {
      "mediaType": "video",
      "fileName": "local_video.mp4",
      "contentType": "video/mp4",
      "fileSize": 257280
    },
    {
      "mediaType": "cover",
      "fileName": "local_cover.webp",
      "contentType": "image/webp",
      "fileSize": 66084
    }
  ]
}
```

参数说明：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `files` | array | 是 | 需要申请上传凭证的文件列表 |
| `files[].mediaType` | string | 是 | `video` 或 `cover` |
| `files[].fileName` | string | 是 | 原始文件名 |
| `files[].contentType` | string | 是 | MIME 类型 |
| `files[].fileSize` | int | 是 | 文件大小，单位 byte |

响应 `data`：

```json
{
  "uploads": [
    {
      "uploadId": "upload_video_20260605_0001",
      "mediaType": "video",
      "objectKey": "videos/2026/06/05/upload_video_20260605_0001.mp4",
      "uploadUrl": "https://oss.example.com/bucket/videos/2026/06/05/upload_video_20260605_0001.mp4?signature=xxx",
      "headers": {
        "Content-Type": "video/mp4"
      },
      "publicUrl": "https://cdn.example.com/videos/2026/06/05/upload_video_20260605_0001.mp4",
      "expiresAt": "2026-06-05T08:45:00Z"
    },
    {
      "uploadId": "upload_cover_20260605_0001",
      "mediaType": "cover",
      "objectKey": "covers/2026/06/05/upload_cover_20260605_0001.webp",
      "uploadUrl": "https://oss.example.com/bucket/covers/2026/06/05/upload_cover_20260605_0001.webp?signature=yyy",
      "headers": {
        "Content-Type": "image/webp"
      },
      "publicUrl": "https://cdn.example.com/covers/2026/06/05/upload_cover_20260605_0001.webp",
      "expiresAt": "2026-06-05T08:45:00Z"
    }
  ]
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | 文件类型不支持 |
| `413` | 文件过大 |
| `40101` | 未登录 |

### 4.14 发布作品

客户端完成直传后调用。成功后返回完整 `VideoPost`，客户端可直接插入推荐流顶部。

```http
POST /api/v1/videos
Authorization: Bearer <token>
Content-Type: application/json
```

请求 body：

```json
{
  "caption": "今天的本地短视频展示",
  "topic": "#校园大作业 #Compose",
  "music": "Compose Demo - 原声",
  "videoUploadId": "upload_video_20260605_0001",
  "coverUploadId": "upload_cover_20260605_0001",
  "durationMs": 7000,
  "width": 1080,
  "height": 1920,
  "visibility": "public"
}
```

参数说明：

| 参数 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `caption` | string | 是 | 1 到 200 字 |
| `topic` | string | 否 | 0 到 100 字 |
| `music` | string | 否 | 0 到 80 字，未传时后端可默认 `原声` |
| `videoUploadId` | string | 是 | 视频上传凭证 ID |
| `coverUploadId` | string | 是 | 封面上传凭证 ID |
| `durationMs` | int | 否 | 视频时长 |
| `width` | int | 否 | 视频宽 |
| `height` | int | 否 | 视频高 |
| `visibility` | string | 否 | 默认 `public` |

响应 `data`：

```json
{
  "video": {
    "id": "video_local_20260605_0001",
    "author": {
      "id": "user_local_me",
      "nickname": "我",
      "handle": "@local_me",
      "avatarUrl": "https://cdn.example.com/avatars/local_me.webp",
      "bio": ""
    },
    "caption": "今天的本地短视频展示",
    "topic": "#校园大作业 #Compose",
    "music": "Compose Demo - 原声",
    "videoUrl": "https://cdn.example.com/videos/2026/06/05/upload_video_20260605_0001.mp4",
    "coverUrl": "https://cdn.example.com/covers/2026/06/05/upload_cover_20260605_0001.webp",
    "durationMs": 7000,
    "width": 1080,
    "height": 1920,
    "likeCount": 0,
    "commentCount": 0,
    "shareCount": 0,
    "collectCount": 0,
    "visibility": "public",
    "status": "published",
    "createdAt": "2026-06-05T08:32:00Z",
    "viewerState": {
      "liked": false,
      "collected": false,
      "following": false
    }
  }
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40002` | 标题为空 |
| `40003` | 标题或话题超长 |
| `40101` | 未登录 |
| `40901` | 上传资源不存在或未上传完成 |

### 4.15 获取当前用户消息概览资源

消息页使用。本版只返回消息入口和会话预览，不提供会话详情。

```http
GET /api/v1/me/message-overview
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "quickActions": [
    {
      "type": "interaction",
      "title": "互动消息",
      "unread": 0
    },
    {
      "type": "friend_activity",
      "title": "朋友动态",
      "unread": 0
    },
    {
      "type": "creator_notice",
      "title": "创作通知",
      "unread": 1
    }
  ],
  "chats": [
    {
      "id": "chat_system_notice",
      "type": "system",
      "title": "系统通知",
      "message": "你的本地作品已发布到推荐流",
      "avatarUrl": "https://cdn.example.com/avatars/system.webp",
      "unread": 1,
      "updatedAt": "2026-06-05T07:20:00Z"
    },
    {
      "id": "chat_demo_group",
      "type": "direct",
      "title": "前端展示小组",
      "message": "今天主要演示刷视频和发布流程",
      "avatarUrl": "https://cdn.example.com/avatars/demo_group.webp",
      "unread": 0,
      "updatedAt": "2026-06-05T06:12:00Z"
    }
  ]
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |

### 4.16 获取当前用户资料

个人主页「我」页面使用。

```http
GET /api/v1/me
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "profile": {
    "id": "user_local_me",
    "nickname": "我",
    "handle": "@local_me",
    "avatarUrl": "https://cdn.example.com/avatars/local_me.webp",
    "coverUrl": "https://cdn.example.com/covers/profile_gradient.webp",
    "bio": "",
    "likedCount": 12,
    "followingCount": 3,
    "followerCount": 8,
    "videoCount": 3,
    "viewerState": {
      "following": false
    }
  }
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40101` | 未登录 |

### 4.17 获取用户公开资料

查看其他作者资料时使用。当前客户端暂未进入作者详情页，但关注状态和个人页后续可复用该接口。

```http
GET /api/v1/users/{userId}
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "profile": {
    "id": "user_mountain_notes",
    "nickname": "山野记录员",
    "handle": "@mountain_notes",
    "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
    "coverUrl": "https://cdn.example.com/covers/mountain_profile.webp",
    "bio": "记录山风、星空和校园日常",
    "likedCount": 32800,
    "followingCount": 12,
    "followerCount": 1024,
    "videoCount": 8,
    "viewerState": {
      "following": false
    }
  }
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40402` | 用户不存在 |

### 4.18 获取用户作品列表

个人主页作品宫格使用。

```http
GET /api/v1/users/{userId}/videos?cursor=&limit=18
Authorization: Bearer <token>
```

响应 `data`：

```json
{
  "items": [
    {
      "id": "video_local_20260605_0001",
      "author": {
        "id": "user_local_me",
        "nickname": "我",
        "handle": "@local_me",
        "avatarUrl": "https://cdn.example.com/avatars/local_me.webp",
        "bio": ""
      },
      "caption": "今天的本地短视频展示",
      "topic": "#校园大作业 #Compose",
      "music": "Compose Demo - 原声",
      "videoUrl": "https://cdn.example.com/videos/2026/06/05/upload_video_20260605_0001.mp4",
      "coverUrl": "https://cdn.example.com/covers/2026/06/05/upload_cover_20260605_0001.webp",
      "durationMs": 7000,
      "width": 1080,
      "height": 1920,
      "likeCount": 0,
      "commentCount": 0,
      "shareCount": 0,
      "collectCount": 0,
      "visibility": "public",
      "status": "published",
      "createdAt": "2026-06-05T08:32:00Z",
      "viewerState": {
        "liked": false,
        "collected": false,
        "following": false
      }
    }
  ],
  "nextCursor": null,
  "hasMore": false
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40001` | 分页参数错误 |
| `40402` | 用户不存在 |

### 4.19 查询视频集合

搜索页输入框使用，支持按视频标题、作者昵称、话题搜索。

```http
GET /api/v1/videos?q=%E9%A3%8E%E6%99%AF&cursor=&limit=10
Authorization: Bearer <token>
```

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `q` | string | 是 | 搜索关键词，1 到 50 字 |
| `cursor` | string | 否 | 分页游标 |
| `limit` | int | 否 | 每页数量，默认 10，最大 30 |

响应 `data`：

```json
{
  "query": "风景",
  "items": [
    {
      "id": "video_mountain_night",
      "author": {
        "id": "user_mountain_notes",
        "nickname": "山野记录员",
        "handle": "@mountain_notes",
        "avatarUrl": "https://cdn.example.com/avatars/mountain_notes.webp",
        "bio": "记录山风、星空和校园日常"
      },
      "caption": "夜里的山风和星空，适合循环看七秒。",
      "topic": "#风景 #治愈 #校园大作业",
      "music": "山谷回声 - 原声",
      "videoUrl": "https://cdn.example.com/videos/mountain_night.mp4",
      "coverUrl": "https://cdn.example.com/covers/mountain_night.webp",
      "durationMs": 7000,
      "width": 1080,
      "height": 1920,
      "likeCount": 32800,
      "commentCount": 524,
      "shareCount": 94,
      "collectCount": 1200,
      "visibility": "public",
      "status": "published",
      "createdAt": "2026-06-05T08:00:00Z",
      "viewerState": {
        "liked": false,
        "collected": false,
        "following": false
      }
    }
  ],
  "nextCursor": null,
  "hasMore": false,
  "totalHint": 1
}
```

空结果响应：

```json
{
  "query": "不存在的关键词",
  "items": [],
  "nextCursor": null,
  "hasMore": false,
  "totalHint": 0
}
```

错误码：

| code | 场景 |
| --- | --- |
| `40002` | 搜索关键词为空 |
| `40003` | 搜索关键词超长 |
| `40001` | 分页参数错误 |

## 5. 客户端页面与接口映射

| 页面/交互 | 当前本地实现 | 后端接口 |
| --- | --- | --- |
| 首页推荐流 | `MockRepository.initialPosts()` | `GET /feeds/recommended/videos` |
| 视频播放资源 | `videoRes` | `videoUrl` |
| 视频封面 | `coverRes` | `coverUrl` |
| 作者头像 | `avatarRes` | `author.avatarUrl` |
| 点赞 | 本地 `liked` map | `PUT/DELETE /videos/{videoId}/likes/me` |
| 收藏 | 本地 `collected` map | `PUT/DELETE /videos/{videoId}/favorites/me` |
| 关注 | 本地 `following` map | `PUT/DELETE /me/following/users/{userId}` |
| 评论列表 | `MockRepository.commentsFor(postId)` | `GET /videos/{videoId}/comments` |
| 发送评论 | 本地插入 `Comment("我", text, "刚刚")` | `POST /videos/{videoId}/comments` |
| 分享 | 本地 toast 反馈 | `POST /videos/{videoId}/shares` |
| 朋友页视频 | 本地复用 `posts` | `GET /me/following/videos` |
| 朋友页关注列表 | 本地复用 `posts` 作者 | `GET /me/following/users` |
| 发布素材上传 | 本地选择内置 raw 视频 | `POST /media-upload-tokens` + 对象存储直传 |
| 创建作品 | 本地 `posts.add(0, newPost)` | `POST /videos` |
| 消息首页 | `MockRepository.chatPreviews()` | `GET /me/message-overview` |
| 个人资料 | 静态 `我/local_me` | `GET /me` |
| 作品宫格 | 本地 `posts.chunked(3)` | `GET /users/{userId}/videos` |
| 搜索 | 本地过滤 caption/author/topic | `GET /videos?q={keyword}` |

## 6. 联调测试场景

后端可用 Postman 或 Apifox 按以下场景验收。

1. 首页首次加载：不传 `cursor` 请求 `GET /feeds/recommended/videos`，返回 `items` 非空，视频字段、计数和 `viewerState` 完整。
2. 首页翻页：传上一页 `nextCursor`，返回下一页数据；最后一页 `hasMore=false`。
3. 点赞与取消：先 `PUT /videos/{videoId}/likes/me`，再重复 `PUT`，`likeCount` 不重复增加；再 `DELETE`，重复 `DELETE` 仍成功。
4. 收藏与取消：验证 `PUT/DELETE /videos/{videoId}/favorites/me` 幂等，返回 `collected` 和 `collectCount`。
5. 关注与取消：验证 `PUT/DELETE /me/following/users/{userId}` 幂等，推荐流或用户资料中的 `following` 状态可同步变化。
6. 评论新增：先 `GET /videos/{videoId}/comments` 获取数量，再 `POST /videos/{videoId}/comments`，返回新增评论和新 `commentCount`，列表刷新可看到最新评论。
7. 分享记录：分别提交 `copy_link`、`wechat`、`moments`、`save_local`，返回合法 `shareUrl` 和 `shareCount`。
8. 发布作品：先调用 `POST /media-upload-tokens` 申请上传凭证，模拟或真实完成直传后调用 `POST /videos`，返回完整 `VideoPost` 且计数为 0。
9. 个人作品列表：发布成功后请求 `GET /me` 和 `GET /users/{userId}/videos`，`videoCount` 和作品列表可反映新增作品。
10. 搜索命中：用作者、标题、话题关键字分别请求 `GET /videos?q={keyword}`，结果包含匹配视频。
11. 搜索空结果：传不存在的关键词，返回空 `items`、`hasMore=false`、`totalHint=0`。
12. 消息预览：请求 `GET /me/message-overview`，返回三个快捷入口和会话预览，包含未读数。
13. 异常参数：传超大 `limit`、空评论、超长搜索词，分别返回约定错误码。
14. 未登录：不带 token 请求需要登录的接口，返回 HTTP `401` 和业务码 `40101`。

## 7. 后端实现建议

- 推荐流接口需要一次性返回计数和当前用户状态，避免客户端逐条补查互动状态。
- 点赞、收藏、关注建议使用唯一索引保证幂等，例如 `(user_id, video_id)` 或 `(follower_id, followee_id)`。
- 评论创建后应异步或同步更新视频 `commentCount`，接口响应以最终展示计数为准。
- 分享接口可以按用户、视频、渠道记录明细；是否每次都增加 `shareCount` 由产品决定，本版默认每次合法分享动作增加一次。
- 预签名上传凭证应设置较短有效期，例如 15 分钟；创建作品时校验 `uploadId` 属于当前用户且对象已存在。
- 视频如果需要转码，`POST /videos` 可先返回 `status=processing`；当前客户端可优先接入 `published`，后续再补轮询或消息通知。
- 所有列表接口应稳定排序：推荐流由推荐服务排序，个人作品按 `createdAt desc`，评论按 `createdAt desc`。

## 8. 字段对照当前 Mock 数据

| 当前字段 | 接口字段 | 示例 |
| --- | --- | --- |
| `VideoPost.id` | `VideoPost.id` | `video_mountain_night` |
| `VideoPost.author` | `VideoPost.author.nickname` | `山野记录员` |
| `VideoPost.handle` | `VideoPost.author.handle` | `@mountain_notes` |
| `VideoPost.caption` | `VideoPost.caption` | `夜里的山风和星空，适合循环看七秒。` |
| `VideoPost.topic` | `VideoPost.topic` | `#风景 #治愈 #校园大作业` |
| `VideoPost.music` | `VideoPost.music` | `山谷回声 - 原声` |
| `VideoPost.likes` | `VideoPost.likeCount` | `32800` |
| `VideoPost.comments` | `VideoPost.commentCount` | `524` |
| `VideoPost.shares` | `VideoPost.shareCount` | `94` |
| `VideoPost.videoRes` | `VideoPost.videoUrl` | `https://cdn.example.com/videos/mountain_night.mp4` |
| `VideoPost.coverRes` | `VideoPost.coverUrl` | `https://cdn.example.com/covers/mountain_night.webp` |
| `VideoPost.avatarRes` | `VideoPost.author.avatarUrl` | `https://cdn.example.com/avatars/mountain_notes.webp` |
| `Comment.author` | `Comment.author.nickname` | `路过的同学` |
| `Comment.content` | `Comment.content` | `这个封面氛围感很适合短视频首页。` |
| `Comment.time` | `Comment.createdAt` | `2026-06-05T08:10:00Z` |
| `ChatPreview.title` | `ChatPreview.title` | `系统通知` |
| `ChatPreview.message` | `ChatPreview.message` | `你的本地作品已发布到推荐流` |
| `ChatPreview.time` | `ChatPreview.updatedAt` | `2026-06-05T07:20:00Z` |
| `ChatPreview.unread` | `ChatPreview.unread` | `1` |
