# 成员 C 工作 Code Review 报告

**分支:** main（成员 C 直接在 main 分支开发 T23-T27）  
**日期:** 2026-06-12  
**审查人:** 成员 A（待确认）  
**变更范围:** 评论闭环 + 前端联调网络层 + 文档更新

## 变更文件

### 后端新增
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/controller/CommentController.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/service/CommentService.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/repository/CommentRepository.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/dto/CommentResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/dto/PostCommentRequest.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/dto/PostCommentResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/dto/GetCommentsResponse.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/model/Comment.java`
- `backend/api-server/src/main/java/com/simpledouyin/api/comment/model/CommentPageCursor.java`

### 后端测试新增
- `backend/api-server/src/test/java/com/simpledouyin/api/comment/controller/CommentControllerTest.java`

### 后端修改
- `backend/api-server/src/main/java/com/simpledouyin/api/auth/security/BearerAuthenticationFilter.java`

### 前端新增
- `frontend/app/src/main/java/com/example/douyin/network/model/ApiModels.kt`
- `frontend/app/src/main/java/com/example/douyin/network/ApiService.kt`
- `frontend/app/src/main/java/com/example/douyin/network/ApiClient.kt`
- `frontend/app/src/main/java/com/example/douyin/data/ApiRepository.kt`

### 前端修改
- `frontend/app/build.gradle.kts`
- `frontend/app/src/main/AndroidManifest.xml`
- `frontend/app/src/main/java/com/example/douyin/MainActivity.kt`
- `frontend/app/src/main/java/com/example/douyin/ui/DouyinApp.kt`

### 文档更新/新增
- `README.md`（更新评论状态、前端联调状态、测试数量）
- `docs/progress.md`（更新 T23-T27 状态、里程碑）
- `docs/test-plan.md`（追加 §14 测试执行结果）
- `docs/scoring-matrix.md`（新建）
- `docs/team-grading.md`（新建）
- `docs/final-checklist.md`（新建）

---

## 一、API 契约审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| `GET /videos/{videoId}/comments` 与 `api-contract-final.md` 一致 | ✅ | 支持 cursor/limit 分页，返回 items/nextCursor/hasMore/commentCount |
| `POST /videos/{videoId}/comments` 与 `api-contract-final.md` 一致 | ✅ | 返回 comment + commentCount，201 状态码 |
| 统一响应格式 `code/message/data/requestId` | ✅ | 复用 `ApiResponse.success()` |
| 时间格式 ISO 8601 UTC | ✅ | `createdAt.atOffset(ZoneOffset.UTC).toString()` |

## 二、权限审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| 评论接口需要 Bearer Token | ✅ | `BearerAuthenticationFilter` 已添加 `/comments` 路径 |
| 发表评论绑定当前用户 | ✅ | `currentUserId(request)` → `commentRepository.create(videoId, currentUserId, content)` |
| 未登录返回 401 | ✅ | CommentControllerTest 覆盖 4 个权限用例 |
| 视频不存在返回 404 | ✅ | `commentRepository.videoExists()` 前置检查 |
| 未越界实现 Bonus | ✅ | 只做了评论，无收藏/关注/分享/搜索 |

## 三、数据一致性审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| comment_count 维护 | ✅ | `INSERT` 评论后 `UPDATE videos SET comment_count = comment_count + 1` |
| 游标分页稳定性 | ✅ | `ORDER BY created_at DESC, id DESC`，cursor 编码 `createdAt|id` |
| 软删除 | ✅ | 查询评论时 `deleted_at IS NULL` |
| 内容校验 | ✅ | Service 层校验长度 ≤300、非空、trim |

## 四、日志审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| requestId 记录 | ✅ | CommentControllerTest 验证 `requestId` 写入 `request_logs` |
| userId 记录 | ✅ | 登录用户 `userId` 写入日志 |
| 错误日志 | ✅ | 404/401 时 `error_message` 存在 |
| 敏感字段脱敏 | ✅ | 复用现有 `SensitiveDataSanitizer` |

## 五、测试审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| 测试覆盖完整 | ✅ | 16 用例：正常发表/列表、空列表、分页、内容异常、视频不存在、权限、日志 |
| 全量测试通过 | ✅ | 111 tests, 0 failures |
| Mock 隔离合理 | ✅ | CommentControllerTest 使用 standaloneSetup + mock CommentService |

## 六、前端联调审查

| 审查项 | 状态 | 说明 |
|--------|------|------|
| Retrofit/OkHttp 依赖添加 | ✅ | `build.gradle.kts` 新增网络依赖 |
| 网络权限 | ✅ | `AndroidManifest.xml` 添加 INTERNET + usesCleartextTraffic |
| Token 管理 | ✅ | `ApiClient` 通过 SharedPreferences 持久化 |
| 评论 API 对接 | ✅ | `DouyinApp.kt` 评论双模式（API 优先 + mock 回退） |
| Base URL 配置 | ✅ | 默认 `http://10.0.2.2:8080/`（模拟器），可自定义 |

---

## 发现的问题

| # | 等级 | 问题 | 状态 |
|---|------|------|------|
| 1 | 🟡 | 未使用 feature 分支，直接在 main 开发 | 后续注意，本次变更完整且测试通过 |

## 审查结论

- [ ] 审查通过，可以合并
- [ ] 需要修改后重新审查

**审查人签名：** ________  
**日期：** ________
