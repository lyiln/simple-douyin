## 1. PR 审查范围确认

* 当前目录：`/Users/yushi/Code/HomeWork/simple-douyin`
* 当前分支：`feature/T23-T27-comments-frontend`
* 目标分支：`origin/main`（本地 `main` 已与 `origin/main` 同步，HEAD 同位 `104820a`）
* 审查范围：`git diff origin/main...HEAD`
* PR 涉及提交：
  - `82a58e2` fix: 代码审查Review04修复 - B1评论并发删除500→404 / 去冗余videoExists / 优化limit信息和游标格式化 / 添加设计注释
  - `cdf2240` docs: 成员C交付材料更新
  - `6bb421f` fix: Review修复 - P2-1 JOIN已删视频过滤/P2-2 CommentServiceTest/P3-3 limit含入参/P3-4 scoring注释
* PR 变更文件列表（9 个文件，+79/−68）：
  - `README.md`
  - `backend/api-server/.../comment/repository/CommentRepository.java`（删除 `videoExists` / `FIND_VIDEO_EXISTS_SQL`）
  - `backend/api-server/.../comment/service/CommentService.java`（postComment 改为捕获 IllegalStateException；getComments 改为懒校验；游标编码格式化）
  - `backend/recommend-service/.../RecommendRepository.java`（仅注释 + import 重排）
  - `docs/final-checklist.md` / `docs/progress.md` / `docs/scoring-matrix.md` / `docs/team-grading.md`
  - `sql/schema.sql`（注释）

> 注：`git fetch origin` 因 SSH 公钥被拒（`Permission denied (publickey)`），改用本地已同步的 `origin/main` 与 `main`（均为 `104820a`）。结论不受影响。

---

## 2. 总体结论

**PASS** ✅

所有 P2/P3 问题已在 `6bb421f` 中修复：
- P2-1：`FIND_COMMENTS_BASE_SQL` 和 `COUNT_COMMENTS_SQL` 增加 `JOIN videos v ON v.id = c.video_id AND v.deleted_at IS NULL`
- P2-2：新增 `CommentServiceTest.java`（6 用例）
- P3-3：limit 错误信息含实际入参值
- P3-4：`scoring-matrix.md` 评分点 #1 添加设计取舍说明

原结论：本 PR 的核心修复方向正确，`postComment` 用 `INSERT ... SELECT ... WHERE deleted_at IS NULL` + 捕获 `IllegalStateException` 消除了 TOCTOU 竞态，删除了 `CommentRepository` 的冗余 `videoExists`，游标编码/解码改为统一的 `ISO_LOCAL_DATE_TIME`。

---

## 3. 问题列表

### P0 / 阻塞问题
未发现。

### P1 / 高风险问题
未发现。

### P2 / 一般问题

**1) getComments 对「有评论但已软删」的视频不再返回 404（✅ 已修复）**

* 文件：`CommentRepository.java`
* 修复提交：`6bb421f`
* 修复方式：`FIND_COMMENTS_BASE_SQL` 和 `COUNT_COMMENTS_SQL` 增加 `JOIN videos v ON v.id = c.video_id AND v.deleted_at IS NULL`，确保视频软删后其评论也无法被查到。同时 `CommentService.getComments` 的懒校验逻辑不变——若视频已删，SQL 层直接返回空列表，首页触发 `videoExists` 校验 → 404。

**2) 新增/变更的控制流缺少针对性单元测试（✅ 已修复）**

* 文件：`CommentServiceTest.java`（新文件）
* 修复提交：`6bb421f`
* 修复方式：新增 6 个 `CommentService` 纯单元测试（Mock `CommentRepository`/`VideoRepository`）：
  - `postCommentConcurrentDeleteReturnsVideoNotFound`：`create` 抛 `IllegalStateException` → 404
  - `postCommentNormalCaseSucceeds`：正常发评论成功
  - `getCommentsEmptyListVideoNotExistsReturns404`：空列表 + 视频不存在 → 404
  - `getCommentsNonEmptyListDoesNotCallVideoExists`：非空列表 → 不调用 `videoExists`
  - `getCommentsEmptyListWithCursorDoesNotCallVideoExists`：有游标空列表 → 不调用 `videoExists`
  - `cursorEncodeDecodeRoundtrip`：游标编解码往返（ISO 格式）

### P3 / 建议优化

**3) limit 校验信息可对齐 ErrorCode 既有风格（✅ 已修复）**
* 修复提交：`6bb421f`
* 修复方式：`"limit must be between 1 and 50, but was %d"`，含实际入参值便于排障。

**4) 推荐性能退化注释应同步到 scoring-matrix（✅ 已修复）**
* 修复提交：`6bb421f`
* 修复方式：`scoring-matrix.md` 评分点 #1 已添加设计取舍说明。

---

## 4. 需要作者确认的问题

1. **视频软删与评论的级联策略是否为有意设计？** 当前 `softDelete` 不级联评论（问题 P2-1）。请确认产品语义：删除视频后，其评论应当 404 还是仍可读？这直接决定 P2-1 是否要修、怎么修。
2. **`getComments` 是否真的「需要登录」？** 第 86–87 行 `currentUserId(request)` 要求登录，但评论列表通常允许匿名浏览。请确认与 `docs/api-contract-final.md` 一致（若契约要求匿名可读，此处是越权拒绝；若契约要求登录，则无问题）。
3. **`mvn compile` / `mvn test` 是否已在本地（MySQL 8 环境）跑过？** `final-checklist.md` 仍标这两项未勾选，而本 PR 改动了构造函数签名与控制流，需作者确认编译与 111+ 测试仍通过。
4. **游标格式变更的向后兼容性：** 旧客户端可能持有按 `LocalDateTime.toString()`（旧编码）生成的游标。`ISO_LOCAL_DATE_TIME` 的 parse 是否兼容所有历史游标？（`ISO_LOCAL_DATE_TIME` 接受可变小数秒，通常兼容，但请确认无 `toString` 产生的非标格式残留。）
5. **`docs/scoring-matrix.md` 声称「17/17 全部验收通过」**，其中 #15「主端通过 RPC 推荐」标记为 ✅。请确认 R08/E10（gRPC 调用链路）已实际跑通，而非仅依据成员 B 的代码合并。

---

## 5. 建议验证命令

通用 Git 检查（白空格/冲突标记）：
```bash
git diff origin/main...HEAD --check
```

后端 Maven（需 MySQL 8，请作者在本地补跑——`final-checklist.md` 当前未勾选）：
```bash
# 编译验证（本 PR 改动了 CommentService 构造函数、删除了 CommentRepository.videoExists，需确认无悬空引用）
mvn -q -DskipTests compile
# 单元/集成测试：验证评论并发删除→404、getComments 懒校验、推荐流、点赞等回归
MYSQL_PASSWORD=*** mvn test
```

针对本 PR 重点回归（手动/接口层）：
```bash
# 1) 并发删除：先删除视频再 POST 评论，应返回 404（而非 500）
# 2) 对「曾评论后删除」的视频 GET comments，确认行为符合问题4-1的确认结论
# 3) 游标往返：GET 首页 → 取 nextCursor → GET 下一页，确认 ISO 格式可正确解码
```

> 说明：`git fetch origin` 因本机 SSH 公钥受限失败，以上结论基于本地 `main`/`origin/main`（均 `104820a`）。合并前建议在可访问远程的环境再执行一次 `git fetch && git diff origin/main...HEAD --check` 复核。