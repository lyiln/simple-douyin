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
| 推荐服务 RPC | gRPC（已实现 RecommendService.ListRecommendedVideos，proto 和服务逻辑已完成） |
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
| T13 点赞 | 已完成 | 已实现 `PUT/DELETE /api/v1/videos/{videoId}/likes/me`，INSERT IGNORE 幂等，维护 like_count。 |
| T14 访问记录 | 已完成 | 已实现 `POST /api/v1/videos/{videoId}/views/me`，ON DUPLICATE KEY UPDATE 幂等，首次访问递增 view_count。 |
| T15-T17 gRPC 推荐和推荐流 | 已完成（成员B） | proto、Recommend Service、推荐规则和 REST 推荐流已实现。`GET /api/v1/feeds/recommended/videos` 通过 gRPC 调用 RecommendService，按 like_count DESC 排序并排除已访问视频。 |
| T18 health | 已完成 | `GET /api/v1/health` 已完成，检查 API Server、MySQL、gRPC Recommend Service。 |
| T19 核心接口测试 | 已完成（成员A） | 新增 LikeControllerTest (12)、ViewControllerTest (8)、HealthControllerTest (6)，覆盖正常/异常/幂等。 |
| T20 推荐规则测试 | 已完成（成员B） | RecommendRepositoryTest (7 用例，真实 MySQL)、FeedControllerTest (10 用例，MockMvc)，R01-R08 + E10 全部通过。 |
| T21 权限测试 | 已完成（成员A） | 点赞、取消点赞、访问记录接口的未登录/无效 token 测试全部通过。 |
| T22 日志测试 | 已完成（成员A） | requestId、userId、path、statusCode、businessCode、durationMs 记录验证通过。 |
| T23-T25 评论闭环 | 已完成（成员C） | 评论列表、发表评论及测试，最终演示必做。 |
| T26-T27 前端联调 | 已完成（成员C） | Android Demo 已添加 Retrofit 网络层、ApiClient/ApiService/ApiRepository，评论功能支持真实 API 对接（登录状态下优先 API，否则回退本地 mock）。 |
| T28-T31 验收与交付 | 未完成（成员C） | 评分点矩阵、文档、PPT、演示视频和最终提交检查。 |

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
| `PUT /api/v1/videos/{videoId}/likes/me` | 是 | 点赞视频，幂等，维护 like_count | 已实现 |
| `DELETE /api/v1/videos/{videoId}/likes/me` | 是 | 取消点赞，幂等，维护 like_count | 已实现 |
| `POST /api/v1/videos/{videoId}/views/me` | 是 | 记录视频访问，首次访问递增 view_count | 已实现 |
| `GET /api/v1/health` | 否 | 检查 API Server、MySQL、gRPC Recommend Service | 已实现 |
| `GET /api/v1/feeds/recommended/videos` | 是 | 推荐视频流，通过 gRPC 调用 RecommendService，按 like_count DESC 排序并排除已访问视频 | 已实现 |

## 未实现接口 / 后续计划

| 方法和路径 | 主要用途 | 当前状态 |
| --- | --- | --- |
| `GET /api/v1/feeds/recommended/videos` | 推荐视频流，API Server 通过 gRPC 获取推荐 videoIds | 已完成（成员B） |
| `GET /api/v1/videos/{videoId}/comments` | 评论列表，最终演示闭环必做 | 已完成 |
| `POST /api/v1/videos/{videoId}/comments` | 发表评论，最终演示闭环必做 | 已完成 |

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

启动 API Server（PowerShell，需先初始化数据库）：

```powershell
$env:MYSQL_PASSWORD="password"; mvn -pl backend/api-server clean spring-boot:run
```

启动 Recommend Service 示例：

```bash
mvn -pl backend/recommend-service spring-boot:run
```

说明：recommend-service 已实现完整的 gRPC proto、gRPC server 和推荐算法（按 like_count DESC, created_at DESC, id DESC 排序，排除已访问视频），启动后监听 gRPC 端口 9090。

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

当前 112 个测试用例全部通过，覆盖范围：

| 测试类 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| `AuthControllerTest` | 9 | 注册、登录、退出正常/异常/日志 |
| `AuthServiceTest` | 11 | 注册/登录逻辑、密码验证、token |
| `UserControllerTest` | 4 | 当前用户、他人主页 |
| `UserProfileRepositoryTest` | 1 | 用户数据查询 |
| `UserServiceTest` | 3 | 用户业务逻辑 |
| `VideoControllerTest` | 6 | 发布/我的视频/删除正常/异常 |
| `LikeControllerTest` | 13 | 点赞/取消正常、幂等、404、权限、日志 |
| `ViewControllerTest` | 10 | 访问记录、首次/重复、404、权限、日志、watchDurationMs |
| `CommentControllerTest` | 16 | 发表评论/评论列表正常、分页、空列表、404、权限、日志 |
| `HealthControllerTest` | 6 | 全UP/部分DOWN、无鉴权、日志 |
| `FeedControllerTest` | 10 | 推荐列表、分页、401/400/500、日志、脱敏（成员B） |
| `RecommendRepositoryTest` | 7 | 排序、过滤、分页、已删除/私密排除（成员B，真实 MySQL） |
| `VideoRepositoryTest` | 14 | 点赞/取消/访问幂等、COUNT 一致性、TOCTOU 防护（成员A，真实 MySQL） |
| `VideoServiceTest` | 10 | 发布/分页/删除业务逻辑 |
| `LocalUploadStorageServiceTest` | 6 | 文件保存/读取/类型校验 |
| `UploadStoragePropertiesTest` | 1 | 配置注入 |
| `UploadWebMvcConfigTest` | 1 | 静态资源映射 |

> 其中 `VideoRepositoryTest` 和 `RecommendRepositoryTest` 需本地 MySQL 8 连接（通过 `MYSQL_PASSWORD` 环境变量），其余 91 个测试使用 MockMvc/Mock 不依赖数据库。

## 前端说明

`frontend/` 当前是 Android 前端 Demo，已接入真实后端 API，登录页仍支持手动修改 API Base URL。

当前已部署后端联调时，API Base URL 默认为：

```text
http://47.95.238.140:18090
```

如果需要改回本机后端调试，可在登录页改为模拟器宿主机地址，例如 `http://10.0.2.2:8080`。

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

| 顺序 | 任务 | 负责人 | 说明 |
| --- | --- | --- | --- |
| 1 | T23-T25 评论闭环 | 成员 C | 实现评论列表和发表评论，完成演示闭环，编写测试。 |
| 2 | T26-T27 前端联调 | 成员 C | Android 接入真实后端 API，覆盖推荐流和视频管理两大场景。 |
| 3 | T28-T31 验收和交付 | 成员 C | 评分点矩阵、文档、PPT、演示视频和最终提交。 |
