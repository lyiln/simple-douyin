# 简易版抖音课程大作业 Gap Analysis

> **历史分析说明**
>
> 本文件记录项目从前端 REST 草稿和 Android Demo 收敛到课程最终方案时的差距，仅用于解释决策背景，不是当前实施契约。
>
> 最终范围和实施口径以 `docs/scope-final.md`、`docs/api-contract-final.md`、`docs/database-design.md`、`docs/rpc-design.md`、`docs/backend-module-plan.md`、`docs/task-breakdown.md`、`docs/test-plan.md` 为准。若历史材料与上述文档冲突，以课程作业要求和 `scope-final.md` 为最高优先级。

## 1. 分析材料

| 材料 | 结论 |
| --- | --- |
| 课程 PDF | 明确推荐、浏览过滤、点赞、视频管理、账号、数据库、存储、日志监控、安全、RPC、两个主要演示场景和完整交付物 |
| `frontend/docs/API_DESIGN.md` | 前端团队的 REST 草稿，可复用部分资源化接口，但不是最终需求来源 |
| Android Compose 源码 | 已具备本地上下滑播放、点赞、评论弹层、发布、个人页等 Demo 交互 |
| APK | 可侧面确认 Demo 资源和页面能力；真实网络、后端和权限能力仍需代码与联调证明 |

## 2. 历史差距与最终处理

| 课程要求 | 初始材料状态 | 最终规划处理 |
| --- | --- | --- |
| 按点赞数最高推荐 | 有推荐流草稿，无确定排序 | 固定 `like_count DESC, created_at DESC, id DESC` |
| 访问过不再推荐 | 缺访问记录 | 使用 `video_views`；视频开始展示时调用访问接口 |
| 主端通过 RPC 推荐 | 只有 REST 草稿 | API Server 必须通过 gRPC `RecommendService.ListRecommendedVideos` 获取 videoIds |
| 上下滑动 | Android Demo 已具备 | 保留 Compose 上下滑能力，改接真实推荐 REST 数据 |
| 点赞 | 有本地交互和 REST 草稿 | 使用 `video_likes` 唯一约束和 `videos.like_count` |
| 评论主要场景 | Demo 有本地评论，旧分析曾误判为非必做 | 评论列表和发表评论为最终演示必做，开发顺序可后置 |
| 发布视频 | Demo 和草稿有发布概念 | 使用 multipart 上传到 API Server 本地 `uploads/` |
| 我的视频分页 | 草稿偏向公开用户作品 | 固定 `GET /api/v1/me/videos`，cursor/limit 分页 |
| 删除我的视频 | 缺接口和权限 | 固定删除接口，校验作者；重复删除返回 200 |
| 登录、注册、退出 | 草稿明确未定义 | 三个接口均为必做；退出由客户端删除 token |
| 数据库设计 | 缺 schema | MySQL 8，先交付 `sql/schema.sql` |
| 视频存储 | 草稿偏对象存储上传凭证 | 最终统一为本地 `uploads/`，不做预签名 URL |
| 日志与监控 | 缺输入、输出和耗时设计 | 全局记录 `request_logs`，支持按接口聚合耗时 |
| 安全和权限 | 只有 Bearer 占位和错误码 | 密码哈希、统一鉴权、资源归属校验、敏感字段脱敏 |
| 测试 | 只有少量联调场景 | 建立正常、异常、权限、推荐、gRPC、日志、health 和前端演示测试矩阵 |
| 交付物 | 初始仓库不完整 | 规划 README、需求、技术设计、测试、PPT、分工、评分表、公开 Git 和演示视频 |

## 3. Android Demo 已有与待接入

