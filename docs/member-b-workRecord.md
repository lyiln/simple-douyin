# Member B 开发记录

## 阶段一：T15 — gRPC 推荐契约

**日期：** 2026-06-10  
**分支：** `main`（待后续切 feature 分支）

### 执行步骤

| 步骤 | 状态 | 说明 |
|---|---|---|
| 1.1 新建 proto | ✅ | `backend/recommend-service/src/main/proto/recommend.proto`，定义 `RecommendService.ListRecommendedVideos`，6 个 request 字段 + 5 个 response 字段 |
| 1.2 父 POM | ✅ | `pom.xml` 新增 `<os-maven-plugin.version>1.7.1</os-maven-plugin.version>` 和 `protobuf-maven-plugin`（版本 0.6.1，protoc 3.25.3，gRPC 1.65.1） |
| 1.3 recommend-service POM | ✅ | 新增 `os-maven-plugin` extension + `protobuf-maven-plugin` + `javax.annotation-api` 依赖（Java 17 兼容） |
| 1.4 api-server POM | ✅ | 新增对 `recommend-service` 的 Maven 依赖 |
| 1.5 编译验证 | ✅ | `mvn -q compile` 通过，6 个 stub 类生成在 `target/generated-sources/` |
| 1.6 合规检查 | ✅ | `git diff --check` 无空白错误，`git diff --name-only -- frontend` 无输出 |

### 验证结果

```
mvn -q compile              → PASS
mvn test (81 tests)         → PASS (0 failures, 0 errors)
git diff --check            → PASS
git diff --name-only -- frontend → PASS
```

### 遇到问题

| 问题 | 解决 |
|---|---|
| `javax.annotation.Generated` 在 Java 17 中不存在，编译报错 | recommend-service 新增 `javax.annotation:javax.annotation-api:1.3.2` 依赖 |
| `mvn -pl backend/api-server test` 无法解析 recommend-service 依赖 | 需先 `mvn -N install` 安装父 POM，再 `mvn -pl backend/recommend-service install` |

### 产出

- 新建 `recommend.proto`
- 修改 `pom.xml`（父）
- 修改 `recommend-service/pom.xml`
- 修改 `api-server/pom.xml`
- 编译生成 6 个 Java stub 类

---

## 阶段二：T16 — 推荐规则实现

**日期：** 2026-06-10

### 执行步骤

| 步骤 | 状态 | 说明 |
|---|---|---|
| 2.1 新建 CursorCodec | ✅ | `CursorCodec.java`，含 `Cursor` record（likeCount, createdAt, videoId）及 encode/decode 方法，Base64 URL-safe 编码 |
| 2.2 新建 RecommendRepository | ✅ | `RecommendRepository.java` + `RecommendVideoRow.java`，核心 SQL：`NOT EXISTS` 排除已访问视频 + 游标分页（6 参数） |
| 2.3 新建 RecommendServiceImpl | ✅ | 继承 `RecommendServiceGrpc.RecommendServiceImplBase`，参数校验（userId>0, limit 1-30），游标解码，调 repository，编码 nextCursor |
| 2.4 修改 Application | ✅ | 实现 `ApplicationRunner`，`ServerBuilder.forPort(9090).addService().start()` + shutdown hook |
| 2.5 编译验证 | ✅ | `mvn -q compile` 通过 |
| 2.6 合规检查 | ✅ | 无空白错误，未修改前端 |

### 验证结果

```
mvn -q compile              → PASS
git diff --check            → PASS
git diff --name-only -- frontend → PASS
```

### 遇到问题

| 问题 | 解决 |
|---|---|
| `catch (Status e)` 编译错误 — `io.grpc.Status` 不是 Throwable | 改为 `catch (StatusRuntimeException e)` |

### 产出

- 新建 `CursorCodec.java`（含 Cursor record）
- 新建 `RecommendRepository.java`
- 新建 `RecommendVideoRow.java`
- 新建 `RecommendServiceImpl.java`
- 修改 `RecommendServiceApplication.java`

---

## 阶段三：T17 — 推荐流 REST 端点

**日期：** 2026-06-10

### 执行步骤

