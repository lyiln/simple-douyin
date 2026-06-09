# PR #2 深度 Code Review 报告

**分支:** `feature/member-a-like-view-health`  
**日期:** 2026-06-09  
**变更范围:** 点赞/取消点赞、访问记录、健康检查、认证过滤器扩展、单元测试  
**变更文件:**
- `backend/api-server/src/main/java/com/simpledouyin/api/video/service/VideoService.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/video/controller/VideoController.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/video/repository/VideoRepository.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/video/dto/LikeResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/video/dto/ViewRequest.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/video/dto/ViewResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/health/controller/HealthController.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/health/dto/HealthResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/health/service/HealthService.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/auth/security/BearerAuthenticationFilter.java`
- `backend/api-server/src/test/java/com/simpledouyin/api/health/controller/HealthControllerTest.java`
- `backend/api-server/src/test/java/com/simpledouyin/api/video/controller/LikeControllerTest.java`
- `backend/api-server/src/test/java/com/simpledouyin/api/video/controller/ViewControllerTest.java`

---

## 一、核心逻辑 — 并发 / 数据一致性

### 🔴 严重 | like()/unlike() 两步操作存在并发计数漂移风险

**文件:** `VideoRepository.java` 第 299–320 行

```java
// like() — 两条独立 SQL
int affectedRows = jdbcTemplate.update(INSERT_LIKE_SQL, userId, videoId);  // ①
if (affectedRows > 0) {
    jdbcTemplate.update(INCREMENT_LIKE_COUNT_SQL, videoId);                // ②
    return true;
}
```

`like()` 和 `unlike()` 都由两条 SQL 组成（INSERT/DELETE + UPDATE counter）。虽然 Service 层有 `@Transactional`，保证了同一个 connection 上不会被打断，但 **反规范化的 `like_count` / `view_count` 计数器在高并发下会漂移**：

- **场景 A（同用户并发双击赞）：** 两个请求同时对 `(userId=1, videoId=1)` 执行 `INSERT IGNORE`，MySQL 行锁排队后，一个成功（affected=1），另一个被忽略（affected=0）。成功的那个执行 `like_count + 1`。**结果正确。** 但如果第一个事务回滚（比如后续查询 `findLikeCount` 抛异常），`INSERT` 和 `like_count + 1` 都回滚，**也正确**。
- **场景 B（不同用户并发点赞同一视频）：** 两个不同用户的事务各自 INSERT 成功，然后各自 `SET like_count = like_count + 1`。MySQL 行锁串行化 UPDATE，最终 `like_count` 正确 +2。**短期正确。**
- **场景 C（长期漂移）：** 如果运维手动清理 `video_likes` 表、或服务重启期间有 in-flight 事务部分提交、或未来加入异步消息解耦，反规范化计数器就会和 `COUNT(*)` from `video_likes` 不一致。当前没有对账机制。

**建议：** 用单条 SQL 或数据库触发器来保证原子性。推荐改法：

```java
// 方案一：用子查询让 INSERT 和计数在同一个语句完成
// 方案二：在 like() 里用 SELECT COUNT(*) 实时计算而非依赖反规范化字段
public long findLikeCount(long videoId) {
    Long count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM video_likes WHERE video_id = ?", Long.class, videoId);
    return count != null ? count : 0L;
}
```

---

### 🔴 严重 | recordView() 同样存在计数漂移 + source 字段被静默忽略

**文件:** `VideoRepository.java` 第 335–349 行 + `VideoService.java` 第 190 行

```java
// Repository 第 336-346 行
int affectedRows = jdbcTemplate.update(INSERT_VIEW_SQL, ...);  // ON DUPLICATE KEY UPDATE
if (affectedRows == 1) {
    jdbcTemplate.update(INCREMENT_VIEW_COUNT_SQL, videoId);    // 仅首次 +1
    return true;
}
```

问题同 like()，另外还有一个 **SQL 逻辑问题**：

`INSERT_VIEW_SQL` 的 `ON DUPLICATE KEY UPDATE` 只更新了 `watch_duration_ms`，**没有更新 `source`**。这意味着用户从不同入口多次访问同一视频时，`source` 永远停留在首次值。如果这是有意设计（记录首次来源），应在代码注释中说明；如果需要记录最新来源，需要加上 `source = VALUES(source)`。

另外，`VALUES()` 函数在 **MySQL 8.0.20 已被标记为 deprecated**，建议改用 alias 语法：

