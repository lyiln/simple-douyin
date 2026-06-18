# Test Plan

## 1. 测试范围

固定架构：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

Core P0 测试覆盖：账号、`GET /me`、推荐、浏览过滤、点赞、本地 `uploads/` 发布、我的视频分页、删除权限、日志和 `GET /health`。

P0-lite 测试覆盖：评论列表和发表评论，排在 Core P0 测试之后，但属于最终演示和交付必做测试。

`POST /media-upload-tokens`、`GET /metrics`、收藏、关注、分享、消息、搜索为 Bonus，不纳入主线验收。

## 2. Core P0 正常用例

| 编号 | 用例 | 前置条件 | 步骤 | 预期 |
| --- | --- | --- | --- | --- |
| N01 | 注册成功 | 用户名不存在 | 调 `POST /auth/register` | 返回 201、用户信息和 token；users 有记录 |
| N02 | 登录成功 | 用户已注册 | 调 `POST /auth/login` | 返回 200 和 token |
| N03 | 退出成功 | 已登录 | 调 `POST /auth/logout`，客户端删除 token | 返回 200；服务端不依赖 `auth_tokens` |
| N04 | 获取当前用户 | 已登录 | 调 `GET /me` | 返回当前用户资料和基础统计 |
| N05 | multipart 发布视频 | 已登录，有合法视频文件 | 调 `POST /videos` | 返回 201；文件保存到 `uploads/`；videos 有当前用户记录 |
| N06 | 开发期 URL 发布 | 已登录，有可用 `videoUrl` | 调开发期 JSON 版本 `POST /videos` | 返回 201；videos 写入 URL |
| N07 | 查看我的视频分页 | 当前用户有多条视频 | 调 `GET /me/videos?limit=2` | 返回 2 条和 nextCursor；下一页无重复 |
| N08 | 删除自己的视频 | 当前用户有视频 | 调 `DELETE /videos/{videoId}` | 返回 200；视频软删除 |
| N09 | 重复删除 | 视频已软删除 | 再次调用删除接口 | 返回 200，幂等成功 |
| N10 | 点赞视频 | 已登录，视频存在 | 调 `PUT /videos/{videoId}/likes/me` | 返回 liked=true；like_count +1 |
| N11 | 取消点赞 | 已点赞 | 调 `DELETE /videos/{videoId}/likes/me` | 返回 liked=false；like_count -1 |
| N12 | 推荐流 | 有公开视频 | 调 `GET /feeds/recommended/videos` | 返回按点赞数降序的视频列表 |
| N13 | 记录访问 | 前端切换到视频并开始展示 | 调 `POST /videos/{videoId}/views/me` | 返回 viewed=true；video_views 有记录 |
| N14 | 健康检查 | 服务启动 | 调 `GET /health` | 返回 API Server、MySQL 8、gRPC Recommend Service 状态 |
| N15 | 重置推荐历史 | 当前用户已有访问记录 | 调 `POST /feeds/recommended/reset` | 返回 reset=true 和 clearedCount；已访问视频可再次进入推荐流 |
| N16 | 重置单视频推荐历史 | 当前用户已访问多个视频 | 调 `POST /feeds/recommended/videos/{videoId}/reset` | 只删除当前用户该视频访问记录，返回 videoId/reset/clearedCount |

## 3. 幂等与数据一致性用例

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| I01 | 重复点赞 | 连续两次调用点赞接口 | 两次均返回 200；`video_likes` 只有一条关系；`like_count` 只增加一次 |
| I02 | 重复取消点赞 | 连续两次调用取消点赞接口 | 两次均返回 200；关系不存在；`like_count` 不为负且只减少一次 |
| I03 | 重复记录访问 | 同一用户重复调用访问接口 | `(user_id, video_id)` 只有一条记录；推荐过滤稳定生效 |
| I04 | 重复删除 | 对已删除的本人视频再次删除 | 返回 200，不产生额外副作用 |

