# Task Breakdown

## 1. 固定基线

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

- Java 17 + Spring Boot + Maven。
- gRPC 推荐服务。
- MySQL 8。
- 数据库初始化优先 `sql/schema.sql`，Flyway 可选。
- 视频存储使用后端本地 `uploads/`。
- 评论为 P0-lite，排在核心 P0 之后。

## 2. 任务顺序

| 序号 | 任务 | 输入 | 输出 | 验收标准 |
| --- | --- | --- | --- | --- |
| T01 | 建立后端基础结构 | 最终规划文档 | Java 17 + Spring Boot + Maven 的 API Server 与 gRPC Recommend Service 目录/模块边界 | Maven 项目可识别；两个运行单元边界清晰；约定本地 `uploads/` |
| T02 | 建立 MySQL 8 schema | `docs/database-design.md` | `sql/schema.sql`；可选 Flyway migration | `users`、`videos`、`video_likes`、`video_views`、`request_logs`、`comments` 可创建成功；不创建 `auth_tokens`；不将 `upload_objects` 作为 P0 |
| T03 | 实现统一响应和错误码 | `docs/api-contract-final.md` | 统一响应模型 | 所有接口返回 `code/message/data/requestId` |
| T04 | 实现请求日志中间件 | `request_logs` 设计 | 请求日志写入 | 每个请求记录输入、输出、状态码、耗时、userId/requestId；敏感字段脱敏 |
| T05 | 实现账号注册 | `users` 表 | `POST /auth/register` | 新用户可注册；用户名重复返回 409；密码非明文存储 |
| T06 | 实现登录和 token | `users` 表、密码哈希 | `POST /auth/login` | 正确密码返回 token；错误密码返回 401 |
| T07 | 实现退出和鉴权 | 客户端删除 token 决策 | `POST /auth/logout`、鉴权中间件 | logout 返回成功；未登录访问 P0 接口返回 401；不创建 `auth_tokens` |
| T08 | 实现当前用户信息 | `users`、`videos` | `GET /me` | 返回当前用户基本信息和视频数量 |
| T09 | 准备本地视频存储 | 本地 `uploads/` 决策 | 存储目录、静态访问路径、文件类型和大小规则 | API Server 可保存并访问 `uploads/` 下的视频；不做预签名 URL |
| T10 | 实现发布视频 | 本地存储、`videos` | `POST /videos` | 优先支持 multipart 上传；保存文件后写 videos，作者为当前用户；开发期可接受 `videoUrl` |
| T11 | 实现我的视频分页 | `videos` | `GET /me/videos` | 只返回当前用户视频，按发布时间倒序，支持 cursor/limit |
| T12 | 实现删除视频权限 | `videos` | `DELETE /videos/{videoId}` | 删除自己视频成功；删除他人视频返回 403；重复删除返回 200 |
| T13 | 实现点赞 / 取消点赞 | `video_likes`、`videos` | `PUT/DELETE /videos/{videoId}/likes/me` | 幂等；like_count 正确变化 |
| T14 | 实现访问记录 | `video_views`、`videos` | `POST /videos/{videoId}/views/me` | 前端切换并展示视频时调用；记录幂等写入；view_count 合理更新 |
| T15 | 实现 gRPC 推荐契约 | `docs/rpc-design.md` | proto 和生成配置 | API Server 可调用 `RecommendService.ListRecommendedVideos` |
| T16 | 实现推荐规则 | `videos`、`video_views` | gRPC Recommend Service 返回 videoIds | 按 `like_count desc, created_at desc, id desc`，排除已访问视频 |
| T17 | 实现推荐流 REST | gRPC 返回 videoIds | `GET /feeds/recommended/videos` | REST 调 gRPC，并按原顺序补齐视频详情和 viewerState |
| T18 | 实现健康检查 | API Server、MySQL 8、gRPC 状态 | `GET /health` | 返回 API Server、MySQL、Recommend Service 状态 |
| T19 | 编写核心接口测试 | API 契约 | 自动化测试或接口集合 | Core P0 正常/异常用例通过 |
| T20 | 编写推荐规则测试 | 推荐规则 | 推荐测试数据和用例 | 验证排序、过滤、分页和 gRPC 调用 |
| T21 | 编写权限测试 | 鉴权和删除规则 | 权限测试用例 | 未登录 401；删除他人视频 403；用户数据隔离 |
| T22 | 编写日志测试 | `request_logs` | 日志测试用例 | 每个接口均有输入、输出、耗时记录 |
| T23 | 实现 P0-lite 获取评论 | `comments`、`videos` | `GET /videos/{videoId}/comments` | 支持 cursor/limit，按时间倒序 |
| T24 | 实现 P0-lite 发表评论 | `comments`、`videos` | `POST /videos/{videoId}/comments` | 登录用户可评论；空内容 400；视频不存在 404 |
| T25 | 编写 P0-lite 评论测试 | 评论 API | 评论测试用例 | 列表、创建、鉴权、参数异常通过 |
| T26 | 前端接入账号和推荐流 | Android Demo、API 契约 | 登录页、推荐流网络数据 | 可登录后刷真实推荐视频，切换时记录访问 |
| T27 | 前端接入发布、我的视频、删除和评论 | Android Demo、API 契约 | 发布/我的视频/删除/评论联调 | 可发布、分页查看、删除自己的视频、查看和发送评论 |
| T28 | 编写 README 和交付文档 | 所有设计和测试结果 | README、需求、技术设计、测试文档 | README 可按步骤部署；文档覆盖课程清单 |
| T29 | 制作答辩 PPT 和演示视频 | 完成的系统 | PPT、演示视频 | 能演示核心 P0、P0-lite、日志、gRPC、数据库设计 |

## 3. 每阶段里程碑

| 阶段 | 结束标准 |
| --- | --- |
| M1 基础后端 | Spring Boot/Maven、MySQL 8 schema、注册、登录、鉴权、日志可用 |
| M2 视频管理 | 本地 uploads、发布、我的视频分页、删除权限、点赞、访问记录可用 |
| M3 推荐闭环 | gRPC 推荐、访问过滤、点赞排序、健康检查可用 |
| M4 P0-lite | 评论列表和发表评论可用 |
| M5 前端联调 | Android Demo 可演示完整 P0/P0-lite 主流程 |
| M6 交付 | README、测试文档、PPT、演示视频齐全 |

## 4. 最新第一个编码任务

任务名：**T01 + T02 后端基础结构与数据库 schema**。

范围：

- 创建 Java 17 + Spring Boot + Maven 后端基础结构。
- 建立 Spring Boot API Server 与 gRPC Recommend Service 的目录/模块边界。
- 配置 MySQL 8 连接约定。
- 准备本地 `uploads/` 目录约定。
- 创建 `sql/schema.sql`。
- 创建 Core P0 表：`users`、`videos`、`video_likes`、`video_views`、`request_logs`。
- 创建 P0-lite 表：`comments`。
- 不创建 `auth_tokens`；不把 `upload_objects` 作为 P0 表。

验收标准：

- Maven 项目结构可识别。
- `sql/schema.sql` 可在 MySQL 8 执行成功。
- Core P0/P0-lite 表、主键、唯一索引和推荐/分页索引符合最终数据库设计。
- 架构边界符合固定链路。
- 不实现业务接口，不实现 Bonus 功能。
