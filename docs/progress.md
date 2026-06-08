# 开发进度记录

日期：2026-06-08

## 当前事实来源

本文件基于当前仓库文件、后端代码结构、`sql/schema.sql`、Maven 模块和规划文档整理。若规划文档与代码实现不一致，本文只记录当前实现状态，不修改规划范围。

## 全部任务进度总览

### 后端基础阶段（M1）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T01 + T02 后端基础结构与数据库 schema | ✅ 已完成 | — | Maven 聚合项目、`backend/api-server`、`backend/recommend-service`、`sql/schema.sql` 含 6 张表。 |
| T03 + T04 统一响应、错误码、requestId、请求日志 | ✅ 已完成 | — | 统一响应结构、错误码枚举、全局异常处理、requestId filter、请求日志中间件。 |
| T05 注册 | ✅ 已完成 | — | `POST /api/v1/auth/register`，BCrypt 哈希存储。 |
| T06 登录 | ✅ 已完成 | — | `POST /api/v1/auth/login`，HMAC token 签发。 |
| T07 退出和鉴权基础能力 | ✅ 已完成 | — | `POST /api/v1/auth/logout` + Bearer Token 解析过滤。 |
| T08 当前用户信息 | ✅ 已完成 | — | `GET /api/v1/me`，返回 profile、videoCount、likedCount。 |

### 视频管理阶段（M2）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T09 本地 uploads 存储基础能力 | ✅ 已完成 | — | uploads 配置、静态资源映射、LocalUploadStorageService。 |
| T10 发布视频 | ✅ 已完成 | — | `POST /api/v1/videos`，支持 multipart 上传和开发期 JSON `videoUrl`。 |
| T11 我的视频分页 | ✅ 已完成 | — | `GET /api/v1/me/videos`，cursor + limit 分页。 |
| T12 删除视频权限控制 | ✅ 已完成 | — | `DELETE /api/v1/videos/{videoId}`，所有权校验、软删除、幂等。 |
| T13 点赞 / 取消点赞 | ✅ 已完成 | **成员 A** | `PUT/DELETE /api/v1/videos/{videoId}/likes/me`，INSERT IGNORE 幂等，维护 like_count。 |
| T14 访问记录 | ✅ 已完成 | **成员 A** | `POST /api/v1/videos/{videoId}/views/me`，ON DUPLICATE KEY 幂等，首次访问递增 view_count。 |

### 推荐闭环阶段（M3）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T15 gRPC 推荐契约 | ⬜ 未完成 | 成员 B | proto 文件和 gRPC 生成配置待实现。 |
| T16 推荐规则 | ⬜ 未完成 | 成员 B | Recommend Service 推荐排序和过滤逻辑待实现。 |
| T17 推荐流 REST | ⬜ 未完成 | 成员 B | `GET /api/v1/feeds/recommended/videos` 待实现，需通过 gRPC 调用。 |
| T18 健康检查 | ✅ 已完成 | **成员 A** | `GET /api/v1/health`，实际检查 API Server、MySQL 8、gRPC Recommend Service。 |

### 测试阶段

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T19 核心接口测试 | ✅ 已完成 | **成员 A** | LikeControllerTest (12)、ViewControllerTest (8)、HealthControllerTest (6)，覆盖正常/异常/幂等。 |
| T20 推荐规则测试 | ⬜ 未完成 | 成员 B | 依赖 T15-T17 完成后编写。 |
| T21 权限测试 | ✅ 已完成 | **成员 A** | 点赞/取消点赞/访问记录未登录 → 401，无效 token → 401。 |
| T22 日志测试 | ✅ 已完成 | **成员 A** | requestId、userId、path、statusCode、businessCode、durationMs 记录验证通过。 |

### 评论阶段（M4，最终演示必做）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T23 获取评论列表 | ⬜ 未完成 | 成员 C | `GET /api/v1/videos/{videoId}/comments` 待实现。 |
| T24 发表评论 | ⬜ 未完成 | 成员 C | `POST /api/v1/videos/{videoId}/comments` 待实现。 |
| T25 评论测试 | ⬜ 未完成 | 成员 C | 依赖 T23-T24 完成后编写。 |

### 前端联调阶段（M5）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T26 前端接入账号和推荐流 | ⬜ 未完成 | 成员 C | Android 前端已有代码骨架（`frontend/app/`），但当前使用 `MockRepository` 本地 mock 数据，未接入真实后端 API。 |
| T27 前端接入发布、我的视频、删除和评论 | ⬜ 未完成 | 成员 C | 同上，前端发布/视频管理/评论功能仍依赖 mock。 |