| 能力 | 当前判断 | 最终要求 |
| --- | --- | --- |
| 推荐页上下滑动 | 已有本地 `VerticalPager` 和视频播放 | 接入真实推荐列表，支持查看上一个和下一个视频 |
| 点赞 | 已有本地状态 | 接入点赞/取消点赞 API 和服务端计数 |
| 评论 | 已有本地评论弹层 | 接入评论列表和发表评论，纳入主要演示场景 |
| 发布 | 已有本地素材发布 Demo | 接入 multipart 上传和后端 `uploads/` |
| 个人页 | 已有作品展示 | 接入 `GET /me`、我的视频分页和删除 |
| 登录、注册、退出 | 未发现完整真实链路 | 接入账号 API 和 token 管理 |
| 访问过滤 | 未发现 | 视频开始展示时调用访问记录接口 |
| 收藏、关注、分享、消息、搜索 | 有部分本地页面或交互 | 仅 Bonus，不作为开发主线 |

## 4. 最终接口缺口

| 模块 | 最终必做接口 |
| --- | --- |
| 账号 | `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、`GET /api/v1/me` |
| 推荐与访问 | `GET /api/v1/feeds/recommended/videos`、`POST /api/v1/videos/{videoId}/views/me` |
| 点赞 | `PUT /api/v1/videos/{videoId}/likes/me`、`DELETE /api/v1/videos/{videoId}/likes/me` |
| 视频管理 | `POST /api/v1/videos`、`GET /api/v1/me/videos`、`DELETE /api/v1/videos/{videoId}` |
| 评论演示闭环 | `GET /api/v1/videos/{videoId}/comments`、`POST /api/v1/videos/{videoId}/comments` |
| 集成健康 | `GET /api/v1/health` |
| 推荐 RPC | `RecommendService.ListRecommendedVideos` |

## 5. 最终数据库缺口

| 表 | 用途 | 最终状态 |
| --- | --- | --- |
| `users` | 注册、登录、用户资料 | 必做 |
| `videos` | 发布、推荐排序、分页、删除权限和计数 | 必做 |
| `video_likes` | 点赞关系和幂等 | 必做 |
| `video_views` | 访问记录和推荐过滤 | 必做 |
| `request_logs` | 输入、输出、状态、耗时和错误 | 必做 |
| `comments` | 评论列表和发表评论 | 最终演示必做 |

明确不做：

| 项 | 最终结论 |
| --- | --- |
| `auth_tokens` | 不创建；退出由客户端删除 token |
| `upload_objects` | 不作为 P0；本地 `uploads/` 不需要上传凭证表 |
| `POST /media-upload-tokens` | Bonus，不属于主线 |
| 独立 `GET /metrics` | Bonus；使用 `request_logs` 聚合展示监控能力 |

## 6. 日志、安全和 RPC 关键缺口

| 领域 | 最终必须补齐 |
| --- | --- |
| 请求日志 | `requestId`、`userId`、`method`、`path`、`query`、`request_body`、`response_body`、`status_code`、`business_code`、`duration_ms`、`error_message`、`created_at` |
| 脱敏 | password、token 等敏感字段不可明文写日志；文件只记录摘要 |
| 监控展示 | 从 `request_logs` 聚合请求数、错误数、平均耗时、最大耗时 |
| health | `GET /health` 检查 API Server、MySQL 和 gRPC Recommend Service |
| 权限 | 所有业务接口统一鉴权；发布、点赞、访问、评论绑定当前用户；删除校验作者 |
| RPC | API Server 推荐接口只能经 gRPC 获取推荐 videoIds |

## 7. 优先级结论

| 顺序 | 内容 |
| --- | --- |
| 1 | T01 + T02：Java 17、Spring Boot、Maven、gRPC、MySQL 8、`uploads/` 和 `sql/schema.sql` 基础 |
| 2 | 统一响应、日志、账号和鉴权 |
| 3 | 发布、我的视频分页、删除权限、点赞、访问记录 |
| 4 | gRPC 推荐和 REST 推荐流 |
| 5 | health、核心测试和权限/日志测试 |
| 6 | 评论列表、发表评论及评论测试，最终联调前必须完成 |
| 7 | Android 两条主要场景联调 |
| 8 | 作业评分点验收、README 和全部答辩提交材料 |

收藏、关注、分享、消息、搜索、对象存储、预签名上传、独立 metrics 接口和复杂推荐算法不进入必做主线。
