# 文档评分材料审查报告：代码事实优先版

> 审查基线：`main` / `7fc66d4`。本次仅静态审查并更新文档，未运行构建、测试、服务、数据库、Android 或 Web，因此“代码存在”不等于“本次运行验证通过”。

## 1. 结论摘要

- 后端已实现账号、推荐、访问过滤、点赞、评论、multipart 发布、我的视频、删除权限、日志和健康检查；推荐 REST 接口通过 gRPC 访问 Recommend Service。
- Android 主入口已切换为 `RealDouyinApp`，核心两条演示流程均存在真实 API 调用点；旧报告关于“仅评论接入、其他功能使用 MockRepository”的判断已过期。
- 新增 `web/` React + Vite 客户端，覆盖登录注册、推荐、访问上报、点赞、评论、发布、我的视频和删除，可作为本地补充演示端。
- 新增 API Server/Recommend Service Dockerfile、Docker Compose、`.env.example`、离线镜像与服务器部署手册；仍未发现 Nginx、CI/CD、TLS 终止或长期生产运维实现。
- 三份正式提交文档已经存在并按最新代码更新，原先“缺少独立需求/技术设计文档”的扣分点已消除。
- 需求方案当前保守预计 **13/15**：主要风险是未附本次端到端运行记录、非功能指标缺少实测数据。
- 技术设计方案当前保守预计 **13/15**：主要风险是缺少可复核架构/时序图、容器部署未在本次审查中运行验证、测试结果未重跑。
- 最优先补救是执行并记录两条端到端演示、统一测试结果口径、补一张运行架构图和两张核心流程时序图。

## 2. 代码事实总览

### 2.1 后端真实能力

| 模块 | 代码位置 | 实际能力 | 对外接口 | 是否有测试 | 备注 |
| -- | ---- | ---- | ---- | ----- | -- |
| 认证 | `backend/api-server/.../auth` | 注册、登录、退出、BCrypt、HMAC Token、Bearer 鉴权 | `/auth/register`、`/auth/login`、`/auth/logout` | 有 | 退出由客户端删除 token |
| 用户 | `.../user` | 当前用户资料、视频数、点赞数 | `GET /me` | 有 | 需 Bearer Token |
| 视频 | `.../video` | multipart/JSON 发布、我的视频分页、软删除、归属校验 | `POST /videos`、`GET /me/videos`、`DELETE /videos/{id}` | 有 | 文件存本地 uploads |
| 互动 | `VideoController`、`VideoService`、`VideoRepository` | 点赞/取消、访问记录、实时计数、幂等 | likes、views 接口 | 有 | 关系表唯一约束 |
| 评论 | `.../comment` | 评论列表、发表评论、游标分页、实时评论数 | GET/POST comments | 有 | 新增 `CommentServiceTest` |
| 推荐 | `.../feed`、`backend/recommend-service` | REST 调 gRPC；排序、过滤已访问、游标分页 | recommended feed；gRPC RPC | 有 | 规则型推荐，不是 ML |
| 日志/健康 | `.../logging`、`.../health` | 输入输出、状态、耗时、脱敏；依赖健康检查 | `GET /health` | 有 | 日志落 `request_logs` |

### 2.2 前端真实能力

| 页面/模块 | 代码位置 | 实际展示内容 | 实际调用接口 | 是否完整接入 | 备注 |
| ----- | ---- | ------ | ------ | ------ | -- |
| Android 认证 | `RealDouyinApp.kt:107-220` | 注册/登录表单 | register、login | 是 | token 存 SharedPreferences |
| Android 推荐 | `RealDouyinApp.kt:287-357,517` | 垂直推荐流、播放、点赞 | feed、view、like/unlike | 是（代码） | 本次未运行验证 |
| Android 评论 | `RealDouyinApp.kt:423-450` | 评论列表与提交 | GET/POST comments | 是（代码） | 登录后真实调用 |
| Android 发布 | `RealDouyinApp.kt:823-844` | 选择演示素材、填写标题、上传 | multipart videos | 是（代码） | 素材先转换为本地文件 |
| Android 个人页 | `RealDouyinApp.kt:306-318,374-401` | 用户资料、分页作品、删除、退出 | me、me/videos、delete、logout | 是（代码） | Android 为主演示端 |
| Web 认证与导航 | `web/src/App.tsx`、`AuthModal.tsx` | 登录注册、页面切换 | auth、me、logout | 是（代码） | React + Vite |
| Web 推荐与评论 | `RecommendPage.tsx`、`CommentDrawer.tsx` | 推荐、访问、点赞、评论 | feed、view、like、comments | 是（代码） | 补充演示端 |
| Web 发布与个人页 | `UploadPage.tsx`、`ProfilePage.tsx` | multipart 发布、作品分页、删除 | videos、me/videos、delete | 是（代码） | 端口 5173 |

