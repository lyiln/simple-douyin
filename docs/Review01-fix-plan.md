# PR #2 Code Review 修复方案

> 审查文档：`docs/Review01.md` | 日期：2026-06-09 | 分支：`feature/member-a-like-view-health`

## 修复清单

| # | 等级 | 问题 | 修复方案 |
|---|------|------|----------|
| 1 | 🔴 | HealthService 每次请求创建/销毁 gRPC Channel | 新增 `GrpcConfig` 注入单例 `ManagedChannel` Bean |
| 2 | 🔴 | gRPC 检查用 `awaitTermination` 语义错误 | 改用轮询 `ConnectivityState.READY` + 2s 超时 |
| 3 | 🔴 | 幂等性测试无效（mock 恒定返回值） | Controller 测试改为验证 mock 调用次数；新增 `VideoRepositoryTest` |
| 4 | 🔴 | `like_count`/`view_count` 反规范化计数器漂移 | `findLikeCount()`/`findViewCount()` 改为 `SELECT COUNT(*)` 实时计算 |
| 5 | 🟡 | DOWN 时 HTTP 仍返回 200 | `HealthController` 返回 503 |
| 6 | 🟡 | `videoExists` 与操作之间 TOCTOU | `INSERT_LIKE_SQL` 加 `FROM videos WHERE id=? AND deleted_at IS NULL` |
| 7 | 🟡 | source 默认值 Service 和 Repository 重复 | 移除 Repository 层默认值，仅 Service 层处理 |
| 8 | 🟡 | ViewResponse 3 参构造函数死代码 | 删除 |
| 9 | 🟢 | `VALUES()` deprecated (MySQL 8.0.20) | 改用 alias 语法 `AS new_row ... new_row.watch_duration_ms` |
| 10 | 🟢 | `watchDurationMs` 无下限校验 | Service 层加 `< 0` 校验 |

## 涉及文件

| 操作 | 文件 |
|------|------|
| 新增 | `backend/api-server/src/main/java/com/simpledouyin/api/config/GrpcConfig.java` |
| 新增 | `backend/api-server/src/test/java/com/simpledouyin/api/video/repository/VideoRepositoryTest.java` |
| 修改 | `HealthService.java` — 注入 ManagedChannel，修复 checkGrpc() |
| 修改 | `HealthController.java` — DOWN → 503 |
| 修改 | `VideoRepository.java` — COUNT(*)、TOCTOU、VALUES别名、移除默认值 |
| 修改 | `VideoService.java` — watchDurationMs校验、source 统一处理 |
| 修改 | `ViewResponse.java` — 删除死代码构造函数 |
| 修改 | `HealthControllerTest.java` — DOWN 断言改为 503 |
| 修改 | `LikeControllerTest.java` — 重写幂等性测试 |
| 修改 | `ViewControllerTest.java` — 边界测试 |

## 不修复的项

| # | 问题 | 原因 |
|---|------|------|
| 🟢 | 点赞/访问接口无速率限制 | 超出成员A任务范围，且 `INSERT IGNORE` 已保证单用户幂等 |

---

## 关键设计决策

### 1. COUNT(*) vs 反规范化计数器

**决策：** 移除 `like_count`/`view_count` 字段的增量更新，改为实时 `SELECT COUNT(*) FROM video_likes/views`。

**理由：**
- 消除并发漂移风险（无两阶段提交问题）
- `video_likes` 和 `video_views` 表有 `(user_id, video_id)` 唯一索引，`COUNT(*) WHERE video_id=?` 走索引覆盖，性能可接受
- `FIND_POST_BY_ID_SQL` 和 `FIND_MY_VIDEOS_BASE_SQL` 同步改为 COUNT 子查询
- videos 表的 `like_count`/`view_count` 列保留不删（schema 兼容），但代码不再写入

### 2. gRPC Channel 生命周期

**决策：** 新增 `GrpcConfig` 将 `ManagedChannel` 作为 `@Bean` (singleton) 注入。

**理由：**
- 避免每次 health check 创建/销毁线程池和 Netty event loop
- `@PreDestroy` 中优雅关闭
- 健康检查只需 `channel.getState(true)` + 轮询 READY

### 3. TOCTOU 修复范围

**决策：** 仅对 `INSERT_LIKE_SQL` 加子查询守卫，不修改 `recordView`。

**理由：**
- 点赞是「对已删除视频操作」的主要风险（用户可能通过缓存 URL 触发）
- 访问记录受影响较小（视频删除后前端不可见，不会触发访问）
- 过度修改会增加复杂度
