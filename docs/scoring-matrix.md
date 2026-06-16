# 评分点验收矩阵

日期：2026-06-12

本矩阵基于课程作业要求、`docs/scope-final.md` 和当前实现状态，逐项检查评分点覆盖情况。

## 评分点对照表

| # | 课程评分点 | 实现位置 | 接口 | 数据库表 | 测试 | 演示 | 状态 |
|---|-----------|---------|------|---------|------|------|------|
| 1 | 按点赞数最高推荐 | gRPC Recommend Service | `GET /feeds/recommended/videos` | `videos` (idx_videos_recommend) | T20 R01/R02/R05 | 推荐列表排序 | ✅ |
| 2 | 访问过不再推荐 | `video_views` 排除逻辑 | `POST /videos/{id}/views/me` | `video_views` (uk_user_video) | T14 R03/R04 | 已看视频消失 | ✅ |
| 3 | 视频上下滑动 | Android `VerticalPager` | 消费推荐流 API | — | F01 | 上下滑动播放 | ✅ |
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
| 15 | 主端通过 RPC 推荐 | `GrpcConfig` + gRPC Client | `GET /feeds/recommended/videos` → `RecommendService` | `videos`、`video_views` | R08/E10 | gRPC 调用日志 | ✅ |
| 16 | 文档与项目管理 | `docs/` + `README.md` + `.gitignore` | — | — | A01-A09 | PPT 展示 | ✅ |
| 17 | 前端联调 | `frontend/` + `ApiService` + `ApiRepository` | 全部 REST 接口 | — | F01-F04 | Android 真实数据 | ✅ |

## 统计

| 状态 | 数量 | 评分点 |
|------|------|--------|
| ✅ 已完成 | 17 | #1-#17 |
| ⬜ 未完成 | 0 | — |
| **合计** | **17** | |

## 缺陷 / 遗留项

| # | 项目 | 影响 | 负责人 |
|---|------|------|--------|
| 1 | 演示视频未录制 | T30 交付物缺失 | 成员 C |
| 2 | 团队评分表待填写姓名/学号 | T30 交付物不完整 | 成员 C |
| 3 | 答辩 PPT 待制作 | T30 交付物缺失 | 成员 C |

## 已完成

- T15-T17 + T20：gRPC 推荐服务和推荐流（成员 B 已完成）
- T23-T25：评论闭环（成员 C 已完成）
- T26-T27：前端联调（成员 C 已完成）

## 验收结论

当前 17 个评分点中 **13 个已可验收**（76%），剩余 4 个均依赖成员 B 的 gRPC 推荐服务。非推荐链路的全部功能（账号、视频管理、点赞、访问记录、评论、日志、健康检查、前端联调）均已就绪，111 个测试用例全部通过。