### 2.3 数据库真实 schema

| 表名 | 来源文件 | 核心字段 | 支撑的业务 | 备注 |
| -- | ---- | ---- | ----- | -- |
| `users` | `sql/schema.sql` | username、password_hash、nickname、status | 注册登录、资料 | username 唯一 |
| `videos` | 同上 | author_id、video_url、visibility、status、deleted_at | 发布、推荐、我的视频、删除 | 软删除 |
| `video_likes` | 同上 | user_id、video_id | 点赞、推荐实时计数 | 联合唯一 |
| `video_views` | 同上 | user_id、video_id、source、watch_duration_ms | 访问幂等、推荐过滤 | 联合唯一 |
| `comments` | 同上 | video_id、author_id、content、deleted_at | 评论闭环 | 时间游标索引 |
| `request_logs` | 同上 | request_id、path、body、status、business_code、duration_ms | 审计和耗时 | request_id 唯一 |

### 2.4 部署与配置事实

| 配置项 | 文件位置 | 实际内容 | 支撑的部署能力 | 备注 |
| --- | ---- | ---- | ------- | -- |
| API 配置 | `backend/api-server/.../application.yml` | MySQL、端口、Token、uploads、gRPC | 本地/环境变量运行 | API 默认 8080 |
| 推荐配置 | `backend/recommend-service/.../application.yml` | MySQL、管理端口、gRPC 端口 | 推荐服务运行 | gRPC 默认 9090 |
| 容器镜像 | `Dockerfile.api-server`、`Dockerfile.recommend-service` | 两个后端镜像 | 容器化启动 | 代码事实已存在 |
| Compose | `deploy/simple-douyin/docker-compose.yml` | MySQL + 推荐 + API | 答辩服务器编排 | API 映射 18090 |
| 离线部署 | `build-images-local.sh`、`README-deploy.md`、`DEPLOYMENT-HANDOFF.md` | 构建、导出、导入、验证、回滚 | 无法稳定访问 Docker Hub 的服务器 | 本次未执行 |
| Web 开发配置 | `web/vite.config.ts`、`.env.example` | 5173；代理 `/api`、`/uploads` | 本地 Web 演示 | 默认目标为演示后端 |
| Nginx/CI | 全仓库检索 | 未找到 | 不支撑 | 不得写成已完成 |

## 3. REST / gRPC 接口证据清单

| 类型 | 方法 | 路径/服务名 | 请求对象 | 响应对象 | 代码位置 | 支撑的功能 |
| -- | -- | ------ | ---- | ---- | ---- | ----- |
| REST | POST | `/api/v1/auth/register` | RegisterRequest | AuthResponse | `AuthController.java` | 注册 |
| REST | POST | `/api/v1/auth/login` | LoginRequest | AuthResponse | 同上 | 登录 |
| REST | POST | `/api/v1/auth/logout` | 无 | LogoutResponse | 同上 | 退出 |
| REST | GET | `/api/v1/me` | 无 | MeResponse | `UserController.java` | 当前用户 |
| REST | GET | `/api/v1/feeds/recommended/videos` | cursor、limit | RecommendedFeedResponse | `FeedController.java` | 推荐流 |
| REST | POST | `/api/v1/videos/{id}/views/me` | ViewRequest | ViewResponse | `VideoController.java` | 访问记录 |
| REST | PUT/DELETE | `/api/v1/videos/{id}/likes/me` | path id | LikeResponse | 同上 | 点赞/取消 |
| REST | POST | `/api/v1/videos` | multipart 或 JSON | CreateVideoResponse | 同上 | 发布 |
| REST | GET | `/api/v1/me/videos` | cursor、limit | MyVideosResponse | 同上 | 我的列表 |
| REST | DELETE | `/api/v1/videos/{id}` | path id | DeleteVideoResponse | 同上 | 删除本人视频 |
| REST | GET/POST | `/api/v1/videos/{id}/comments` | cursor/limit 或 PostCommentRequest | 评论列表/评论响应 | `CommentController.java` | 评论闭环 |
| REST | GET | `/api/v1/health` | 无 | HealthResponse | `HealthController.java` | 集成健康检查 |
| gRPC | RPC | `RecommendService.ListRecommendedVideos` | ListRecommendedVideosRequest | ListRecommendedVideosResponse | `recommend.proto`、`RecommendServiceImpl.java` | 推荐排序与过滤 |

