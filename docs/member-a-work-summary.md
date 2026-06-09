# 成员A 工作文档

> 姓名：________  学号：________  日期：2026-06-08

## 1. 职责范围

根据团队分工，成员A负责 **API Server 核心交互能力**，涵盖点赞/取消点赞、访问记录、健康检查三类接口，以及对应的核心测试、权限测试和日志测试。

| 任务 | 内容 | 优先级 |
|------|------|--------|
| T13 | 点赞 / 取消点赞 | Core P0 |
| T14 | 访问记录 | Core P0 |
| T18 | 健康检查 | Core P0 |
| T19 | 核心接口测试 | Core P0 |
| T21 | 权限测试 | Core P0 |
| T22 | 日志测试 | Core P0 |

## 2. 成果概览

分支：`feature/member-a-like-view-health`，PR #2 → `lyiln/simple-douyin:main`

| 维度 | 数据 |
|------|------|
| 修改/新增文件 | 16 个（+1210 行 / −73 行） |
| 新增接口 | 4 个 REST 端点 |
| 新增 DTO | 3 个（LikeResponse、ViewRequest、ViewResponse） |
| 新增 Service | 1 个（HealthService） |
| 新增 Controller | 1 个（HealthController） |
| 新增测试类 | 3 个（26 个用例） |
| 全量测试 | 78 个，全部通过 |

## 3. 接口清单

所有接口前缀：`/api/v1`

### 3.1 点赞：PUT /videos/{videoId}/likes/me

