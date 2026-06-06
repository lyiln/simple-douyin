# Scope Final

## Context

本规划基于 `AGENTS.md`、`docs/gap-analysis.md`、`frontend/docs/API_DESIGN.md`、当前 Android Compose Demo 源码和课程大作业要求。

当前目标不是复刻完整抖音，而是交付“简易版抖音 / 视频流推荐系统”的课程必需闭环：账号 -> 发布视频 -> 推荐视频 -> 访问记录过滤 -> 点赞 -> 我的视频分页 -> 删除权限 -> 日志监控 -> P0-lite 评论 -> 文档测试。

固定架构：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

固定技术栈：

| 项 | 决策 |
| --- | --- |
| 后端语言与框架 | Java 17 + Spring Boot |
| 构建工具 | Maven |
| RPC | gRPC |
| 数据库 | MySQL 8 |
| 数据库初始化 | 先提供 `sql/schema.sql`；Flyway 可选，不为 Flyway 增加复杂度 |
| 视频存储 | 后端本地 `uploads/` 目录 |

## Core P0 必须完成

| 模块 | 功能 | 验收口径 |
| --- | --- | --- |
| 用户系统 | 注册、登录、退出 | 注册后可登录；登录返回 token；退出接口返回成功，客户端删除 token |
| 鉴权 | Bearer Token 解析当前用户 | 未登录访问 P0 业务接口返回 401；无权限返回 403 |
| 当前用户 | `GET /me` | 返回当前登录用户资料和基础统计 |
| 推荐视频主页 | 推荐视频列表 | Android 端可上下滑动播放推荐视频；后端返回可分页推荐流 |
| 推荐规则 | 按点赞数最高推荐 | gRPC Recommend Service 按 `like_count desc, created_at desc, id desc` 排序 |
| 浏览过滤 | 用户访问过的视频不再推荐 | 已写入 `video_views` 的视频不会再出现在该用户推荐流中 |
| 访问记录 | 前端切换到视频并开始展示时记录 | 调用 `POST /videos/{videoId}/views/me` 幂等写入 |
| gRPC 推荐服务 | API Server 通过 gRPC 调用 RecommendService | API Server 不在 Controller 内直接拼推荐规则 |
| 点赞 | 点赞 / 取消点赞 | 幂等；重复点赞不重复加计数；取消点赞不重复减计数 |
| 发布视频 | 发布视频记录 | 登录用户可通过 multipart 上传到本地 `uploads/` 并发布；开发期可接受 `videoUrl` 简化 |
| 我的视频 | 分页查看我的视频 | `GET /me/videos` 支持分页，按发布时间倒序 |
| 删除我的视频 | 只能删除自己的视频 | 删除他人视频返回 403；重复删除按幂等处理返回 200 |
| 数据库 | MySQL 8 表设计 | P0 表为 `users`、`videos`、`video_likes`、`video_views`、`request_logs` |
| 日志 | 记录输入、输出、耗时 | 每个用户请求记录 requestId、userId、接口、请求、响应、状态码、耗时 |
| 监控 | `GET /health` | 返回 API Server、MySQL 8、gRPC Recommend Service 健康状态 |
| 安全 | 账号、密码、权限 | 密码哈希存储；删除/发布/点赞/浏览记录均绑定当前用户 |
| 测试 | 核心测试文档和用例 | 覆盖正常、异常、权限、推荐规则、日志、健康检查 |
| 交付材料 | README、需求、技术设计、测试、PPT、分工、演示视频 | README 写部署步骤；其他材料按课程提交 |

## P0-lite / 低优先级必备

| 功能 | 接口 / 表 | 顺序 |
| --- | --- | --- |
| 获取评论列表 | `GET /api/v1/videos/{videoId}/comments` | 核心 P0 完成后实现 |
| 发表评论 | `POST /api/v1/videos/{videoId}/comments` | 核心 P0 完成后实现 |
| 评论表 | `comments` | 写入 `sql/schema.sql` 规划 |

P0-lite 评论排在推荐、点赞、发布、我的视频、删除、日志之后，不阻塞核心推荐闭环。

## Bonus / 暂不优先

| 功能 | 原因 | 处理方式 |
| --- | --- | --- |
| `POST /media-upload-tokens` | P0 使用本地 `uploads/`，不做预签名 URL | Bonus |
| `GET /metrics` | P0 只做 `GET /health` 和日志统计 | Bonus |
| 收藏 | 课程未要求 | Bonus |
| 关注 / 朋友页 | 课程未要求 | Demo 可保留本地效果，后端暂不实现 |
| 分享 | 课程未要求 | Bonus |
| 消息 | 课程未要求 | Bonus |
| 搜索 | 课程未要求 | Bonus |
| 他人主页 | 课程未要求 | 暂不实现或仅保留作者摘要字段 |
| 私信 / 举报 / 审核 | 远超课程范围 | 不做 |
| 复杂推荐算法 | 课程规则已指定按点赞数 | 不引入机器学习、召回画像、复杂排序 |
| 对象存储 / 预签名上传 | P0 使用本地 `uploads/` | Bonus |
| `auth_tokens` | 退出登录已简化为客户端删除 token | 不做 |

## 最小演示闭环

1. 用户注册并登录。
2. 用户发布一个视频到本地 `uploads/`。
3. 我的页面分页看到该视频。
4. 当前用户删除自己的视频成功，删除他人视频失败。
5. 推荐页通过 Spring Boot API Server 调 gRPC Recommend Service 返回未看过的视频。
6. 点赞某视频后，其 `like_count` 增加，推荐排序可体现点赞数影响。
7. 用户访问某视频后写入 `video_views`，再次刷新推荐流不再出现该视频。
8. 日志中可查到每个接口的输入、输出、耗时和 userId。
9. `GET /health` 可查看 API Server、MySQL 8、gRPC Recommend Service 状态。
10. 核心 P0 后补 P0-lite 评论列表和发表评论。
