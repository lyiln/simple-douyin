# Test Plan

## 1. 测试范围

固定架构：

```text
Android Frontend -> RESTful API over HTTP/JSON -> Spring Boot API Server -> gRPC Recommend Service -> MySQL 8
```

Core P0 测试覆盖：账号、`GET /me`、推荐、浏览过滤、点赞、本地 `uploads/` 发布、我的视频分页、删除权限、日志和 `GET /health`。

P0-lite 测试覆盖：评论列表和发表评论，排在 Core P0 测试之后。

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

## 3. Core P0 异常用例

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

## 4. 权限测试

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| P01 | 未登录访问推荐流 | 不带 token 调推荐接口 | 401 |
| P02 | 未登录发布视频 | 不带 token 调 `POST /videos` | 401 |
| P03 | 未登录查看我的视频 | 不带 token 调 `GET /me/videos` | 401 |
| P04 | 未登录点赞 | 不带 token 调点赞接口 | 401 |
| P05 | 删除他人视频 | 用户 A 删除用户 B 视频 | 403，`40301` |
| P06 | 我的列表隔离 | 用户 A 调 `GET /me/videos` | 不出现用户 B 的视频 |

## 5. 推荐规则测试

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

## 6. 日志测试

| 编号 | 用例 | 验收 |
| --- | --- | --- |
| L01 | 成功请求日志 | `request_logs` 记录 requestId、userId、method、path、request_body、response_body、status_code、duration_ms |
| L02 | 未登录请求日志 | userId 为空，但 path、status_code=401、duration_ms 存在 |
| L03 | 异常请求日志 | 错误响应也记录 response_body 和 error_message |
| L04 | 敏感字段脱敏 | 登录/注册日志中 password 和 token 不得明文出现 |
| L05 | 接口耗时 | 每条日志 `duration_ms > 0`，推荐接口额外可记录 gRPC 耗时 |
| L06 | requestId 串联 | 响应体 requestId 与 request_logs.request_id 一致 |

## 7. 健康检查测试

| 编号 | 用例 | 预期 |
| --- | --- | --- |
| H01 | 全部组件正常 | `GET /health` 返回 `UP` |
| H02 | MySQL 8 连接异常 | 健康检查反映 mysql 不可用 |
| H03 | gRPC 推荐服务异常 | 健康检查反映 recommendService 不可用 |
| H04 | 接口耗时统计 | 可从 request_logs 按 path 聚合平均耗时、最大耗时 |

`GET /metrics` 不属于 P0，不纳入测试。

## 8. P0-lite 评论测试

评论测试在 Core P0 全部通过后执行。

| 编号 | 用例 | 步骤 | 预期 |
| --- | --- | --- | --- |
| C01 | 获取评论列表 | `GET /videos/{videoId}/comments` | 返回分页评论和 commentCount |
| C02 | 发表评论 | `POST /videos/{videoId}/comments` 提交合法内容 | 返回 201；comments 有记录；comment_count +1 |
| C03 | 未登录发表评论 | 不带 token 发表评论 | 返回 401 |
| C04 | 评论内容为空 | content 为空 | 返回 400，`40002` |
| C05 | 评论内容过长 | content 超过 300 字 | 返回 400，`40003` |
| C06 | 评论不存在视频 | 对不存在视频发表评论 | 返回 404，`40401` |

## 9. 回归测试清单

| 回归项 | 原因 |
| --- | --- |
| 点赞幂等 | 防止计数重复增加 |
| 访问过滤 | 防止推荐已看过视频 |
| 删除权限和删除幂等 | 防止越权删除和重复删除异常 |
| 我的分页 | 防止串用户数据 |
| 本地 uploads | 防止发布后文件不可访问 |
| 日志脱敏 | 防止密码和 token 泄漏 |
| gRPC 调用 | 防止推荐接口绕过 Recommend Service |
| 评论计数 | 防止评论列表和 videos.comment_count 不一致 |