```sql
INSERT INTO video_views (...) VALUES (?, ?, ?, ?, ...) AS new_row
ON DUPLICATE KEY UPDATE
    updated_at = CURRENT_TIMESTAMP(3),
    watch_duration_ms = new_row.watch_duration_ms
```

---

### 🟡 中等 | Service 层 source 默认值重复

**文件:** `VideoService.java` 第 190 行 + `VideoRepository.java` 第 340 行

```java
// Service 层
String source = body != null && body.source() != null ? body.source() : "recommended_feed";

// Repository 层又做了一次
source != null && !source.isBlank() ? source.trim() : "recommended_feed"
```

两处逻辑功能重叠但行为不完全一致（Service 不 trim，Repository 做 trim）。如果传入 `" "`（纯空格），Service 层认为非 null 直接传过去，Repository 层 trim 后判空回退到默认值。**建议只在 Service 层做一次规范化，Repository 不做额外判断。**

---

### 🟡 中等 | videoExists() 和后续操作之间存在 TOCTOU 竞态

**文件:** `VideoService.java` 第 159–163 行

```java
if (!videoRepository.videoExists(videoId)) {       // ① 检查
    throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
}
videoRepository.like(currentUserId, videoId);       // ② 操作
```

在 `@Transactional` 内，步骤①和②之间没有对 `videos` 表加锁。另一个事务可能在此窗口内软删除该视频，导致对已删除视频的点赞被记录。虽然不严重（因为视频已经不存在了，前端不会展示），但 **建议在 `INSERT_LIKE_SQL` 中加一个条件**：

```sql
INSERT IGNORE INTO video_likes (user_id, video_id, created_at)
SELECT ?, ?, CURRENT_TIMESTAMP(3)
FROM videos WHERE id = ? AND deleted_at IS NULL
```

---

## 二、健壮性 — 异常处理 & 边界

### 🔴 严重 | 健康检查每次创建新的 gRPC Channel — 资源泄露

**文件:** `HealthService.java` 第 64–90 行

```java
private String checkGrpc() {
    ManagedChannel channel = null;
    try {
        channel = ManagedChannelBuilder
                .forAddress(grpcHost, grpcPort)
                .usePlaintext()
                .build();                           // 每次调用都创建新 channel！
        // ...
    } finally {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();                  // shutdownNow 不等待线程池清理
        }
    }
}
```

每次健康检查请求都创建一个 `ManagedChannel`（内部包含线程池、Netty event loop 等）。如果有监控系统每 10 秒调一次 `/health`，就会每 10 秒创建/销毁一个 channel，导致：
- 线程创建/销毁开销
- `shutdownNow()` 不等待底层线程池优雅退出，可能留下孤儿线程
- 端口资源短暂占用

**建议：** 将 `ManagedChannel` 作为 Bean 注入（singleton），在应用生命周期内复用。健康检查只做 `channel.getState(true)` 即可。

---

### 🔴 严重 | gRPC 健康检查逻辑有误 — awaitTermination 语义错误

**文件:** `HealthService.java` 第 72–78 行

```java
ConnectivityState state = channel.getState(true);
channel.notifyWhenStateChanged(state, () -> {});
channel.awaitTermination(2, TimeUnit.SECONDS);     // ← 这里是问题
return channel.getState(false) != ConnectivityState.SHUTDOWN
        && channel.getState(false) != ConnectivityState.TRANSIENT_FAILURE
        ? UP : DOWN;
```

`awaitTermination()` 的含义是「等待 channel **关闭**」，不是「等待连接建立」。对于新建的 channel：
1. `getState(true)` → 返回 `IDLE`（并触发连接）
2. `notifyWhenStateChanged` → 注册回调（但回调是异步的，不一定在 2s 内执行）
3. `awaitTermination(2s)` → 等 channel 关闭（没人调 shutdown，所以 2s 后超时返回 false）
4. `getState(false)` → 可能仍为 `IDLE` 或 `CONNECTING`（既不 SHUTDOWN 也不 TRANSIENT_FAILURE）→ 误报为 `UP`

实际上，如果 gRPC 服务根本不可达，状态可能是 `TRANSIENT_FAILURE`，但也可能是 `CONNECTING`（还在尝试），此时代码会报 `UP`，这是**假阳性**。

**建议：** 使用 gRPC 标准的 Health Checking Protocol（`io.grpc.health.v1.HealthGrpc`），或至少用 `ConnectivityState.READY` 来判断：