### 交付阶段（M6-M7）

| 任务 | 状态 | 负责人 | 当前证据 |
| --- | --- | --- | --- |
| T28 作业评分点验收检查 | ⬜ 未完成 | 成员 C | 评分点矩阵待构建。 |
| T29 完善 README 和提交文档 | ⬜ 未完成 | 成员 C | README 已有基础内容，需求/技术设计/测试文档待补齐。 |
| T30 答辩 PPT、团队分工、成员评分表、演示视频 | ⬜ 未完成 | 成员 C | 待准备。 |
| T31 最终提交检查 | ⬜ 未完成 | 成员 C | 待最终合并后执行。 |

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
| `POST /api/v1/auth/register` | 不需要 | ✅ |
| `POST /api/v1/auth/login` | 不需要 | ✅ |
| `POST /api/v1/auth/logout` | 需要 Bearer Token | ✅ |
| `GET /api/v1/me` | 需要 Bearer Token | ✅ |
| `POST /api/v1/videos` | 需要 Bearer Token | ✅ |
| `GET /api/v1/me/videos` | 需要 Bearer Token | ✅ |
| `DELETE /api/v1/videos/{videoId}` | 需要 Bearer Token | ✅ |
| `PUT /api/v1/videos/{videoId}/likes/me` | 需要 Bearer Token | ✅ |
| `DELETE /api/v1/videos/{videoId}/likes/me` | 需要 Bearer Token | ✅ |
| `POST /api/v1/videos/{videoId}/views/me` | 需要 Bearer Token | ✅ |
| `GET /api/v1/health` | 不需要 | ✅ |

## 未实现接口

| 方法和路径 | 负责人 | 状态 |
| --- | --- | --- |
| `GET /api/v1/feeds/recommended/videos` | 成员 B | ⬜ |
| `GET /api/v1/videos/{videoId}/comments` | 成员 C | ⬜ |
| `POST /api/v1/videos/{videoId}/comments` | 成员 C | ⬜ |

## 前端现状

`frontend/` 目录包含完整的 Android 项目（Kotlin + Jetpack Compose），包含推荐流、发布、登录等 UI。但当前使用 `MockRepository` 本地假数据，尚未接入真实后端 API。成员 C 负责 T26-T27 前端联调。

## 里程碑进度

| 里程碑 | 状态 | 完成度 |
| --- | --- | --- |
| M1 基础后端 | ✅ 完成 | 6/6 (T01-T08) |
| M2 视频管理 | ✅ 完成 | 6/6 (T09-T14) |
| M3 推荐闭环 | 🔄 进行中 | 1/4 (T18 ✅, T15-T17 待成员B) |
| M4 评论闭环 | ⬜ 未开始 | 0/3 (T23-T25) |
| M5 前端联调 | ⬜ 未开始 | 0/2 (T26-T27) |
| M6 评分点验收 | ⬜ 未开始 | 0/1 (T28) |
| M7 交付 | ⬜ 未开始 | 0/3 (T29-T31) |

## 风险点

- gRPC 推荐服务目前只有模块边界，尚未具备 RPC 契约和推荐结果输出能力。
- 推荐链路依赖点赞（T13 ✅）、访问记录（T14 ✅）和 gRPC 推荐服务（T15-T17 ⬜）。
- 前端目前使用 MockRepository 本地假数据，T26-T27 联调工作由成员 C 负责。
- 评论不是 Bonus，必须在最终前端联调和演示前完成（成员 C 负责）。
- 请求日志已具备基础能力，后续新增接口需继续保证敏感字段脱敏和耗时记录。
- 本地 uploads 存储已接入，后续联调仍需本地 MySQL 8 和可访问的 uploads 目录。

## 下一步建议

1. **成员 B** 推进 T15 gRPC 契约 + T16 推荐规则 + T17 推荐流 REST（依赖 T13/T14 已就绪）。
2. **成员 C** 并行推进 T23-T25 评论闭环（接口实现 + 测试）。
3. 成员 B 补 T20 推荐规则测试后，成员 C 推进 T26-T27 前端真实 API 联调。
4. 全部功能完成后，成员 C 牵头 T28-T31 验收和交付材料。

后续团队分工详见 `docs/team-task-assignment.md`。
