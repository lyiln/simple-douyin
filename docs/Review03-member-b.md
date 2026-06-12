# 成员 B 工作 Code Review 报告

**分支:** `feature/member-b-grpc-recommend`  
**日期:** 2026-06-12  
**审查人:** 成员 C（合并协调人预审） → 正式审查由成员 A 最终确认  
**变更范围:** gRPC 推荐契约、推荐规则、推荐流 REST、推荐测试

## 变更文件

| 文件 | 新增/修改 | 评分点 |
|------|----------|--------|
| `recommend.proto` | 新增 | T15 gRPC 契约 |
| `RecommendServiceImpl.java` | 新增 | T16 推荐规则 |
| `RecommendRepository.java` | 新增 | T16 推荐规则 |
| `CursorCodec.java` | 新增 | T16 游标分页 |
| `RecommendVideoRow.java` | 新增 | T16 数据模型 |
| `RecommendGrpcClient.java` | 新增 | T17 gRPC 客户端 |
| `FeedController.java` | 新增 | T17 推荐流 REST |
| `FeedService.java` | 新增 | T17 推荐流 REST |
| `RecommendedFeedResponse.java` | 新增 | T17 响应 DTO |
| `FeedControllerTest.java` | 新增 (9 用例) | T20 推荐测试 |
| `RecommendRepositoryTest.java` | 新增 (8 用例) | T20 推荐测试 |
| `BearerAuthenticationFilter.java` | 修改 (+2行) | 鉴权扩展 |
| `pom.xml` (3个) | 修改 | protobuf 编译配置 |

---

## 一、API 契约审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| proto 字段与 `rpc-design.md` 一致 | ✅ | requestId/userId/cursor/limit/excludeViewed/strategy 全部对应 |
| REST 端点路径正确 | ✅ | `GET /api/v1/feeds/recommended/videos` |
| 推荐规则与文档一致 | ✅ | `like_count DESC, created_at DESC, id DESC`，排除已访问 |
| 游标编码格式 | ✅ | Base64(`likeCount|createdAt|videoId`)，支持三元组分页 |
| FeedService 保持 gRPC 返回顺序 | ✅ | 按 `getVideoIdsList()` 顺序组装 |
| 缺失视频处理 | ✅ | `ifPresentOrElse` 跳过 + warn 日志，不抛异常 |

## 二、权限审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| Feed REST 需要登录 | ✅ | `BearerAuthenticationFilter` 添加 `FEED_PATH` |
| gRPC 层不自己鉴权 | ✅ | userId 由 REST 层传入 |
| 未登录返回 401 | ✅ | FeedControllerTest 覆盖 |
| 未越界实现 Bonus | ✅ | 只做推荐，无收藏/关注/搜索 |

## 三、数据一致性审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| 访问过滤正确 | ✅ | `NOT EXISTS (SELECT 1 FROM video_views WHERE user_id=? AND video_id=v.id)` |
| 状态过滤正确 | ✅ | `status='published' AND visibility='public' AND deleted_at IS NULL` |
| 游标排序稳定 | ✅ | 三元组 `(like_count, created_at, id)` |
| gRPC 错误处理 | ✅ | `StatusRuntimeException` → `onError`；其他 → `INTERNAL` + 日志 |

### 🔴 严重：游标分页中的 `v.like_count` 使用表列而非实时计数值

**文件:** `RecommendRepository.java` CURSOR_CLAUSE

```sql
-- SELECT 中的 like_count 是子查询（实时值）
SELECT v.id,
       (SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) AS like_count,
       v.created_at

-- 但 CURSOR_CLAUSE 用的是 v.like_count（表列，可能过期）
AND (
    v.like_count < ?          -- ← 这里的 v.like_count 是表列！
    OR (v.like_count = ? ...
)
```

**问题：** 根据 Review01 修复方案，`videos.like_count` 列不再由代码维护（改为实时 `SELECT COUNT(*)`），因此表列值可能为 0 或过期。游标分页使用 `v.like_count` 会导致排序和分页错误。