## 4. Core P0 异常用例

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| E01 | 注册用户名为空 | `POST /auth/register` username 为空 | 400，`40002` |
| E02 | 注册用户名重复 | 用已存在 username 注册 | 409，`40901` |
| E03 | 登录密码错误 | `POST /auth/login` 错误密码 | 401，`40101` |
| E04 | 推荐分页参数非法 | `limit=1000` | 400，`40001` |
| E05 | 发布标题为空 | `POST /videos` caption 为空 | 400，`40002` |
| E06 | 上传文件过大 | multipart 上传超大视频 | 413，`41301` |
| E07 | 上传文件类型非法 | multipart 上传不支持类型 | 400，`40001` |
| E08 | 点赞不存在视频 | `PUT /videos/unknown/likes/me` | 404，`40401` |
| E09 | 访问不存在视频 | `POST /videos/unknown/views/me` | 404，`40401` |
| E10 | gRPC 推荐服务不可用 | 停止 Recommend Service 后请求推荐流 | 500，`50001`，日志记录 gRPC 异常 |

## 5. 权限测试

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| P01 | 未登录访问推荐流 | 不带 token 调推荐接口 | 401 |
| P02 | 未登录发布视频 | 不带 token 调 `POST /videos` | 401 |
| P03 | 未登录查看我的视频 | 不带 token 调 `GET /me/videos` | 401 |
| P04 | 未登录点赞 | 不带 token 调点赞接口 | 401 |
| P05 | 删除他人视频 | 用户 A 删除用户 B 视频 | 403，`40301` |
| P06 | 我的列表隔离 | 用户 A 调 `GET /me/videos` | 不出现用户 B 的视频 |
| P07 | 未登录查看评论 | 不带 token 调评论列表 | 401 |
| P08 | 未登录发表评论 | 不带 token 提交评论 | 401 |
| P09 | 未登录重置推荐历史 | 不带 token 调 `POST /feeds/recommended/reset` | 401 |
| P10 | 未登录重置单视频推荐历史 | 不带 token 调 `POST /feeds/recommended/videos/{videoId}/reset` | 401 |

## 6. 推荐规则测试

| 编号 | 数据准备 | 步骤 | 预期 |
| --- | --- | --- | --- |
| R01 | 三条视频点赞数 100、50、10 | 请求推荐流 | 返回顺序为 100、50、10 |
| R02 | 两条视频点赞数相同，发布时间不同 | 请求推荐流 | 新视频排在前面 |
| R03 | 当前用户已访问高赞视频 | 请求推荐流 | 已访问视频不返回，返回下一条 |
| R04 | 当前用户访问全部视频 | 请求推荐流 | 返回空 `items`、`hasMore=false` |
| R05 | 第一页 limit=2 | 请求第一页和第二页 | 两页无重复，排序连续 |
| R06 | 删除一条高赞视频 | 请求推荐流 | 已删除视频不返回 |
| R07 | 私密视频高赞 | 请求推荐流 | `visibility=private` 不返回 |
| R08 | REST 是否调用 gRPC | 观察日志或 mock gRPC | 推荐 REST 产生 `RecommendService.ListRecommendedVideos` 调用 |
| R09 | 重置推荐历史 | 当前用户访问全部视频后调用 reset | 仅当前用户的 `video_views` 被删除，推荐流重新返回这些视频 |
| R10 | 重置单视频推荐历史 | 当前用户访问多个视频后调用单视频 reset | 仅指定视频可重新进入当前用户推荐流，其他已访问视频仍被过滤 |

## 7. 日志测试

| 编号 | 用例 | 验收 |
| --- | --- | --- |
| L01 | 成功请求日志 | `request_logs` 记录 requestId、userId、method、path、query、request_body、response_body、status_code、business_code、duration_ms、error_message、created_at |
| L02 | 未登录请求日志 | userId 为空，但 path、status_code=401、duration_ms 存在 |
| L03 | 异常请求日志 | 错误响应也记录 response_body 和 error_message |
| L04 | 敏感字段脱敏 | 登录/注册日志中 password 和 token 不得明文出现 |
| L05 | 接口耗时 | 每条日志 `duration_ms > 0`，推荐接口额外可记录 gRPC 耗时 |
| L06 | requestId 串联 | 响应体 requestId 与 request_logs.request_id 一致 |
| L07 | 监控聚合 | 从 `request_logs` 按 path 聚合请求数、错误数、平均耗时和最大耗时，结果可用于答辩展示 |

## 8. 健康检查测试

| 编号 | 用例 | 预期 |
| --- | --- | --- |
| H01 | 全部组件正常 | `GET /health` 返回 `UP` |
| H02 | MySQL 8 连接异常 | 健康检查反映 mysql 不可用 |
| H03 | gRPC 推荐服务异常 | 健康检查反映 recommendService 不可用 |
| H04 | 接口耗时统计 | 可从 request_logs 按 path 聚合平均耗时、最大耗时 |