```java
ConnectivityState state = channel.getState(true);
// 等待最多 2 秒直到状态变化
long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
while (state != ConnectivityState.READY && state != ConnectivityState.TRANSIENT_FAILURE
       && System.nanoTime() < deadline) {
    Thread.sleep(100);
    state = channel.getState(false);
}
return state == ConnectivityState.READY ? UP : DOWN;
```

---

### 🟡 中等 | 健康检查返回 DOWN 时 HTTP 状态码仍为 200

**文件:** `HealthController.java` 第 22–24 行

```java
@GetMapping("/health")
public ResponseEntity<ApiResponse<HealthResponse>> health() {
    return ResponseEntity.ok(ApiResponse.success(healthService.check()));
    //                    ↑ 永远是 200
}
```

当 MySQL 或 gRPC 不可用时，`status` 为 `"DOWN"` 但 HTTP 仍返回 `200 OK`。标准的健康检查实践是：DOWN 时返回 `503 Service Unavailable`，以便负载均衡器和监控系统正确识别。

**建议：**
```java
HealthResponse response = healthService.check();
HttpStatus status = "UP".equals(response.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
return ResponseEntity.status(status).body(ApiResponse.success(response));
```

---

### 🟡 中等 | ViewResponse.created 的 @JsonIgnore 与内部访问的矛盾

**文件:** `ViewResponse.java` 第 1–17 行

```java
public record ViewResponse(
        long videoId,
        boolean viewed,
        long viewCount,
        @JsonIgnore boolean created    // 不序列化到 JSON
) {
    public ViewResponse(long videoId, boolean viewed, long viewCount) {
        this(videoId, viewed, viewCount, false);  // 默认 false
    }
}
```

`@JsonIgnore` 阻止了 JSON 序列化，但 Service 层仍然通过 `response.created()` 来决定 HTTP 状态码（201 vs 200）。这个设计能工作，但 **三参数构造函数没有任何调用者**，属于死代码。如果未来有人调用 `new ViewResponse(1, true, 5)` 并期望 `created=true`，会出 bug。建议删除或标记为 `@Deprecated`。

---

### 🟢 低 | watchDurationMs 无下限校验

**文件:** `VideoService.java` 第 191 行 + `VideoRepository.java` 第 341 行

`watchDurationMs` 可以为负数或 0。数据库定义为 `INT UNSIGNED`，传入负数会导致 MySQL 报错（如果启用了 strict mode）或截断。建议在 Service 层加校验：

```java
if (watchDurationMs != null && watchDurationMs < 0) {
    throw new BusinessException(ErrorCode.INVALID_PARAMETER, "invalid watchDurationMs");
}
```

---

### 🟢 低 | 点赞/访问无速率限制

当前 `PUT /videos/{id}/likes/me` 和 `POST /videos/{id}/views/me` 没有任何速率限制。恶意用户可以脚本刷赞/刷播放量。虽然 `INSERT IGNORE` 和 `ON DUPLICATE KEY UPDATE` 保证了幂等性（同一用户重复调不会多计数），但不同用户的刷量仍会影响 `like_count`/`view_count`。

---

## 三、测试覆盖评估

### 测试概览

| 测试文件 | 测试数 | 覆盖维度 |
|---------|-------|---------|
| `HealthControllerTest.java` | 6 | 核心逻辑 (4) + 日志 (2) |
| `LikeControllerTest.java` | 10 | 核心逻辑 (5) + 权限 (3) + 日志 (2) |
| `ViewControllerTest.java` | 8 | 核心逻辑 (4) + 权限 (2) + 日志 (2) |

### ✅ 覆盖良好的部分

1. **幂等性测试**（LikeControllerTest 第 111–152 行）：测试了重复点赞和重复取消点赞的行为
2. **权限校验**（三个测试类都覆盖了未认证/无效 Token 场景）
3. **日志完整性**（验证了 requestId、userId、path、method、statusCode、durationMs）
4. **敏感数据不泄露**（验证响应中不包含 token/password 等字段）
5. **HTTP 状态码**（验证了首次访问返回 201，重复返回 200）

### 🔴 严重缺陷 — 幂等性测试无效

**文件:** `LikeControllerTest.java` 第 111–152 行

