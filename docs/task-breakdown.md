# Task Breakdown

## 1. 开发顺序

任务按依赖顺序排列。每个任务只完成一个清晰交付物，避免先做 Bonus。

## 2. 任务列表

| 序号 | 任务 | 输入 | 输出 | 验收标准 |
| --- | --- | --- | --- | --- |
| T01 | 确认技术栈和目录结构 | 当前仓库、课程要求 | 后端目录结构和 README 草案 | 明确 API Server、Recommend Service、MySQL、视频存储方式 |
| T02 | 建立数据库 schema | `docs/database-design.md` | MySQL migration 或初始化 SQL | users、videos、video_likes、video_views、request_logs 可创建成功 |
| T03 | 实现统一响应和错误码 | `docs/api-contract-final.md` | 统一响应模型 | 所有接口返回 `code/message/data/requestId` |
| T04 | 实现请求日志中间件 | 日志要求、`request_logs` | 请求日志写入 | 每个请求记录输入、输出、状态码、耗时、userId/requestId |
| T05 | 实现账号注册 | users 表 | `POST /auth/register` | 新用户可注册；用户名重复返回 409；密码非明文存储 |
| T06 | 实现登录和 token | users 表、密码哈希 | `POST /auth/login` | 正确密码返回 token；错误密码返回 401 |
| T07 | 实现退出和鉴权中间件 | token 方案 | `POST /auth/logout`、鉴权 | 未登录访问 P0 接口返回 401 |
| T08 | 实现当前用户信息 | users、videos | `GET /me` | 返回当前用户基本信息和视频数量 |
| T09 | 实现视频存储凭证 | `upload_objects` | `POST /media-upload-tokens` | 登录用户可申请 video/cover 上传凭证，文件类型和大小受限 |
| T10 | 实现发布视频 | upload_objects、videos | `POST /videos` | 发布后 videos 有记录，作者为当前用户 |
| T11 | 实现我的视频分页 | videos | `GET /me/videos` | 只返回当前用户视频，按发布时间倒序，支持 cursor/limit |
| T12 | 实现删除视频权限 | videos | `DELETE /videos/{videoId}` | 删除自己视频成功；删除他人视频返回 403 |
| T13 | 实现点赞 / 取消点赞 | video_likes、videos | `PUT/DELETE /videos/{videoId}/likes/me` | 幂等；like_count 正确变化 |
| T14 | 实现访问记录 | video_views、videos | `POST /videos/{videoId}/views/me` | 访问记录幂等写入；view_count 合理更新 |
| T15 | 实现 RecommendService RPC 契约 | `docs/rpc-design.md` | RPC 接口定义 | API Server 可调用 RPC 方法 |
| T16 | 实现推荐规则 | videos、video_views | RecommendService 返回 videoIds | 按 `like_count desc`，排除已访问视频 |
| T17 | 实现推荐流 REST | RPC 返回 videoIds | `GET /feeds/recommended/videos` | REST 调 RPC，并补齐视频详情和 viewerState |
| T18 | 实现健康检查 | DB、RPC 状态 | `GET /health` | 可看到 API Server、DB、Recommend Service 状态 |
| T19 | 编写接口测试 | API 契约 | Postman/Apifox 或自动化测试 | P0 接口正常/异常用例通过 |
| T20 | 编写推荐规则测试 | 推荐规则 | 推荐测试数据和用例 | 验证排序、过滤、分页 |
| T21 | 编写权限测试 | 鉴权和删除规则 | 权限测试用例 | 未登录 401；删除他人视频 403 |
| T22 | 编写日志测试 | request_logs | 日志测试用例 | 每个接口均有输入、输出、耗时记录 |
| T23 | 前端接入账号和推荐流 | Android Demo、API 契约 | 登录页、推荐流网络数据 | 可登录后刷真实推荐视频 |
| T24 | 前端接入发布、我的视频、删除 | Android Demo、API 契约 | 发布/我的视频/删除联调 | 可发布、分页查看、删除自己的视频 |
| T25 | 编写 README 和交付文档 | 所有设计和测试结果 | README、需求、技术设计、测试文档 | README 可按步骤部署；文档覆盖课程清单 |
| T26 | 制作答辩 PPT 和演示视频 | 完成的系统 | PPT、演示视频 | 能演示 P0 闭环和日志/RPC/数据库设计 |

## 3. 并行建议

| 并行组 | 成员 A | 成员 B | 成员 C |
| --- | --- | --- | --- |
| 第一轮 | 数据库 schema | 账号和鉴权 | 文档整理 |
| 第二轮 | 视频发布/删除 | 点赞/访问记录 | RPC 契约 |
| 第三轮 | 推荐服务 | 前端联调 | 测试用例 |
| 第四轮 | 日志监控 | README/PPT | 演示视频 |

## 4. 每阶段里程碑

| 阶段 | 结束标准 |
| --- | --- |
| M1 基础后端 | 注册、登录、鉴权、日志、数据库可用 |
| M2 视频管理 | 发布、我的视频分页、删除权限可用 |
| M3 推荐闭环 | RPC 推荐、访问过滤、点赞排序可用 |
| M4 前端联调 | Android Demo 可演示 P0 主流程 |
| M5 交付 | README、测试文档、PPT、演示视频齐全 |

## 5. 推荐的第一个编码任务

第一个编码任务建议从 T01 + T02 开始：创建后端基础目录、配置 MySQL、落地数据库 schema。理由是账号、视频、点赞、浏览记录、日志、推荐服务全部依赖这些表；先把 schema 定稳，后续接口才不会来回返工。
