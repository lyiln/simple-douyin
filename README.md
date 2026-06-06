# Simple Douyin

《API 设计与实现》课程大作业：简易版抖音 / 视频流推荐系统。

## 项目状态

当前已完成：

- Java 17 + Spring Boot + Maven 多模块后端基础结构。
- MySQL 8 初始化脚本。
- 统一响应、错误码、requestId、全局异常处理。
- 请求输入、输出和耗时日志，包含敏感字段脱敏。
- `POST /api/v1/auth/register` 注册接口。
- BCrypt 密码哈希。
- 注册成功后的无状态 HMAC access token。
- Android Compose 本地演示端。

当前尚未完成：

- 登录、退出和完整 Bearer Token 鉴权。
- 视频上传、我的视频、删除、点赞和访问记录。
- gRPC 推荐契约、推荐规则和推荐 REST 接口。
- 评论接口和前后端网络联调。

Recommend Service 目前只有 Maven/Spring Boot 模块边界，尚未监听 gRPC 端口。

## 架构

```text
Android Frontend
    -> RESTful API over HTTP/JSON
    -> Spring Boot API Server
    -> gRPC Recommend Service
    -> MySQL 8
```

API Server 和 Recommend Service 均可访问 MySQL 8。Android 端只访问 API Server。

## 技术栈

- Java 17
- Spring Boot 3.3
- Maven
- gRPC
- MySQL 8
- Android Jetpack Compose
- 本地 `uploads/` 视频目录

## 目录结构

```text
.
├── backend/
│   ├── api-server/          # REST API、账号、日志等
│   └── recommend-service/   # gRPC 推荐服务模块
├── docs/                    # 范围、API、数据库、RPC、任务和测试规划
├── frontend/                # Android Compose 项目
├── sql/schema.sql           # MySQL 8 初始化脚本
├── uploads/                 # 本地视频文件目录
└── pom.xml                  # Maven 聚合项目
```

## 环境要求

- JDK 17
- Maven 3.9+
- MySQL 8
- Android Studio（运行 Android Demo 时需要）
- Android SDK 35，设备或模拟器最低 Android 7.0 / API 24

确认本机版本：

```bash
java -version
mvn -version
mysql --version
```

如果本机同时安装了多个 JDK，请确保 Maven 使用 Java 17：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -version
```

## 当前启动流程

当前可验证的后端链路是：

```text
MySQL 8 -> Spring Boot API Server -> POST /api/v1/auth/register
```

### 1. 启动 MySQL 8

确保 MySQL 正在监听 `localhost:3306`。

macOS Homebrew 示例：

```bash
brew services start mysql
```

也可以使用已有的本地或远程 MySQL 8 实例。

### 2. 初始化数据库

在仓库根目录执行：

```bash
mysql -uroot -p < sql/schema.sql
```

脚本将创建 `simple_douyin` 数据库及以下表：

- `users`
- `videos`
- `video_likes`
- `video_views`
- `request_logs`
- `comments`

项目不创建 `auth_tokens` 和 `upload_objects`。

### 3. 配置环境变量

API Server 支持以下环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/simple_douyin?...` | MySQL JDBC 地址 |
| `MYSQL_USERNAME` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `AUTH_TOKEN_SECRET` | 开发默认值 | HMAC token 密钥，非本地环境必须覆盖 |
| `AUTH_TOKEN_EXPIRES_IN_SECONDS` | `7200` | access token 有效期 |
| `API_SERVER_PORT` | `8080` | API Server HTTP 端口 |
| `UPLOADS_DIR` | `uploads` | 本地视频目录 |
| `RECOMMEND_GRPC_HOST` | `localhost` | 推荐服务地址 |
| `RECOMMEND_GRPC_PORT` | `9090` | 推荐服务 gRPC 端口 |

本地开发示例：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD='your-mysql-password'
export AUTH_TOKEN_SECRET='replace-with-a-local-development-secret'
```

不要把真实数据库密码或 token 密钥提交到 Git。

### 4. 构建和测试

在仓库根目录执行：

```bash
mvn -q test
```

仅编译：

```bash
mvn -q -DskipTests compile
```

### 5. 启动 API Server

在仓库根目录执行：

```bash
mvn -pl backend/api-server spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

### 6. 验证注册接口

```bash
curl -i \
  -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: readme-register-001' \
  -d '{
    "username": "alice",
    "password": "Passw0rd!",
    "nickname": "Alice"
  }'
```

预期：

- HTTP 状态码为 `201`。
- 响应包含 `code`、`message`、`data`、`requestId`。
- `data` 包含 `user`、`accessToken`、`expiresIn`。
- `users.password_hash` 保存 BCrypt 哈希，不保存明文密码。
- `request_logs` 中记录请求、响应和耗时，password 和 token 显示为 `***`。

查看数据：

```bash
mysql -uroot -p simple_douyin
```

```sql
SELECT id, username, password_hash, nickname, status
FROM users;

SELECT request_id, method, path, request_body, response_body,
       status_code, business_code, duration_ms, created_at
FROM request_logs
ORDER BY id DESC
LIMIT 5;
```

## Recommend Service

模块位置：

```text
backend/recommend-service
```

配置预留：

| 变量 | 默认值 |
| --- | --- |
| `RECOMMEND_GRPC_PORT` | `9090` |
| `RECOMMEND_ADMIN_PORT` | `8081` |

当前模块尚未实现 proto、gRPC Server 和推荐查询，因此不参与注册接口验证。完成 T15-T17 后，完整启动顺序应为：

1. MySQL 8。
2. gRPC Recommend Service。
3. Spring Boot API Server。
4. Android Frontend。

## Android Demo

Android 项目位于 `frontend/`，当前主要用于本地页面和上下滑视频播放演示，尚未接入真实后端。

使用 Android Studio：

1. 打开 `frontend/`。
2. 等待 Gradle 同步完成。
3. 选择 API 24 以上的设备或模拟器。
4. 运行 `app`。

命令行构建：

```bash
cd frontend
./gradlew assembleDebug
```

APK 输出目录：

```text
frontend/app/build/outputs/apk/debug/
```

## 常见问题

### 无法连接 MySQL

检查 MySQL 服务、端口和环境变量：

```bash
mysql -h localhost -P 3306 -u root -p
```

确认已经执行 `sql/schema.sql`。

### 注册接口返回 500

优先检查：

- `simple_douyin` 数据库是否存在。
- `users` 和 `request_logs` 表是否创建。
- `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 是否正确。

### 用户名重复

`users.username` 有唯一索引。重复注册返回 HTTP `409` 和业务码 `40901`。

### Maven 使用了错误的 Java 版本

项目编译目标固定为 Java 17。重新设置 `JAVA_HOME` 后再运行 Maven。

## 设计文档

- [最终范围](docs/scope-final.md)
- [REST API 契约](docs/api-contract-final.md)
- [数据库设计](docs/database-design.md)
- [RPC 设计](docs/rpc-design.md)
- [后端模块规划](docs/backend-module-plan.md)
- [任务拆分](docs/task-breakdown.md)
- [测试计划](docs/test-plan.md)

课程要求和 `docs/` 下的最终规划优先于 `frontend/docs/API_DESIGN.md`。