`GET /metrics` 不属于 P0，不纳入测试。

## 9. P0-lite 评论测试（最终演示必做）

评论测试在 Core P0 全部通过后执行，但必须在前端最终联调和演示录屏前通过。

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| C01 | 获取评论列表 | `GET /videos/{videoId}/comments` | 返回分页评论和 commentCount |
| C02 | 发表评论 | `POST /videos/{videoId}/comments` 提交合法内容 | 返回 201；comments 有记录；comment_count +1 |
| C03 | 未登录发表评论 | 不带 token 发表评论 | 返回 401 |
| C04 | 评论内容为空 | content 为空 | 返回 400，`40002` |
| C05 | 评论内容过长 | content 超过 300 字 | 返回 400，`40003` |
| C06 | 评论不存在视频 | 对不存在视频发表评论 | 返回 404，`40401` |

## 10. 数据库与存储测试

| 编号 | 用例 | 验收 |
| --- | --- | --- |
| D01 | MySQL 8 schema 初始化 | `sql/schema.sql` 可执行，创建 `users`、`videos`、`video_likes`、`video_views`、`request_logs`、`comments` |
| D02 | 唯一约束 | 用户名、点赞关系、访问关系、requestId 唯一约束生效 |
| D03 | 推荐与分页索引 | 推荐排序、我的视频分页、评论分页的索引与设计一致 |
| D04 | 非必做表检查 | 不创建 `auth_tokens`；不把 `upload_objects` 作为 P0 |
| S01 | 本地视频可访问 | multipart 发布后文件保存在 `uploads/`，返回 URL 可被 Android 播放 |

## 11. 前端演示链路测试

| 编号 | 场景 | 步骤 | 预期 |
| --- | --- | --- | --- |
| F01 | 推荐主场景 | 登录 -> 拉取推荐 -> 上下滑动 -> 点赞 -> 查看评论 -> 提交评论 -> 重置推荐 | 真实接口完成完整链路；切换展示时记录访问；刷新后已访问视频不再出现；重置后视频可再次出现 |
| F02 | 视频管理主场景 | 注册或登录 -> multipart 发布 -> 我的列表分页 -> 点击我的作品回主页播放 -> 删除自己的视频 | 发布文件可播放；列表仅显示本人数据；点击作品会单视频重置推荐过滤并在主页播放；删除成功且刷新后消失 |
| F03 | 权限演示 | 未登录访问业务接口；用户 A 删除用户 B 视频 | 分别返回 401 和 403 |
| F04 | 后台能力演示 | 调推荐、登录等接口后查看日志和 health | 可展示输入输出、耗时、脱敏、gRPC 调用和三个组件健康状态 |

## 12. 作业材料验收

| 编号 | 交付项 | 验收 |
| --- | --- | --- |
| A01 | Git 项目管理 | Git 地址公开，提交历史可追溯，`.gitignore` 忽略 target、IDE、编译产物和本地上传文件 |
| A02 | README | 最终代码包包含 README，部署步骤可复现 |
| A03 | 需求文档 | 覆盖全部课程评分点和两个主要场景 |
| A04 | 技术设计文档 | 覆盖架构、REST、gRPC、MySQL、uploads、日志、安全 |
| A05 | 测试文档 | 包含 case 设计逻辑、case 列表、执行结果 |
| A06 | 答辩 PPT | 可在 8 分钟内讲清需求、架构、实现、测试和结果 |
| A07 | 团队分工与评分 | 有工作分工、工作量占比和项目成员内部分数评定表 |
| A08 | 演示视频 | 约 2 分钟，覆盖 F01、F02 和关键后台证据 |
| A09 | 最终代码包 | 与公开 Git 最终版本一致，包含所需文档与 README |

## 14. 测试执行结果

**执行日期：** 2026-06-12
**执行命令：** `$env:MYSQL_PASSWORD="***"; mvn test`
**总体结果：BUILD SUCCESS**

```
Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
```

### 14.1 测试类明细