| 步骤 | 状态 | 说明 |
|---|---|---|
| 3.1 新建 RecommendGrpcClient | ✅ | `RecommendGrpcClient.java`，`@Component`，注入 `ManagedChannel` Bean，构建 `RecommendServiceBlockingStub` |
| 3.2 新建 RecommendedFeedResponse | ✅ | `RecommendedFeedResponse.java`，`record(List<VideoPostResponse> items, String nextCursor, boolean hasMore, String strategy)` |
| 3.3 新建 FeedService | ✅ | `FeedService.java`，注入 `RecommendGrpcClient` + `VideoRepository` + `VideoPostAssembler`，逐 videoId 补全详情后按原序组装 |
| 3.4 新建 FeedController | ✅ | `FeedController.java`，`@RestController`，`GET /feeds/recommended/videos`，统一响应格式 |
| 3.5 修改鉴权 Filter | ✅ | `BearerAuthenticationFilter.java` 增加 `FEED_PATH` 常量和鉴权条件 |
| 3.6 编译验证 | ✅ | `mvn -q compile` 通过 |
| 3.7 回归测试 | ✅ | 81 tests PASS，0 failures，0 errors |
| 3.8 合规检查 | ✅ | 无空白错误，未修改前端 |

### 验证结果

```
mvn -q compile              → PASS
mvn test (81 tests)         → PASS (0 failures, 0 errors)
git diff --check            → PASS
git diff --name-only -- frontend → PASS
```

### 遇到问题

| 问题 | 解决 |
|---|---|
| 无 | 编译和测试一次通过 |

### 产出

- 新建 `RecommendGrpcClient.java`
- 新建 `RecommendedFeedResponse.java`
- 新建 `FeedService.java`
- 新建 `FeedController.java`
- 修改 `BearerAuthenticationFilter.java`

---

## 阶段四：T20 — 推荐规则测试

**日期：** 2026-06-10

### 执行步骤

| 步骤 | 状态 | 说明 |
|---|---|---|
| 4.1 新建测试配置 | ✅ | `recommend-service/src/test/resources/application-test.yml`，MySQL 密码环境变量占位符 |
| 4.2 补充测试依赖 | ✅ | `recommend-service/pom.xml` 新增 `spring-boot-starter-test` |
| 4.3 编写 RecommendRepositoryTest (R01-R07) | ✅ | `@SpringBootTest` 集成测试，7 个用例：排序、同赞排序、已访问过滤、全部访问、分页、已删除、私密 |
| 4.4 编写 FeedControllerTest (R08 + E10) | ✅ | MockMvc + mock FeedService，10 个用例：正常响应、cursor/limit 传递、401 未登录、400 无效参数、500 gRPC 异常、日志、脱敏 |
| 4.5 全量测试 | ✅ | 98 tests total，0 failures，0 errors |
| 4.6 合规检查 | ✅ | 无空白错误，未修改前端 |

### 验证结果

```
RecommendRepositoryTest (7) → PASS
FeedControllerTest (10)      → PASS
Existing tests (81)          → PASS
Total: 98 tests, 0 failures, 0 errors
```

### 测试覆盖矩阵

| test-plan 编号 | 测试方法 | 结果 |
|---|---|---|
| R01 | `sortsByLikeCountDesc` | ✅ |
| R02 | `sortsByCreatedAtWhenLikesEqual` | ✅ |
| R03 | `excludesViewedVideos` | ✅ |
| R04 | `returnsEmptyWhenAllViewed` | ✅ |
| R05 | `paginatesWithoutDuplicates` | ✅ |
| R06 | `excludesDeletedVideos` | ✅ |
| R07 | `excludesPrivateVideos` | ✅ |
| R08 | `feedEndpointCallsFeedServiceWhichCallsGrpc` | ✅ |
| E10 | `returns500WhenGrpcFails` | ✅ |

### 产出

- 新建 `recommend-service/src/test/resources/application-test.yml`
- 新建 `RecommendRepositoryTest.java`（7 用例）
- 新建 `FeedControllerTest.java`（10 用例）
- 修改 `recommend-service/pom.xml`（新增 `spring-boot-starter-test`）

---

## 总体回顾

四个阶段全部完成，产出汇总：

| 类型 | 数量 | 文件 |
|---|---|---|
| 新建源文件 | 11 | recommend.proto, CursorCodec.java, RecommendVideoRow.java, RecommendRepository.java, RecommendServiceImpl.java, RecommendGrpcClient.java, RecommendedFeedResponse.java, FeedService.java, FeedController.java, RecommendRepositoryTest.java, FeedControllerTest.java |
| 新建配置文件 | 1 | application-test.yml (recommend-service) |
| 修改源文件 | 2 | RecommendServiceApplication.java, BearerAuthenticationFilter.java |
| 修改 POM | 3 | pom.xml (父), recommend-service/pom.xml, api-server/pom.xml |

**测试：98 个用例全部通过，零失败，零回归。**

---