**修复建议：** 将 CURSOR_CLAUSE 中的 `v.like_count` 替换为子查询，或用 HAVING 子句引用别名：

```sql
-- 方案：使用 HAVING 引用别名 like_count
-- 但这需要把 WHERE 条件移到 HAVING，性能会差
-- 更好的方案：把子查询结果作为派生表

-- 或者最简单的方案：直接在 WHERE 中重复子查询
AND (
    (SELECT COUNT(*) FROM video_likes WHERE video_id = v.id) < ?
    OR ...
)
```

**但注意：** 如果成员 A 的 Review01 修复尚未部署到 MySQL（即 `like_count` 列仍在维护中），那么当前代码可以正常工作。需要在合并前确认 `like_count` 列的维护状态。

## 四、gRPC 实现审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| Service 继承正确 | ✅ | `RecommendServiceGrpc.RecommendServiceImplBase` |
| 参数校验 | ✅ | userId>0, limit 1-30 |
| 响应字段完整 | ✅ | videoIds/nextCursor/hasMore/strategy |
| Channel 复用 | ✅ | `ManagedChannel` 作为 Bean 注入（Review01 修复后） |
| 阻塞 Stub 使用 | ✅ | `newBlockingStub(channel)` |

## 五、测试审查

### FeedControllerTest (9 用例)

| 用例 | 覆盖 | 状态 |
|------|------|------|
| 正常返回推荐视频 | N12、R08 | ✅ |
| cursor+limit 透传 | N12 | ✅ |
| 未登录 401 | P01 | ✅ |
| 无效 token 401 | P01 | ✅ |
| 非法 limit 400 | E04 | ✅ |
| gRPC 异常 500 | E10 | ✅ |
| 日志记录正确 | L01 | ✅ |
| 未登录日志正确 | L02 | ✅ |
| 敏感字段不泄漏 | L04 | ✅ |

### RecommendRepositoryTest (8 用例)

| 用例 | 覆盖 | 状态 |
|------|------|------|
| like_count 降序 | R01 | ✅ |
| 同赞按时间降序 | R02 | ✅ |
| 排除已访问视频 | R03 | ✅ |
| 全部已访问返回空 | R04 | ✅ |
| 分页无重复 | R05 | ✅ |
| 排除软删除视频 | R06 | ✅ |
| 排除私密视频 | R07 | ✅ |
| — | R08 (REST→gRPC) | 由 FeedControllerTest 覆盖 |

## 六、Maven 配置审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| protobuf-maven-plugin 配置 | ✅ | 0.6.1 + protoc 3.25.3 + grpc-java 1.65.1 |
| os-maven-plugin | ✅ | 1.7.1，自动检测 OS 架构 |
| recommend-service 依赖 | ✅ | api-server 通过 `${project.version}` 引用 |
| javax.annotation-api | ⚠️ | 添加了 `javax.annotation:javax.annotation-api:1.3.2`，但 Spring Boot 3.x 使用 Jakarta，可能需要 `jakarta.annotation-api` |

## 七、与成员 C 分支的冲突预览

合并时以下文件会冲突：

| 文件 | 成员 B 变更 | 成员 C 变更 | 冲突处理 |
|------|------------|------------|----------|
| `BearerAuthenticationFilter.java` | +FEED_PATH | +comments 路径 | 合并两者 |
| `README.md` | 更新推荐状态 | 更新评论/前端状态 | 合并两者 |
| `progress.md` | 更新推荐状态 | 更新评论/前端状态 | 合并两者 |

## 八、审查结论

| 项 | 评价 |
|----|------|
| 代码质量 | ⭐⭐⭐⭐ 结构清晰、分层合理 |
| 测试覆盖 | ⭐⭐⭐⭐⭐ 17 用例全覆盖 R01-R08/E04/E10/P01/L01-L04 |
| 文档 | ⭐⭐⭐⭐⭐ 含 workRecord.md + workplan.md |
| 主要风险 | 🔴 `v.like_count` 游标列问题 |

- [ ] 需修复 `v.like_count` 游标问题后通过
- [ ] 成员 A 最终确认

**审查人:** ________（成员 A）  
**日期:** ________
