# Scope Final

## Context

本规划基于 `AGENTS.md`、`docs/gap-analysis.md`、`frontend/docs/API_DESIGN.md`、当前 Android Compose Demo 源码和课程大作业要求。

当前目标不是复刻完整抖音，而是交付“简易版抖音 / 视频流推荐系统”的课程必需闭环：账号 -> 发布视频 -> 推荐视频 -> 访问记录过滤 -> 点赞 -> 我的视频分页 -> 删除权限 -> 日志监控 -> 评论演示闭环 -> 文档测试。

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
| 数据库 | MySQL 8 表设计 | 核心表为 `users`、`videos`、`video_likes`、`video_views`、`request_logs`；最终演示必做表为 `comments` |
| 日志 | 记录输入、输出、耗时 | 每个用户请求记录 requestId、userId、接口、请求、响应、状态码、耗时 |
| 监控 | `GET /health` | 返回 API Server、MySQL 8、gRPC Recommend Service 健康状态 |
| 安全 | 账号、密码、权限 | 密码哈希存储；删除/发布/点赞/浏览记录均绑定当前用户 |
| 测试 | 核心测试文档和用例 | 覆盖正常、异常、权限、推荐规则、日志、健康检查 |
| 交付材料 | README、需求、技术设计、测试、PPT、分工、内部分数评定表、演示视频、公开 Git 地址 | README 写部署步骤；最终代码打包；材料按课程提交 |

## P0-lite / 最终演示必做（仅实施顺序后置）

| 功能 | 接口 / 表 | 顺序 |
| --- | --- | --- |
| 获取评论列表 | `GET /api/v1/videos/{videoId}/comments` | 核心 P0 完成后实现，最终联调前完成 |
| 发表评论 | `POST /api/v1/videos/{videoId}/comments` | 核心 P0 完成后实现，最终联调前完成 |
| 评论表 | `comments` | 写入 `sql/schema.sql`，属于最终交付 schema |

P0-lite 仅表示开发顺序后置，不表示可选。评论列表和发表评论属于课程指定主要演示场景，必须在前端最终联调、测试验收和演示录屏前完成。

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

## 课程评分点映射

| 作业要求 | 项目实现方案 | 对应接口 | 对应表 | 对应测试 | 演示点 |
| --- | --- | --- | --- | --- | --- |
| 按点赞数最高推荐 | gRPC 服务按 `like_count DESC, created_at DESC, id DESC` 返回 videoIds | `GET /api/v1/feeds/recommended/videos`；`RecommendService.ListRecommendedVideos` | `videos` | R01、R02、R05、R08 | 推荐列表展示确定排序，说明 REST 经 gRPC 获取结果 |
| 访问过不再推荐 | 视频开始展示时幂等写访问记录，推荐查询排除当前用户已访问视频 | `POST /api/v1/videos/{videoId}/views/me` | `video_views`、`videos` | N13、R03、R04 | 刷到视频后刷新推荐流，该视频不再出现 |
| 视频上下滑动 | 保留 Android Compose `VerticalPager`，接入真实推荐流 | 无独立接口，消费推荐列表 | 无 | F01 | 在推荐页查看上一个/下一个视频 |
| 视频点赞 | 当前用户点赞关系唯一，事务维护 `like_count` | `PUT/DELETE /api/v1/videos/{videoId}/likes/me` | `video_likes`、`videos` | N10、N11、I01、I02 | 点赞状态和计数更新 |
| 评论演示闭环 | 核心 P0 后实现评论列表和发表评论，最终演示前完成 | `GET/POST /api/v1/videos/{videoId}/comments` | `comments`、`videos` | C01-C06、F01 | 查看评论并提交评论 |
| 发布视频 | multipart 上传到 API Server 本地 `uploads/` 并写视频记录 | `POST /api/v1/videos` | `videos` | N05、E05-E07 | 登录后发布视频 |
| 我的视频分页 | 基于当前用户和 cursor/limit 分页 | `GET /api/v1/me/videos` | `videos` | N07、P06 | 我的列表翻页且无重复 |
| 删除我的视频 | 校验 `author_id == currentUser.id`，重复删除幂等 200 | `DELETE /api/v1/videos/{videoId}` | `videos` | N08、N09、P05 | 删除自己的成功，删除他人的失败 |
| 登录、注册、取消登录 | 密码哈希、Bearer token；退出由客户端删除 token，服务端返回成功 | `POST /api/v1/auth/register`、`login`、`logout`；`GET /me` | `users` | N01-N04、E01-E03 | 注册/登录后进入业务页，退出后清除登录态 |
| 数据库设计 | MySQL 8，`sql/schema.sql` 初始化，索引和代码校验优先 | 由各业务接口使用 | 六张最终表 | D01-D04 | 展示 ER/表结构和关键索引 |
| 视频存储设计 | API Server 本地 `uploads/`，不做对象存储和预签名 URL | `POST /api/v1/videos` | `videos.video_url` | N05、E06、E07、S01 | 发布后文件可播放 |
| 日志、输入输出、耗时 | 全局中间件写 `request_logs`，敏感字段脱敏，可按 path 聚合耗时 | 覆盖全部 REST 接口 | `request_logs` | L01-L07 | 展示请求输入、输出、状态、耗时和聚合结果 |
| 集成健康检查 | 检查 API Server、MySQL、gRPC Recommend Service | `GET /api/v1/health` | 无 | H01-H04 | 停启依赖展示组件状态 |
| 安全与权限 | 统一鉴权、当前用户绑定、资源归属校验、日志脱敏 | 所有需登录接口 | `users`、业务关系表 | P01-P08、L04 | 未登录 401、越权删除 403 |
| 主端通过 RPC 访问推荐 | API Server 的推荐 REST 接口必须调用 gRPC | REST 推荐接口；推荐 RPC | `videos`、`video_views` | R08、E10 | 日志或测试证明 gRPC 调用发生 |
| 文档与项目管理 | 补齐公开 Git、`.gitignore`、README 和全部课程材料 | 不适用 | 不适用 | A01-A09 | PPT 展示仓库、文档与测试结果 |