## 审查问题修复

**日期：** 2026-06-10  
**审查人：** Code Review  
**类型：** 跨成员数据一致性缺陷

### 发现问题

| # | 严重度 | 文件 | 问题 |
|---|---|---|---|
| 1 | 🔴 严重 | `RecommendRepository.java:14` | `ORDER BY v.like_count DESC` 使用 `videos` 表列值，但成员 A（T13）已将点赞改为实时 `SELECT COUNT(*)`，`videos.like_count` 列不再被维护（始终为 0），导致推荐排序失效 |

### 修复方案

**Option A（采用）：** 修改 RecommendRepository SQL，`v.like_count` → `(SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) AS like_count`

**理由：**
- 仅改 1 个文件、1 条 SQL，改动最小
- 与成员 A 的 Review01 修复决策一致
- 不改动已合并的 main 分支代码

### 修复内容

| 文件 | 改动 |
|---|---|
| `RecommendRepository.java` | `SELECT v.id, v.like_count, v.created_at` → `SELECT v.id, (SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) AS like_count, v.created_at` |
| `RecommendRepositoryTest.java` | 新增 `insertLikes()` 辅助方法；每个测试补充 `video_likes` 行插入；更新 `tearDown()` 清理范围 |

### 修复后验证

```
RecommendRepositoryTest (7) → PASS (修复后)
api-server tests (91)       → PASS (零回归)
```

---

## 审查问题修复 #2（PR #3 Review 发现）

**日期：** 2026-06-10  
**审查人：** 成员 A（PR Review）  
**类型：** SQL 别名引用不完整（修复 #1 遗留）

### 发现问题

| # | 严重度 | 文件 | 问题 |
|---|---|---|---|
| 1 | 🔴 严重 | `RecommendRepository.java` | SELECT 已改为子查询 `AS like_count`，但 `ORDER BY v.like_count DESC` 和 `CURSOR_CLAUSE` 的 `v.like_count` 仍引用表列值（始终为 0），导致排序和分页失效 |

**根因分析：** `v.like_count` 在 ORDER BY / WHERE 子句中指向 `videos.like_count` 表列，MySQL 不会自动解析为 SELECT 别名。第一次修复（审查修复 #1）仅改了 SELECT 子句，遗漏了同一 SQL 中其他引用该列的子句。

**另外发现：** 修复 #1 的测试之所以通过，是因为 `insertLikes()` 写入的行数与 `insertVideo()` 的列值恰好相同，`COUNT(*)` = 列值，掩盖了引用错误。

### 修复方案

将 SQL 改为派生表结构，内层子查询负责计算 `like_count` 别名，外层 WHERE 和 ORDER BY 统一通过 `t.like_count` 引用：

```sql
-- 修复前（SELECT 用了别名，但 ORDER BY / WHERE 引用表列）
SELECT v.id,
       (SELECT COUNT(*) ...) AS like_count,
       v.created_at
FROM videos v
WHERE ... AND v.like_count < ?        -- ❌ 表列
ORDER BY v.like_count DESC            -- ❌ 表列

-- 修复后（派生表统一引用）
SELECT t.id, t.like_count, t.created_at
FROM (
    SELECT v.id,
           (SELECT COUNT(*) ...) AS like_count,
           v.created_at
    FROM videos v
    WHERE ...
) t
WHERE t.like_count < ?                -- ✅ 别名
ORDER BY t.like_count DESC            -- ✅ 别名
```

同时将 Text Block SQL 改为字符串拼接，避免 Text Block 行尾空格导致的 SQL 语法问题。

### 修复内容

| 文件 | 改动 |
|---|---|
| `RecommendRepository.java` | 完整重写 SQL 结构：INNER_SQL（内层） + CURSOR_CLAUSE（`t.like_count`）+ ORDER_BY（`t.like_count`），Text Block → 字符串拼接 |

### 修复后验证

```
mvn -q compile                                          → PASS
RecommendRepositoryTest (7, 真实 MySQL)                  → PASS
FeedControllerTest (10, MockMvc)                         → PASS
api-server 已有测试 (81, MockMvc/Mock)                   → PASS
Total: 98 tests, 0 failures, 0 errors
```

### 教训

- 修改 SELECT 字段计算方式时，必须同步检查 ORDER BY、WHERE、GROUP BY、HAVING 中所有引用该字段的子句
- 测试数据恰好巧合（COUNT 值 = 列值）会掩盖 SQL 引用错误
- 跨模块变更（如 like_count 列废弃）应同步更新设计文档和任务文档中的 SQL 模板