| 测试类 | 用例数 | 结果 | 覆盖内容 |
|--------|--------|------|----------|
| `AuthControllerTest` | 9 | ✅ | 注册、登录、退出正常/异常/日志 |
| `AuthServiceTest` | 11 | ✅ | 注册/登录逻辑、密码验证、token |
| `UserControllerTest` | 4 | ✅ | 当前用户、他人主页 |
| `UserProfileRepositoryTest` | 1 | ✅ | 用户数据查询 |
| `UserServiceTest` | 3 | ✅ | 用户业务逻辑 |
| `VideoControllerTest` | 6 | ✅ | 发布/我的视频/删除正常/异常 |
| `VideoRepositoryTest` | 14 | ✅ | 视频/点赞/访问/删除数据层 |
| `VideoServiceTest` | 10 | ✅ | 发布/分页/删除业务逻辑 |
| `LikeControllerTest` | 13 | ✅ | 点赞/取消正常、幂等、404、权限、日志 |
| `ViewControllerTest` | 10 | ✅ | 访问记录、首次/重复、404、权限、日志 |
| `CommentControllerTest` | 16 | ✅ | 发表评论/评论列表、分页、空列表、404、权限、日志 |
| `HealthControllerTest` | 6 | ✅ | 全UP/部分DOWN、无鉴权、日志 |
| `LocalUploadStorageServiceTest` | 6 | ✅ | 文件保存/读取/类型校验 |
| `UploadStoragePropertiesTest` | 1 | ✅ | 配置注入 |
| `UploadWebMvcConfigTest` | 1 | ✅ | 静态资源映射 |
| **合计** | **111** | **✅ 全通过** | |

### 14.2 用例覆盖状态

| 编号 | 用例 | 测试类/方法 | 状态 |
|------|------|------------|------|
| N01 | 注册成功 | AuthControllerTest | ✅ |
| N02 | 登录成功 | AuthControllerTest | ✅ |
| N03 | 退出成功 | AuthControllerTest | ✅ |
| N04 | 获取当前用户 | UserControllerTest | ✅ |
| N05 | multipart 发布视频 | VideoControllerTest | ✅ |
| N06 | 开发期 URL 发布 | VideoControllerTest | ✅ |
| N07 | 查看我的视频分页 | VideoControllerTest / VideoServiceTest | ✅ |
| N08 | 删除自己的视频 | VideoControllerTest / VideoServiceTest | ✅ |
| N09 | 重复删除 | VideoServiceTest | ✅ |
| N10 | 点赞视频 | LikeControllerTest | ✅ |
| N11 | 取消点赞 | LikeControllerTest | ✅ |
| N12 | 推荐流 | FeedControllerTest | ✅ |
| N13 | 记录访问 | ViewControllerTest | ✅ |
| N14 | 健康检查 | HealthControllerTest | ✅ |
| I01 | 重复点赞 | LikeControllerTest / VideoRepositoryTest | ✅ |
| I02 | 重复取消点赞 | LikeControllerTest / VideoRepositoryTest | ✅ |
| I03 | 重复记录访问 | ViewControllerTest / VideoRepositoryTest | ✅ |
| I04 | 重复删除 | VideoRepositoryTest | ✅ |
| E01-E10 | 异常用例 | 各 Controller Test | ✅ |
| P01-P08 | 权限测试 | LikeControllerTest / ViewControllerTest / CommentControllerTest | ✅ |
| R01-R08 | 推荐规则测试 | RecommendRepositoryTest / FeedControllerTest | ✅ |
| L01-L07 | 日志测试 | LikeControllerTest / ViewControllerTest / CommentControllerTest / HealthControllerTest | ✅ |
| H01-H04 | 健康检查测试 | HealthControllerTest | ✅ |
| C01-C06 | 评论测试 | CommentControllerTest | ✅ |
| D01-D04 | 数据库测试 | VideoRepositoryTest (真实 MySQL) | ✅ |
| F01-F04 | 前端演示链路 | Android 端到端 + Web 补充端 | ✅ |

### 14.3 环境信息

| 项目 | 版本 |
|------|------|
| Java | 21.0.11 |
| Spring Boot | 3.3.6 |
| Maven | 3.9.6 |
| MySQL | 9.7 |
| JUnit | 5 (via Spring Boot) |
| Mockito | via spring-boot-starter-test |

> **说明：** 推荐流 (N12) 和推荐规则测试 (R01-R08, T20) 由成员 B 完成（FeedControllerTest 10 用例 + RecommendRepositoryTest 7 用例）。前端演示链路 (F01-F04) 由成员 C 完成 Android/Web 联调。评论 (C01-C06) 由成员 C 完成。
