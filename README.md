# Simple Douyin

## 项目简介

本仓库是《API 设计与实现》课程大作业项目，目标是实现一个简易版抖音 / 视频流推荐系统。

当前主线目标是完成账号系统、视频发布、推荐流、访问过滤、点赞、我的视频分页、删除权限控制、请求日志、健康检查和评论闭环。课程评分点优先级高于 `frontend/docs/API_DESIGN.md` 中的前端草稿。

## 技术架构

最终规划架构固定为：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

当前后端技术栈：

| 项目 | 说明 |
| --- | --- |
| Java | Java 17 |
| 后端框架 | Spring Boot |
| 构建工具 | Maven |
| 数据库 | MySQL 8 |
| 推荐服务 RPC | gRPC，当前仍待实现 proto 和服务逻辑 |
| 视频存储 | 后端本地 `uploads/` 目录 |

## 当前实现进度

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| T01 + T02 后端基础结构与数据库 schema | 已完成 | 已建立 Maven 多模块结构，包含 `backend/api-server`、`backend/recommend-service` 和 `sql/schema.sql`。 |
| T03 + T04 统一响应、错误码、requestId、请求日志 | 已完成 | 已实现统一响应结构、错误码、全局异常处理、requestId 和请求日志中间件。 |
| T05 注册 | 已完成 | 已实现 `POST /api/v1/auth/register`，BCrypt 存储密码哈希，注册成功返回 token。 |
| T06 登录 | 已完成 | 已实现 `POST /api/v1/auth/login`，复用 BCrypt 校验和 HMAC token 签发。 |
| T07 退出和鉴权基础能力 | 已完成 | 已实现 `POST /api/v1/auth/logout` 和 Bearer Token 解析校验，不创建 `auth_tokens`。 |
| T08 当前用户信息 | 已完成 | 已实现 `GET /api/v1/me`，返回 profile、videoCount、likedCount。 |
| T09 本地 uploads 存储基础能力 | 已完成 | 已实现本地上传配置、静态资源映射和存储组件。 |
| T10 发布视频 | 已完成 | 已实现 `POST /api/v1/videos`，支持 multipart 上传和开发期 JSON `videoUrl` 分支。 |
| T11 我的列表分页 | 已完成 | 已实现 `GET /api/v1/me/videos`，只返回当前用户未删除视频，支持 cursor + limit。 |
| T12 删除视频权限控制 | 已完成 | 已实现 `DELETE /api/v1/videos/{videoId}`，支持所有权校验、软删除和重复删除幂等。 |
| T13 点赞 | 未完成 | 点赞和取消点赞接口尚未实现。 |
| T14 访问记录 | 未完成 | 浏览记录接口尚未实现。 |
| T15-T17 gRPC 推荐和推荐流 | 部分完成 | 已有 recommend-service 模块边界，proto、gRPC server、推荐规则和 REST 推荐流接口尚未实现。 |
| T18 health | 未完成 | `GET /api/v1/health` 尚未实现。 |
| T23-T24 评论闭环 | 未完成 | 评论列表和发表评论接口尚未实现。 |
| 前端真实联调 | 未完成 | `frontend/` 是 Android Demo，当前未确认已接入真实后端 API。 |
| 交付材料 | 未完成 | 需求文档、技术设计文档、测试文档、答辩 PPT、团队分工、成员评分表、演示视频和公开 Git 地址仍需准备。 |

## 已实现接口

统一响应外层格式为 `code`、`message`、`data`、`requestId`。

| 方法和路径 | 需要 Authorization | 主要用途 | 当前状态 |
| --- | --- | --- | --- |
| `POST /api/v1/auth/register` | 否 | 注册账号，返回 user、accessToken、expiresIn | 已实现 |
| `POST /api/v1/auth/login` | 否 | 用户名密码登录，返回 user、accessToken、expiresIn | 已实现 |
| `POST /api/v1/auth/logout` | 是 | 退出登录，服务端返回成功，客户端删除 token | 已实现 |
| `GET /api/v1/me` | 是 | 查询当前用户 profile、videoCount、likedCount | 已实现 |
| `POST /api/v1/videos` | 是 | 发布视频，multipart 保存到本地 uploads，也支持开发期 JSON `videoUrl` | 已实现 |
| `GET /api/v1/me/videos` | 是 | 分页查看当前用户未删除视频 | 已实现 |
| `DELETE /api/v1/videos/{videoId}` | 是 | 软删除自己的视频，重复删除返回成功 | 已实现 |

## 未实现接口 / 后续计划

