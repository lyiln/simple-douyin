# 评分点验收矩阵

日期：2026-06-12

本矩阵基于课程作业要求、`docs/scope-final.md` 和当前实现状态，逐项检查评分点覆盖情况。

## 评分点对照表

| # | 课程评分点 | 实现位置 | 接口 | 数据库表 | 测试 | 演示 | 状态 |
|---|-----------|---------|------|---------|------|------|------|
| 1 | 按点赞数最高推荐 | gRPC Recommend Service | `GET /feeds/recommended/videos` | `videos` (idx_videos_recommend) | T20 R01/R02/R05 | 推荐列表排序 | ⬜ 成员B |
| 2 | 访问过不再推荐 | `video_views` 排除逻辑 | `POST /videos/{id}/views/me` | `video_views` (uk_user_video) | T14 R03/R04 | 已看视频消失 | ⬜ 成员B |
| 3 | 视频上下滑动 | Android `VerticalPager` | 消费推荐流 API | — | F01 | 上下滑动播放 | ⬜ 依赖推荐流 |
| 4 | 视频点赞 | `LikeController` + `VideoService` | `PUT/DELETE /videos/{id}/likes/me` | `video_likes` (uk_user_video) | T13 I01/I02 | 点赞状态更新 | ✅ |
| 5 | 评论演示闭环 | `CommentController` + `CommentService` | `GET/POST /videos/{id}/comments` | `comments` | T23-T25 C01-C06 | 查看+提交评论 | ✅ |
| 6 | 发布视频 | `VideoController` + `LocalUploadStorageService` | `POST /videos` (multipart) | `videos` | T10 N05/E05-E07 | 登录后发布 | ✅ |
| 7 | 我的视频分页 | `VideoService.myVideos()` | `GET /me/videos` | `videos` (idx_author_created) | T11 N07/P06 | 翻页无重复 | ✅ |
| 8 | 删除我的视频 | `VideoService.deleteMyVideo()` | `DELETE /videos/{id}` | `videos` (软删除 deleted_at) | T12 N08/N09/P05 | 删除自己/他人 | ✅ |
| 9 | 登录、注册、退出 | `AuthController` + `HmacTokenService` | `POST /auth/register\|login\|logout` | `users` (uk_username) | T05-T07 N01-N04/E01-E03 | 注册→登录→退出 | ✅ |
| 10 | 数据库设计 | `sql/schema.sql` | — | 6 张表 | D01-D04 | 表结构+索引展示 | ✅ |
| 11 | 视频存储设计 | `LocalUploadStorageService` | `POST /videos` | `videos.video_url` | S01 | 发布后可播放 | ✅ |
| 12 | 日志监控 | `RequestLoggingFilter` | 所有 REST 接口 | `request_logs` | L01-L07 | 输入/输出/耗时/聚合 | ✅ |
| 13 | 集成健康检查 | `HealthController` + `GrpcConfig` | `GET /health` | — | H01-H04 | MySQL/gRPC 状态 | ✅ |
| 14 | 安全与权限 | `BearerAuthenticationFilter` | 所有需登录接口 | — | P01-P08/L04 | 401/403/脱敏 | ✅ |
| 15 | 主端通过 RPC 推荐 | `GrpcConfig` + gRPC Client | `GET /feeds/recommended/videos` → `RecommendService` | `videos`、`video_views` | R08/E10 | gRPC 调用日志 | ⬜ 成员B |
| 16 | 文档与项目管理 | `docs/` + `README.md` + `.gitignore` | — | — | A01-A09 | PPT 展示 | ✅ |
| 17 | 前端联调 | `frontend/` + `ApiService` + `ApiRepository` | 全部 REST 接口 | — | F01-F04 | Android 真实数据 | ✅ |

## 统计

| 状态 | 数量 | 评分点 |
|------|------|--------|
| ✅ 已完成 | 13 | #4-#14, #16-#17 |
| ⬜ 待成员B | 4 | #1-#3, #15 |
| **合计** | **17** | |

## 缺陷 / 遗留项

| # | 项目 | 影响 | 负责人 |
|---|------|------|--------|
| 1 | 推荐流 (T15-T17) 未实现 | 无法演示推荐主场景 (F01) | 成员 B |
| 2 | 推荐规则测试 (T20) 未编写 | 推荐排序/过滤无法自动化验证 | 成员 B |
| 3 | 演示视频未录制 | T30 交付物缺失 | 成员 C |

## 需成员B交付后方可关闭

- T15：gRPC proto 定义 + Maven 生成配置
- T16：Recommend Service 推荐规则实现
- T17：`GET /api/v1/feeds/recommended/videos` REST 端点
- T20：推荐规则测试（R01-R08）

## 验收结论

当前 17 个评分点中 **13 个已可验收**（76%），剩余 4 个均依赖成员 B 的 gRPC 推荐服务。非推荐链路的全部功能（账号、视频管理、点赞、访问记录、评论、日志、健康检查、前端联调）均已就绪，111 个测试用例全部通过。
