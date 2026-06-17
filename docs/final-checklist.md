# 最终提交检查清单

日期：2026-06-12

## 代码仓库

- [x] Git 仓库公开可访问：`https://github.com/lyiln/simple-douyin`
- [x] `.gitignore` 忽略 `target/`、`.idea/`、`*.iml`、`uploads/` 中上传文件
- [x] 提交历史清晰可追溯，分支合并记录完整
- [ ] 最终代码与 master/main 分支一致（待合并）
- [x] 无硬编码密码或 token（使用环境变量 `MYSQL_PASSWORD`、`AUTH_TOKEN_SECRET`）

## 代码审查

- [x] 成员 A 的 PR #2 审查完成（`docs/Review01.md` + `Review01-fix-plan.md`）
- [x] 成员 C 的评论+前端变更已审查（`docs/Review02-member-c.md`、`docs/Review04-member-c.md`）
- [x] 成员 B 的 T15-T17 代码已审查（`docs/Review03-member-b.md`）
- [ ] 所有审查通过后由成员 C 合并到主分支

## 后端可运行性

- [x] `mvn compile` 无错误（protoc 中文路径问题为已知环境限制，不影响代码正确性）
- [ ] `$env:MYSQL_PASSWORD="***"; mvn test` → BUILD SUCCESS（需 MySQL 8 环境）
- [x] `sql/schema.sql` 可在 MySQL 8 中执行创建完整数据库
- [ ] `$env:MYSQL_PASSWORD="***"; mvn -pl backend/api-server spring-boot:run` 可启动（需 MySQL 8 环境）

## 接口完整性

| 接口 | 状态 |
|------|------|
| `POST /api/v1/auth/register` | ✅ |
| `POST /api/v1/auth/login` | ✅ |
| `POST /api/v1/auth/logout` | ✅ |
| `GET /api/v1/me` | ✅ |
| `POST /api/v1/videos` | ✅ |
| `GET /api/v1/me/videos` | ✅ |
| `DELETE /api/v1/videos/{videoId}` | ✅ |
| `PUT /api/v1/videos/{videoId}/likes/me` | ✅ |
| `DELETE /api/v1/videos/{videoId}/likes/me` | ✅ |
| `POST /api/v1/videos/{videoId}/views/me` | ✅ |
| `GET /api/v1/videos/{videoId}/comments` | ✅ |
| `POST /api/v1/videos/{videoId}/comments` | ✅ |
| `GET /api/v1/health` | ✅ |
| `GET /api/v1/feeds/recommended/videos` | ✅ |

## 文档交付

| 文档 | 文件 | 状态 |
|------|------|------|
| README（含部署步骤） | `README.md` | ✅ |
| 需求文档 | `docs/scope-final.md` | ✅ |
| API 契约 | `docs/api-contract-final.md` | ✅ |
| 数据库设计 | `docs/database-design.md` | ✅ |
| 模块设计 | `docs/backend-module-plan.md` | ✅ |
| gRPC 设计 | `docs/rpc-design.md` | ✅ |
| 任务分解 | `docs/task-breakdown.md` | ✅ |
| 测试计划+结果 | `docs/test-plan.md` | ✅ |
| 评分点矩阵 | `docs/scoring-matrix.md` | ✅ |
| 进度记录 | `docs/progress.md` | ✅ |
| 分工文档 | `docs/team-task-assignment.md` | ✅ |
| 代码审查报告 | `docs/代码审查报告.md` | ✅ |
| 团队评分表 | `docs/team-grading.md` | ⚠️ 需填姓名/学号/分数 |
| 成员A工作文档 | `docs/member-a-work-summary.md` | ⚠️ 需填姓名/学号 |
| 成员B工作记录 | `docs/member-b-workRecord.md` | ✅ |

## 答辩材料

- [ ] PPT（8 分钟，覆盖：需求→架构→实现→测试→结果）
- [ ] 演示视频（~2 分钟，覆盖 F01 推荐场景 + F02 视频管理场景）
- [ ] 团队评分表填写完成

## 最终提交包

- [ ] 代码打包（ZIP）与 Git 仓库一致
- [ ] 包含所有文档 PDF 版本
- [ ] 按课程平台要求上传

---

> **剩余待办：** 演示视频录制、答辩 PPT 制作、团队评分表填写姓名/学号/分数、最终合并到主分支。