## 4. 需求方案评分材料

| 需求模块 | 代码事实依据 | 可写入需求方案的内容 | 是否充分 | 还缺什么 |
| ---- | ------ | ---------- | ---- | ---- |
| 用户角色 | 认证、当前用户、作者归属代码 | 游客（仅注册登录）、登录用户、视频作者 | 充分 | 无管理员角色，不应虚构 |
| 场景一 | Android/Web + feed/like/comments | 推荐/刷视频→点赞→评论列表→发表评论 | 基本充分 | 补实际录屏/验收记录 |
| 场景二 | Android/Web + auth/upload/me/delete | 登录注册→发布→我的列表→删除 | 基本充分 | 补实际录屏/验收记录 |
| 功能需求 | Controller、Service、两端调用点 | 账号、推荐、互动、视频管理、日志、健康 | 充分 | 收藏等明确排除 |
| 非功能需求 | 安全、分页、上传限制、日志、Compose | 安全、容量限制、可维护性、演示部署 | 基本充分 | 无压力测试和 SLA 数据 |
| 验收标准 | `docs/test-plan.md`、后端测试源码 | 正常、异常、权限、幂等、推荐规则 | 基本充分 | 本次未重跑，执行结果待确认 |

## 5. 技术设计方案评分材料

| 设计项 | 代码事实依据 | 可写入技术设计方案的内容 | 是否充分 | 还缺什么 |
| --- | ------ | ------------ | ---- | ---- |
| 系统架构 | Android、Web、REST、gRPC、MySQL | 两客户端→API Server→gRPC/Repository→MySQL | 充分 | 补图形化部署图 |
| 前端架构 | `RealDouyinApp`、`web/src` | Compose/Retrofit 与 React/Vite 两种客户端 | 充分 | 无前端自动化测试 |
| 后端分层 | Controller/Service/Repository | 分层职责、事务和错误处理 | 充分 | 无需虚构 JPA |
| 数据库 | schema + Repository SQL | 六表、索引、幂等、软删除、游标 | 充分 | 无 migration/物理外键 |
| 接口与安全 | Controller/DTO/Filter | 统一响应、Bearer、归属校验、脱敏、上传检查 | 充分 | 无 refresh/revoke/TLS |
| 核心流程 | 两端调用点 + Service | 两条演示闭环与异常分支 | 基本充分 | 补时序图 |
| 推荐 RPC | proto/client/server/repository | RPC 契约、排序、访问过滤 | 充分 | plaintext，无重试/服务发现 |
| 部署 | Dockerfile/Compose/手册 | 本地运行和离线答辩部署 | 基本充分 | 本次未运行；无 Nginx/CI |
| 测试 | 18 个后端测试类 | Controller/Service/Repository/Storage | 基本充分 | 无本次执行证据、无前端 E2E |
| 限制 | 配置与代码 | 单机文件、规则推荐、课程级安全 | 充分 | 应保留诚实边界 |

## 6. 文档与代码冲突清单

| 文档位置 | 文档说法 | 代码事实 | 判断 | 建议处理 |
| ---- | ---- | ---- | -- | ---- |
| `docs/scope-final.md` 前端接入范围 | 仍以“已有 Demo，最终必须接入”描述 | `RealDouyinApp` 已接核心 API | 代码已实现，文档过期 | 标为规划基线，不作为完成状态来源 |
| `docs/api-contract-final.md` 架构 | 只列 Android 客户端 | 另有 Web 客户端调用相同 REST API | 代码存在，但文档缺失 | 加注 Web 为补充端，契约本身仍有效 |
| `docs/gap-analysis.md` | 记录早期缺口 | 核心后端和客户端调用点已完成 | 文档只是规划，不应写成已完成 | 标注历史分析 |
| `README.md` 下一步计划 | 仍列评论和前端联调为下一步 | 评论及 Android/Web 接入代码已存在 | 文档描述不准确 | 下一步改为验收、PPT、录屏 |
| `README.md` 测试结果 | 声称 112 个全部通过 | 源码新增 `CommentServiceTest`；本次未执行 | 文档描述不准确 | 重新运行后以 Surefire 汇总为准 |
| 旧审查报告 | Android 仅评论接 API；无 Docker/Compose/Web | 最新提交已补齐上述代码 | 代码已实现，文档过期 | 本文件已覆盖旧判断 |
| `frontend/docs/API_DESIGN.md` | 前端草拟契约 | 课程契约和 Controller/DTO 才是事实来源 | 文档只是规划，不应写成已完成 | 仅作历史参考 |

