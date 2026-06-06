# 开发进度记录

日期：2026-06-06

## 当前事实来源

本文件基于当前仓库文件、后端代码结构、`sql/schema.sql`、Maven 模块和规划文档整理。若规划文档与代码实现不一致，本文只记录当前实现状态，不修改规划范围。

## 已完成任务

| 任务 | 状态 | 当前证据 |
| --- | --- | --- |
| T01 + T02 后端基础结构与数据库 schema | 已完成 | 根目录 Maven 聚合项目、`backend/api-server`、`backend/recommend-service`、`sql/schema.sql` 已存在。 |
| T03 + T04 统一响应、错误码、requestId、请求日志 | 已完成 | API Server 中已有统一响应、错误码、全局异常处理、requestId filter 和 request log filter。 |
| T05 注册 | 已完成 | `POST /api/v1/auth/register` 已实现，使用 BCrypt 写入 `users.password_hash`。 |
| T06 登录 | 已完成 | `POST /api/v1/auth/login` 已实现，复用 HMAC token 签发。 |
| T07 退出和鉴权基础能力 | 已完成 | `POST /api/v1/auth/logout` 已实现，Bearer Token 鉴权基础能力已具备。 |
| T08 当前用户信息 | 已完成 | `GET /api/v1/me` 已实现，返回 profile、videoCount、likedCount。 |
| T09 本地 uploads 存储基础能力 | 已完成 | 已有 uploads 配置、静态资源映射和本地存储组件。 |
| T10 发布视频 | 已完成 | `POST /api/v1/videos` 已实现，支持 multipart 上传和开发期 JSON `videoUrl` 分支。 |
| T11 我的视频分页 | 已完成 | `GET /api/v1/me/videos` 已实现，只返回当前用户未删除视频，支持 cursor + limit。 |
| T12 删除视频权限控制 | 已完成 | `DELETE /api/v1/videos/{videoId}` 已实现，支持所有权校验、软删除和重复删除幂等。 |

## 当前可运行命令

```bash
mvn -q test
mvn -pl backend/api-server spring-boot:run
mvn -pl backend/recommend-service spring-boot:run
git diff --check
git diff --name-only -- frontend
```

说明：recommend-service 当前只是模块骨架，尚未实现 gRPC proto 和推荐服务逻辑。

## 已实现接口

| 方法和路径 | 鉴权 | 状态 |
| --- | --- | --- |
| `POST /api/v1/auth/register` | 不需要 | 已实现 |
| `POST /api/v1/auth/login` | 不需要 | 已实现 |
| `POST /api/v1/auth/logout` | 需要 Bearer Token | 已实现 |
| `GET /api/v1/me` | 需要 Bearer Token | 已实现 |
| `POST /api/v1/videos` | 需要 Bearer Token | 已实现 |
| `GET /api/v1/me/videos` | 需要 Bearer Token | 已实现 |
| `DELETE /api/v1/videos/{videoId}` | 需要 Bearer Token | 已实现 |

## 未完成任务

| 任务 | 接口或模块 | 说明 |
| --- | --- | --- |
| T13 点赞 | `PUT/DELETE /api/v1/videos/{videoId}/likes/me` | 需保证幂等并维护 like_count。 |
| T14 访问记录 | `POST /api/v1/videos/{videoId}/views/me` | 推荐过滤依赖该表。 |
| T15-T17 推荐服务和推荐流 | gRPC proto、Recommend Service、`GET /api/v1/feeds/recommended/videos` | 必须按 like_count desc、created_at desc、id desc 排序，并过滤已访问视频。 |
| T18 health | `GET /api/v1/health` | 需检查 API Server、MySQL、gRPC Recommend Service。 |
| T23-T24 评论闭环 | `GET/POST /api/v1/videos/{videoId}/comments` | 属于最终演示闭环必做，排在核心视频链路之后。 |
| 前端真实联调 | Android 调用真实后端 API | 当前未确认已接入真实后端。 |
| 交付材料 | README、需求文档、技术设计文档、测试文档、答辩 PPT、团队分工、成员评分表、演示视频、公开 Git 地址 | README 已更新，其余仍需补齐。 |

## 风险点

- gRPC 推荐服务目前只有模块边界，尚未具备 RPC 契约和推荐结果输出能力。
- 推荐链路还依赖点赞、访问记录和 gRPC 推荐服务，必须按任务顺序补齐。
- 评论不是 Bonus，必须在最终前端联调和演示前完成。
- 前端当前不应被视为已完成真实 API 联调。
- 请求日志已经具备基础能力，但后续新增接口需要继续保证敏感字段脱敏和耗时记录。
- 本地 uploads 存储已接入发布视频接口，后续真实联调仍需本地 MySQL 8 和可访问的 uploads 目录。
- 当前工作区存在视频管理相关未提交代码变更，后续提交或验收前需要统一检查。

## 下一步建议

下一项编码任务建议是 T13 点赞 / 取消点赞：

- 实现 `PUT /api/v1/videos/{videoId}/likes/me`。
- 实现 `DELETE /api/v1/videos/{videoId}/likes/me`。
- 需要 Bearer Token 鉴权。
- 写入 `video_likes` 表并维护 `videos.like_count`。
- 点赞和取消点赞都需要幂等。
- 不实现访问记录、推荐流、评论或 Bonus。
- 验收重点是计数一致性、重复调用幂等、统一响应、requestId、请求日志和不修改 `frontend/`。