## 前端接入范围

| 当前能力 | 最终必须接入 | 非主线 |
| --- | --- | --- |
| Android Compose 已有上下滑播放、本地点赞、评论弹层、发布和个人页 Demo | 真实注册/登录/退出、推荐流、点赞、访问记录、multipart 发布、我的视频分页、删除权限、评论列表和发表评论 | 收藏、关注、分享、消息、搜索只保留为非主线增强或本地展示，不影响主线验收 |

## 交付物清单

| 交付物 | 验收要求 |
| --- | --- |
| 公开 Git 项目地址 | 仓库可公开访问；有完整 `.gitignore`，忽略 `target/`、IDE、编译产物和本地上传文件 |
| 最终代码包与 `README.md` | README 写明 Java、MySQL、配置、schema、API Server、Recommend Service、Android 端启动步骤 |
| 需求文档 | 覆盖课程评分点、用户场景、范围和验收标准 |
| 技术设计文档 | 覆盖架构、REST、gRPC、数据库、存储、日志、安全 |
| 测试文档 | 包含 case 设计逻辑、case 列表和执行结果 |
| 答辩 PPT | 支持 8 分钟讲解，突出评分点、架构、测试和结果 |
| 团队分工文档 | 写明每位成员工作内容和工作量占比 |
| 项目成员内部分数评定表 | 按课程模板完成组内评分 |
| 演示视频 | 约 2 分钟，完整覆盖两个主要场景及关键后台能力 |

## 最小演示闭环

1. 场景一：推荐视频 / 上下刷视频 -> 点赞 -> 查看评论 -> 提交评论。
2. 场景二：注册或登录 -> 发布视频到本地 `uploads/` -> 分页查看我的视频 -> 删除自己的视频。
3. 推荐页通过 Spring Boot API Server 调 gRPC Recommend Service，按 `like_count DESC, created_at DESC, id DESC` 返回未看过的视频。
4. 视频开始展示时写入 `video_views`，再次刷新推荐流不再出现该视频。
5. 删除他人视频返回 403，重复删除自己的视频返回 200。
6. 日志中可查到每个接口的输入、输出、耗时、状态码、businessCode、userId 和 requestId。
7. `GET /health` 可查看 API Server、MySQL 8、gRPC Recommend Service 状态。