## 7. 当前预计得分

| 评分项 | 满分 | 当前预计得分 | 得分依据 | 主要扣分点 | 补救优先级 |
| ------ | -: | -----: | ---- | ----- | ----- |
| 需求方案 | 15 | **13** | 正式文档已覆盖角色、两条场景、模块、非功能、范围和限制；客户端/后端均有代码证据 | 未附本次端到端验收记录；性能指标无实测；部分旧规划文档仍冲突 | P0：补验收记录；P1：清理旧状态 |
| 技术设计方案 | 15 | **13** | 架构、前后端、REST、gRPC、数据库、安全、日志、部署和测试均有代码/配置证据 | 缺图形化架构/时序图；容器部署和测试未在本次复核；无前端 E2E | P0：补图与验证证据；P1：补 E2E |

当前合计保守预计：**26/30**。该分数评价当前文档材料，不代表课程教师最终评分。

## 8. 下一步补救方案

| 优先级 | 动作 | 目标文件 | 为什么优先 | 预计提升 |
| --- | -- | ---- | ----- | ---- |
| P0 | 按两条核心场景执行并记录日期、环境、步骤、结果、截图 | `docs/test-plan.md` 或独立测试报告 | 当前最大缺口是运行证据 | 约 +1～2 |
| P0 | 补总体部署图与两条核心流程时序图 | `docs/技术设计文档.md` | 让架构与 RPC 链路可快速评分 | 约 +0.5～1 |
| P0 | 重跑后端测试并统一 README/test-plan/scoring-matrix 数量 | 三份现有文档 | 消除“112 个通过”与新增测试源码冲突 | 降低可信度扣分 |
| P1 | 将 gap/scope/task-breakdown 标注为规划基线 | 各历史文档 | 防止教师把旧缺口当现状 | 约 +0.5 |
| P1 | 本地运行 Web 并确认后端关闭/开启两种表现 | `web/README.md` | Web 是新增补充演示端 | 降低演示风险 |

- `docs/需求文档.md`：已新建并更新，无需另建 `需求方案.md`，提交时统一使用一个正式名称即可。
- `docs/技术设计文档.md`：已新建并更新，无需再复制一份 `技术设计方案.md`。
- 可合并引用：scope/API/database/RPC/module 文档作为正式两份文档的详细附录。
- 应标记为规划或历史：`gap-analysis.md`、`task-breakdown.md`、`frontend/docs/API_DESIGN.md`。
- 不能写成已完成：Nginx、CI/CD、TLS、生产监控、对象存储、复杂推荐算法、长期生产 SLA，以及本次未执行的测试/部署结果。

## 9. 可直接生成正式文档的素材索引

| 正式文档章节 | 可引用代码/配置/测试位置 | 可写内容摘要 |
| ------ | ------------- | ------ |
| 项目目标与范围 | `docs/scope-final.md`、Controller、两端页面 | 两条闭环、P0/非主线边界 |
| Android 前端 | `MainActivity.kt`、`RealDouyinApp.kt`、`ApiRepository.kt` | 真实 REST 接入和垂直视频流 |
| Web 前端 | `web/src/App.tsx`、`pages`、`api.ts` | React 补充演示端及 API 调用 |
| REST API | 各 Controller/DTO | 14 类核心接口、统一响应 |
| 后端分层 | 各 Service/Repository | 校验、事务、幂等、分页、软删除 |
| 推荐 RPC | `recommend.proto`、gRPC client/server、RecommendRepository | 点赞排序、访问过滤、游标 |
| 数据库 | `sql/schema.sql`、Repository 测试 | 六表、索引、实时计数 |
| 存储与安全 | storage、security、logging | uploads、Bearer、BCrypt、脱敏 |
| 部署 | Dockerfile、Compose、`.env.example`、部署手册 | 本地与离线答辩部署拓扑 |
| 测试 | `backend/**/src/test`、`docs/test-plan.md` | 多层测试设计；结果需重跑确认 |

## 审查范围与未确认项

- 已读取：规定规划文档、三份正式文档、README、Android 主入口/Repository、Web 页面/API、后端模块与测试清单、schema、Docker/Compose 与部署手册。
- 未确认：当前所有测试是否通过、Compose 是否能在目标服务器启动、Android/Web 两条流程是否在当前环境端到端通过、服务器当前是否在线。
- 未运行：构建、测试、服务、数据库、Android、Web、Docker。