| 方法和路径 | 主要用途 | 当前状态 |
| --- | --- | --- |
| `PUT /api/v1/videos/{videoId}/likes/me` | 点赞视频 | 未实现 |
| `DELETE /api/v1/videos/{videoId}/likes/me` | 取消点赞 | 未实现 |
| `POST /api/v1/videos/{videoId}/views/me` | 前端开始展示视频时记录访问 | 未实现 |
| `GET /api/v1/feeds/recommended/videos` | 推荐视频流，API Server 通过 gRPC 获取推荐 videoIds | 未实现 |
| `GET /api/v1/health` | 检查 API Server、MySQL、gRPC Recommend Service | 未实现 |
| `GET /api/v1/videos/{videoId}/comments` | 评论列表，最终演示闭环必做 | 未实现 |
| `POST /api/v1/videos/{videoId}/comments` | 发表评论，最终演示闭环必做 | 未实现 |

## 数据库初始化

数据库设计位于 `sql/schema.sql`，目标数据库为 MySQL 8。当前 schema 包含：

- `users`
- `videos`
- `video_likes`
- `video_views`
- `request_logs`
- `comments`

示例初始化命令：

```bash
mysql -u root -p < sql/schema.sql
```

不要在命令或配置文件中提交真实数据库密码。

## 后端运行方式

在仓库根目录执行：

```bash
mvn -q test
```

启动 API Server 示例：

```bash
mvn -pl backend/api-server spring-boot:run
```

启动 Recommend Service 骨架示例：

```bash
mvn -pl backend/recommend-service spring-boot:run
```

说明：当前 recommend-service 只有模块边界和 Spring Boot 启动骨架，尚未实现 gRPC proto、gRPC server 和推荐算法。当前已实现的账号和视频管理接口主要运行在 API Server。

## 配置说明

API Server 的配置位于 `backend/api-server/src/main/resources/application.yml`，主要通过环境变量覆盖：

| 配置 | 说明 |
| --- | --- |
| `MYSQL_URL` | MySQL 连接地址 |
| `MYSQL_USERNAME` | MySQL 用户名 |
| `MYSQL_PASSWORD` | MySQL 密码 |
| `API_SERVER_PORT` | API Server 端口，默认 `8080` |
| `AUTH_TOKEN_SECRET` | HMAC token 密钥，开发环境有默认值，生产或演示部署应通过环境变量设置 |
| `AUTH_TOKEN_EXPIRES_IN_SECONDS` | token 过期秒数 |
| `UPLOADS_DIR` | 本地 uploads 根目录，默认 `uploads` |
| `UPLOADS_PUBLIC_PATH` | 静态访问前缀，默认 `/uploads/` |
| `UPLOADS_VIDEO_DIR` | 视频子目录，默认 `videos` |
| `UPLOADS_COVER_DIR` | 封面子目录，默认 `covers` |
| `RECOMMEND_GRPC_HOST` | 推荐服务地址 |
| `RECOMMEND_GRPC_PORT` | 推荐服务 gRPC 端口 |

本地上传文件保存在后端本地 `uploads/` 下。当前发布视频接口会复用存储组件保存 multipart 文件，并通过 `/uploads/**` 静态资源映射访问。

## 测试方式

常用检查命令：

```bash
mvn -q test
git diff --check
git diff --name-only -- frontend
```

当前测试主要覆盖统一响应、账号接口、鉴权、请求日志、本地上传存储、发布视频、我的视频分页和删除权限。测试不依赖真实 MySQL；真实落库和完整启动仍需要本地 MySQL 8 环境。

## 前端说明

`frontend/` 当前是 Android 前端 Demo。README 只说明当前仓库状态，不声称前端已经完成真实后端联调。

后续 Android 模拟器联调本机后端时，通常需要把 API Base URL 配置为：

```text
http://10.0.2.2:8080
```

如果在真机上调试，需要使用电脑在局域网中的实际 IP 地址。

## 非 P0 / 暂不实现

以下内容不是当前主线，不应在 P0 完成前优先实现：

- 不创建 `auth_tokens` 表。
- `upload_objects` 不作为 P0 表。
- `POST /media-upload-tokens` 是 Bonus，不作为当前上传方案。
- 独立 `GET /metrics` 接口是 Bonus。
- 不做对象存储。
- 不做预签名 URL。
- 收藏、关注、分享、消息、搜索不是主线。

## 下一步开发计划

按当前代码状态和任务顺序，建议继续：

| 顺序 | 任务 | 说明 |
| --- | --- | --- |
| 1 | T13 点赞 | 实现点赞和取消点赞，维护 `video_likes` 和 `videos.like_count`。 |
| 2 | T14 访问记录 | 实现展示视频时记录访问，供推荐过滤使用。 |
| 3 | T15-T17 gRPC 推荐和推荐流 | 实现 proto、Recommend Service、推荐规则和 REST 推荐流。 |
| 4 | T18 health | 实现 API Server、MySQL、gRPC Recommend Service 健康检查。 |
| 5 | T23-T24 评论 | 实现评论列表和发表评论，完成演示闭环。 |
| 6 | 测试、前端联调、交付材料 | 补齐测试文档、答辩 PPT、团队分工、成员评分表、演示视频和公开 Git 地址。 |