```java
@Test
void likeIdempotentRepeatedCallsReturnSameLikeCount() throws Exception {
    // 模拟已点赞再点赞的场景：like_count 不变
    when(videoService.likeVideo(any(HttpServletRequest.class), eq(2001L)))
            .thenReturn(new LikeResponse(2001L, true, 5L));  // ← mock 永远返回 5

    // 第一次点赞
    mockMvc.perform(put(...)).andExpect(jsonPath("$.data.likeCount").value(5));
    // 第二次点赞（幂等）
    mockMvc.perform(put(...)).andExpect(jsonPath("$.data.likeCount").value(5));
}
```

**这个测试没有真正测试幂等性。** 它 mock 了 Service 层永远返回 `likeCount=5`，然后断言两次调用都返回 5 — 当然会通过，但这只验证了 mock 框架在工作。真正需要验证的是：
- 第二次 `INSERT IGNORE` 返回 `affectedRows=0`
- `like_count` 没有被二次递增

**同理，unlikeIdempotent 测试（第 133–152 行）也有同样问题。**

要真正测试幂等性，需要 **集成测试**（用 `@SpringBootTest` + Testcontainers 或 H2），或者至少在 Repository 层做单元测试。

### 🟡 缺失的测试场景

| 缺失场景 | 影响 |
|---------|------|
| **Repository 层单元测试**（`like()`, `unlike()`, `recordView()` 的 SQL 逻辑） | 无法验证 INSERT IGNORE / ON DUPLICATE KEY UPDATE 的实际行为 |
| **`watchDurationMs` 为 0 或负数** | 边界条件未覆盖 |
| **`source` 为空字符串/纯空格** | Service/Repository 的默认值回退逻辑未验证 |
| **`videoId` 边界值**（0, -1, Long.MAX_VALUE） | Service 层有 `videoId <= 0` 校验但测试没覆盖 |
| **HealthService 的 gRPC 检查**（DOWN 场景、超时场景） | 只测了 mock 的 Controller 层，Service 的实际连接逻辑无覆盖 |
| **并发点赞**（多线程同时 like 同一视频） | 计数漂移风险无法通过单线程测试发现 |
| **BearerAuthenticationFilter.isVideoActionPath 路径匹配** | 路径匹配逻辑改了，但没有对应的路径匹配测试（如 `/videos/abc/likes/me`、`/videos/123/other`） |

---

## 四、问题汇总

| 等级 | 文件 | 行号 | 问题 |
|-----|------|------|------|
| 🔴 严重 | `VideoRepository.java` | 299–320 | like/unlike 两步操作，反规范化计数器可能漂移 |
| 🔴 严重 | `VideoRepository.java` | 335–349 | recordView 同上的计数漂移问题 |
| 🔴 严重 | `HealthService.java` | 64–90 | 每次请求创建/销毁 gRPC Channel，资源泄露 |
| 🔴 严重 | `HealthService.java` | 72–78 | `awaitTermination` 语义错误，gRPC 连通性判断不可靠 |
| 🔴 严重 | `LikeControllerTest.java` | 111–152 | 幂等性测试无效（mock 恒定返回值） |
| 🟡 中等 | `HealthController.java` | 22–24 | DOWN 时应返回 503 而非 200 |
| 🟡 中等 | `VideoService.java` | 159–163 | videoExists 检查与操作之间 TOCTOU 竞态 |
| 🟡 中等 | `VideoService.java` + `VideoRepository.java` | 190 / 340 | source 默认值逻辑重复且行为不一致 |
| 🟡 中等 | `ViewResponse.java` | 13–16 | 三参数构造函数是死代码 |
| 🟢 低 | `VideoService.java` | 191 | `watchDurationMs` 无下限校验 |
| 🟢 低 | `VideoController.java` | — | 点赞/访问接口无速率限制 |
| 🟢 低 | `VideoRepository.java` | 170 | `VALUES()` 在 MySQL 8.0.20 已 deprecated |

---

## 五、总体评价

**代码质量尚可，架构清晰**，Controller → Service → Repository 的分层规范，认证过滤器的路径匹配也做了合理重构。但有几个核心问题需要修正后才能合并：

1. **必须修复：** HealthService 的 gRPC 检查逻辑（资源泄露 + 语义错误）
2. **强烈建议：** 补充 Repository 层的集成测试，替换当前无效的幂等性 mock 测试
3. **建议改进：** 计数器的原子性保证（触发器或实时 COUNT 查询）
4. **建议改进：** Health 接口 DOWN 时返回 503