- **鉴权**：需要 Bearer Token
- **幂等策略**：`INSERT IGNORE INTO video_likes`，重复调用不影响 like_count
- **响应**：`200` — `{ videoId, liked: true, likeCount }`
- **异常**：`401`（未登录）、`404`（视频不存在）
- **关键代码**：[VideoController.java:87](backend/api-server/src/main/java/com/simpledouyin/api/video/controller/VideoController.java#L87)、[VideoService.java:152](backend/api-server/src/main/java/com/simpledouyin/api/video/service/VideoService.java#L152)、[VideoRepository.java:250](backend/api-server/src/main/java/com/simpledouyin/api/video/repository/VideoRepository.java#L250)

```
PUT /api/v1/videos/{videoId}/likes/me
Authorization: Bearer <token>

Response 200:
{
  "code": 0,
  "message": "ok",
  "data": {
    "videoId": 2001,
    "liked": true,
    "likeCount": 5
  },
  "requestId": "req_xxx"
}
```

### 3.2 取消点赞：DELETE /videos/{videoId}/likes/me

- **鉴权**：需要 Bearer Token
- **幂等策略**：`DELETE FROM video_likes`，受影响行数 > 0 才递减 like_count
- **防负**：`UPDATE ... SET like_count = like_count - 1 WHERE ... AND like_count > 0`
- **异常**：同点赞
- **关键代码**：[VideoController.java:95](backend/api-server/src/main/java/com/simpledouyin/api/video/controller/VideoController.java#L95)、[VideoService.java:167](backend/api-server/src/main/java/com/simpledouyin/api/video/service/VideoService.java#L167)、[VideoRepository.java:264](backend/api-server/src/main/java/com/simpledouyin/api/video/repository/VideoRepository.java#L264)

### 3.3 访问记录：POST /videos/{videoId}/views/me

- **鉴权**：需要 Bearer Token
- **幂等策略**：`INSERT ... ON DUPLICATE KEY UPDATE`，首次插入递增 view_count
- **响应**：首次 `201`，重复 `200` — `{ videoId, viewed: true, viewCount }`
- **请求体**：`{ "source": "recommended_feed", "watchDurationMs": 5000 }`
- **异常**：`401`、`404`
- **关键代码**：[VideoController.java:103](backend/api-server/src/main/java/com/simpledouyin/api/video/controller/VideoController.java#L103)、[VideoService.java:182](backend/api-server/src/main/java/com/simpledouyin/api/video/service/VideoService.java#L182)、[VideoRepository.java:280](backend/api-server/src/main/java/com/simpledouyin/api/video/repository/VideoRepository.java#L280)

### 3.4 健康检查：GET /health

- **鉴权**：不需要
- **检查项**：
  - `apiServer` — 始终 UP（能执行即运行中）
  - `mysql` — 执行 `SELECT 1` 验证数据库连接
  - `recommendService` — 通过 gRPC `ManagedChannel.getState()` 检测连通性
- **响应**：`200` — `{ status, components: { apiServer, mysql, recommendService } }`
- **关键代码**：[HealthController.java](backend/api-server/src/main/java/com/simpledouyin/api/health/controller/HealthController.java)、[HealthService.java](backend/api-server/src/main/java/com/simpledouyin/api/health/service/HealthService.java)

```
GET /api/v1/health

Response 200:
{
  "code": 0,
  "data": {
    "status": "UP",
    "components": {
      "apiServer": "UP",
      "mysql": "UP",
      "recommendService": "DOWN"
    }
  }
}
```

## 4. 代码结构

新增和修改的文件按分层架构组织：

```
backend/api-server/src/main/java/com/simpledouyin/api/
├── auth/
│   ├── security/BearerAuthenticationFilter.java    ← 扩展鉴权路径
│   └── token/HmacTokenService.java                 ← 加 @Autowired
├── health/                                         ← 新增模块
│   ├── controller/HealthController.java
│   ├── dto/HealthResponse.java
│   └── service/HealthService.java
├── video/
│   ├── controller/VideoController.java             ← 加4个端点
│   ├── dto/
│   │   ├── LikeResponse.java                       ← 新增
│   │   ├── ViewRequest.java                        ← 新增
│   │   └── ViewResponse.java                       ← 新增
│   ├── repository/VideoRepository.java             ← 加9个数据方法
│   └── service/VideoService.java                   ← 加3个业务方法
└── logging/                                        ← 无改动

backend/api-server/src/test/java/.../
├── health/controller/HealthControllerTest.java     ← 新增（6 cases）
└── video/controller/
    ├── LikeControllerTest.java                     ← 新增（12 cases）
    └── ViewControllerTest.java                     ← 新增（8 cases）
```

## 5. 设计要点

### 5.1 幂等实现

| 操作 | SQL 策略 | 首次行为 | 重复行为 |
|------|----------|----------|----------|
| 点赞 | `INSERT IGNORE` | 插入行，like_count +1 | 忽略，like_count 不变 |
| 取消点赞 | `DELETE` + 判断 affected rows | 删除行，like_count −1 | 0行影响，like_count 不变 |
| 记录访问 | `INSERT ... ON DUPLICATE KEY UPDATE` | 插入行，view_count +1 | 更新时间，view_count 不变 |

所有操作在同一个 `@Transactional` 方法中完成，保证计数一致性。

### 5.2 鉴权扩展

`BearerAuthenticationFilter` 原有路径白名单只覆盖到 T12（删除视频）。本次将点赞、取消点赞、访问记录三个端点的路径模式加入 `requiresAuthentication()` 方法，通过统一的 `isVideoActionPath(path, suffix)` 方法匹配。

### 5.3 gRPC 健康检测

健康检查通过 `ManagedChannelBuilder` 创建临时 Channel，使用 `getState(true)` 触发连接尝试，检测完成后立即关闭，避免资源泄漏。超时控制在 2 秒内。

### 5.4 启动修复

`HmacTokenService` 有两个构造函数（生产用三参数 + 测试用四参数），Spring 在无 `@Autowired` 时无法确定使用哪个构造函数，导致 `No default constructor found` 错误。在三参数构造函数上添加 `@Autowired` 解决。

## 6. 测试覆盖

| 测试类 | 用例数 | 覆盖场景 |
|--------|--------|----------|
| `LikeControllerTest` | 12 | 点赞正常、取消正常、重复点赞幂等、重复取消幂等、视频不存在 404、未登录 401、无效 token 401、日志 requestId、日志 userId、日志 userId 为 null、敏感字段不泄漏 |
| `ViewControllerTest` | 8 | 首次访问 201、重复访问 200、无请求体默认值、视频不存在 404、未登录 401、无效 token 401、日志记录正确、未登录日志正确 |
| `HealthControllerTest` | 6 | 全组件 UP、MySQL DOWN、gRPC DOWN、不需鉴权、日志记录、无敏感数据 |

### T21 权限测试要点

- 所有需鉴权接口在无 token 时返回 `401 / 40101`
- 无效 token 同样返回 `401 / 40101`
- 未登录时 `request_logs.user_id` 为 NULL

### T22 日志测试要点

- 每条请求日志包含：`requestId`、`userId`、`method`、`path`、`statusCode`、`businessCode`、`durationMs`（> 0）
- 响应体不含 `token`、`password`、`accessToken` 等敏感字段
- 未登录请求的 `userId` 为 null，`statusCode` 为 401

## 7. 运行验证

```powershell
# 全量测试
mvn -q test
# 结果：78 tests, 0 failures

# 启动服务
$env:MYSQL_PASSWORD="password"; mvn -pl backend/api-server clean spring-boot:run

# 健康检查
curl http://localhost:8080/api/v1/health
# → {"code":0,"data":{"status":"UP","components":{"apiServer":"UP","mysql":"UP","recommendService":"DOWN"}}}

# 注册 → 登录 → 点赞 → 取消 → 访问记录
curl -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"pass123","nickname":"Alice"}'
# 然后用返回的 token 测试点赞/取消/访问接口
```

## 8. 文档更新

- [docs/progress.md](docs/progress.md) — 全量 31 任务追踪，T13/T14/T18/T19/T21/T22 标记完成
- [README.md](README.md) — 进度表、接口表、测试清单、运行命令同步更新
