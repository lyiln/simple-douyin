# Member B (leaf) 工作计划

日期：2026-06-10

## 1. 当前项目状态

| 已完成 | 负责成员 | 说明 |
|---|---|---|
| T01-T12（基础设施、账号、视频管理） | 共享 | Maven 多模块、MySQL Schema、注册/登录/退出、`GET /me`、发布视频、我的视频分页、删除视频、统一响应/日志/鉴权 |
| T13 点赞、T14 访问记录、T18 健康检查 | 成员 A | 已合并到 main（PR #2） |

| 成员 B 依赖 | 状态 |
|---|---|
| `video_likes` 表与 `like()` / `unlike()` | ✅ 成员 A 已实现（T13） |
| `video_views` 表与 `recordView()` | ✅ 成员 A 已实现（T14） |
| `GrpcConfig`（单例 `ManagedChannel` Bean） | ✅ 成员 A 已实现 |
| `BearerAuthenticationFilter` 路径匹配 | ✅ 已扩展，B 需追加 feed 路径 |

---

## 2. 任务清单

| 任务 | 内容 | 优先级 | 预计工作量 |
|---|---|---|---|
| T15 | gRPC 推荐契约（`.proto` + Maven 插件 + 代码生成） | Core P0 | 半天 |
| T16 | 推荐规则实现（Recommend Service：gRPC Server + SQL + cursor） | Core P0 | 一天 |
| T17 | 推荐流 REST 端点（API Server：gRPC Client + 详情补全 + Controller） | Core P0 | 一天 |
| T20 | 推荐规则测试（R01-R08） | Core P0 | 半天 |

---

## 3. T15：gRPC 推荐契约

### 3.1 目标

创建 `.proto` 文件并配置 Maven protobuf 插件，使 `recommend-service` 和 `api-server` 都能生成并使用相同的 Java stub。

### 3.2 文件清单

| 操作 | 文件 | 说明 |
|---|---|---|
| 新建 | `backend/recommend-service/src/main/proto/recommend.proto` | gRPC 服务定义 |
| 修改 | `backend/recommend-service/pom.xml` | 添加 `protobuf-maven-plugin` + `os-maven-plugin` 生成 stub |
| 修改 | `pom.xml`（父 POM） | `pluginManagement` 中增加 `protobuf-maven-plugin` 版本管理 |
| 修改 | `backend/api-server/pom.xml` | 添加对 `recommend-service` 的 Maven 依赖（无需 proto 插件） |

> api-server 不需要 `protobuf-maven-plugin`。proto 文件仅在 recommend-service 中编译，生成的 Java stub 类随 recommend-service.jar 发布。api-server 通过 Maven 依赖引入即可直接使用 `RecommendServiceGrpc` 等 stub 类。

### 3.3 Proto 定义

文件：`backend/recommend-service/src/main/proto/recommend.proto`

```protobuf
syntax = "proto3";

package recommend;

option java_multiple_files = true;
option java_package = "com.simpledouyin.recommend.proto";
option java_outer_classname = "RecommendProto";

service RecommendService {
    rpc ListRecommendedVideos (ListRecommendedVideosRequest) returns (ListRecommendedVideosResponse);
}

message ListRecommendedVideosRequest {
    string request_id = 1;     // API Server 传入，用于日志串联
    int64 user_id = 2;         // 当前登录用户 ID
    string cursor = 3;         // 分页游标（可选）
    int32 limit = 4;           // 返回数量，最大 30
    bool exclude_viewed = 5;   // P0 固定为 true
    string strategy = 6;       // P0 固定 "like_count_desc"
}

message ListRecommendedVideosResponse {
    repeated int64 video_ids = 1;    // 推荐视频 ID 列表
    string next_cursor = 2;          // 下一页游标
    bool has_more = 3;               // 是否还有更多
    string strategy = 4;             // 固定 "like_count_desc_exclude_viewed"
    string debug_message = 5;        // 仅开发环境可返回
}
```

### 3.4 Maven 配置变更

#### 父 POM (`pom.xml`) — `pluginManagement` 中新增

```xml
<plugin>
    <groupId>org.xolstice.maven.plugins</groupId>
    <artifactId>protobuf-maven-plugin</artifactId>
    <version>0.6.1</version>
    <configuration>
        <protocArtifact>com.google.protobuf:protoc:3.25.3:exe:${os.detected.classifier}</protocArtifact>
        <pluginId>grpc-java</pluginId>
        <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.65.1:exe:${os.detected.classifier}</pluginArtifact>
    </configuration>
    <executions>
        <execution>
            <goals><goal>compile</goal><goal>compile-custom</goal></goals>
        </execution>
    </executions>
</plugin>
```

父 POM 的 `properties` 中需新增：
```xml
<os-maven-plugin.version>1.7.1</os-maven-plugin.version>
```

#### `recommend-service/pom.xml` — `<build>` 中新增

```xml
<extensions>
    <extension>
        <groupId>kr.motd.maven</groupId>
        <artifactId>os-maven-plugin</artifactId>
        <version>${os-maven-plugin.version}</version>
    </extension>
</extensions>
<plugins>
    <plugin>
        <groupId>org.xolstice.maven.plugins</groupId>
        <artifactId>protobuf-maven-plugin</artifactId>
    </plugin>
</plugins>
```

### 3.5 api-server 引用 proto 类

`api-server/pom.xml` 新增依赖（无需 `protobuf-maven-plugin`，stub 类由 recommend-service 编译后提供）：

```xml
<dependency>
    <groupId>com.simpledouyin</groupId>
    <artifactId>recommend-service</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 3.6 验收标准

```bash
mvn -q compile   # 无错误，proto 生成 Java 类位于 target/generated-sources/
```

---

## 4. T16：推荐规则实现

### 4.1 目标

在 `recommend-service` 模块中实现 gRPC server，启动后监听 9090 端口，按规则查询 MySQL 并返回 videoId 列表。

### 4.2 推荐规则（固定，不可偏离）

```
1. videos.status = 'published'
2. videos.visibility = 'public'
3. videos.deleted_at IS NULL
4. 排除当前用户在 video_views 中已访问的视频
5. ORDER BY like_count DESC, created_at DESC, id DESC
```

### 4.3 游标分页设计

游标编码三个字段（Base64 URL-safe，`|` 分隔）：

```
lastLikeCount|lastCreatedAt|lastVideoId
```

- `lastCreatedAt` 格式：ISO 8601 UTC（如 `2026-06-10T08:00:00`）
- 解码：`new String(Base64.getUrlDecoder().decode(cursor), UTF_8).split("\\|")`

SQL 游标条件逻辑：

```sql
WHERE (
    v.like_count < lastLikeCount
    OR (v.like_count = lastLikeCount AND v.created_at < lastCreatedAt)
    OR (v.like_count = lastLikeCount AND v.created_at = lastCreatedAt AND v.id < lastVideoId)
)
```

### 4.4 核心 SQL

使用 `NOT EXISTS` 子查询排除已访问视频，利用 `idx_videos_recommend` 索引：

```sql
SELECT v.id, v.like_count, v.created_at
FROM videos v
WHERE v.status = 'published'
  AND v.visibility = 'public'
  AND v.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM video_views vv
      WHERE vv.user_id = ? AND vv.video_id = v.id
  )
  -- 游标条件（如果有 cursor）
  AND (v.like_count < ? OR (v.like_count = ? AND v.created_at < ?) OR (v.like_count = ? AND v.created_at = ? AND v.id < ?))
ORDER BY v.like_count DESC, v.created_at DESC, v.id DESC
LIMIT ?
```

> `video_recommend` 索引定义：`(status, visibility, deleted_at, like_count DESC, created_at DESC, id DESC)`  
> 已在 `sql/schema.sql` 中声明，MySQL 8 支持降序索引。

### 4.5 文件清单

| 操作 | 文件 | 说明 |
|---|---|---|
| 新建 | `recommend-service/.../recommend/RecommendServiceImpl.java` | gRPC 服务实现（继承 `RecommendServiceGrpc.RecommendServiceImplBase`） |
| 新建 | `recommend-service/.../recommend/RecommendRepository.java` | 推荐 SQL 查询 + 游标编解码 |
| 新建 | `recommend-service/.../recommend/CursorCodec.java` | 游标编码/解码工具类 |
| 修改 | `recommend-service/.../RecommendServiceApplication.java` | 启动 gRPC server，注册服务 |

### 4.6 gRPC Server 启动

`RecommendServiceApplication.java` 注入 `RecommendServiceGrpc` 实现，在 `main()` 或 `@PostConstruct` 中启动 Netty gRPC server：

```java
@SpringBootApplication
public class RecommendServiceApplication implements ApplicationRunner {

    @Value("${recommend.grpc.port:9090}")
    private int grpcPort;

    private final RecommendServiceImpl recommendService;

    public RecommendServiceApplication(RecommendServiceImpl recommendService) {
        this.recommendService = recommendService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Server server = ServerBuilder.forPort(grpcPort)
                .addService(recommendService)
                .build();
        try {
            server.start();
            log.info("gRPC server started on port {}", grpcPort);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start gRPC server", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            try {
                server.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }
}
```

### 4.7 Service 实现骨架

```java
@GrpcService
public class RecommendServiceImpl extends RecommendServiceGrpc.RecommendServiceImplBase {

    private final RecommendRepository repository;

    @Override
    public void listRecommendedVideos(
            ListRecommendedVideosRequest request,
            StreamObserver<ListRecommendedVideosResponse> responseObserver
    ) {
        // 1. 参数校验（limit 1-30，userId > 0）
        // 2. 解码 cursor
        // 3. 查询 repository.findRecommended(userId, cursor, limit + 1)
        // 4. 判断 hasMore
        // 5. 编码 nextCursor
        // 6. 构建 response 并返回
    }
}
```

### 4.8 编码规范

- 与 API Server 保持一致：构造器注入、JdbcTemplate、`@Repository`
- 遵循现有命名风格：`findXxx` 返回列表、`videoId` 小驼峰、SQL 用 `"""..."""` text block
- 日志用 SLF4J（`@Slf4j` 或 `LoggerFactory.getLogger`）
- 不使用 Lombok，保持与 API Server 一致

### 4.9 验收标准

```bash
# 启动服务
mvn -pl backend/recommend-service spring-boot:run
# 确认 gRPC 端口 9090 监听
```

---

## 5. T17：推荐流 REST 端点

### 5.1 目标

在 API Server 中实现 `GET /api/v1/feeds/recommended/videos`，通过 gRPC 调用 Recommend Service，补全视频详情后返回。

### 5.2 文件清单

| 操作 | 文件 | 说明 |
|---|---|---|
| 新建 | `api-server/.../feed/controller/FeedController.java` | REST 端点 |
| 新建 | `api-server/.../feed/service/FeedService.java` | 编排：gRPC 调用 + 详情补全 |
| 新建 | `api-server/.../feed/dto/RecommendedFeedResponse.java` | 响应 DTO |
| 新建 | `api-server/.../recommend/client/RecommendGrpcClient.java` | gRPC 客户端封装 |
| 修改 | `api-server/.../auth/security/BearerAuthenticationFilter.java` | 增加 feed 路径鉴权 |

### 5.3 gRPC 客户端

复用成员 A 的 `ManagedChannel` Bean（`GrpcConfig`），创建 blocking stub：

```java
@Component
public class RecommendGrpcClient {

    private final RecommendServiceGrpc.RecommendServiceBlockingStub stub;

    public RecommendGrpcClient(ManagedChannel channel) {
        this.stub = RecommendServiceGrpc.newBlockingStub(channel);
    }

    public ListRecommendedVideosResponse listRecommended(
            String requestId, long userId, String cursor, int limit
    ) {
        ListRecommendedVideosRequest request = ListRecommendedVideosRequest.newBuilder()
                .setRequestId(requestId)
                .setUserId(userId)
                .setCursor(cursor != null ? cursor : "")
                .setLimit(limit)
                .setExcludeViewed(true)
                .setStrategy("like_count_desc")
                .build();
        return stub.listRecommendedVideos(request);
    }
}
```

### 5.4 FeedService 详情补全

使用 `VideoPostAssembler`（已有）将 `VideoPost` 转为 `VideoPostResponse`，保持与现有 myVideos/publish 接口一致的输出格式。

```
1. 接收 userId, cursor, limit
2. 调 RecommendGrpcClient.listRecommended(...) 获取 videoIds
3. 按序批量查 VideoPost：复用 VideoRepository.findPostById(videoId, userId)
4. 通过 VideoPostAssembler.toResponse(post, userId) 转为 VideoPostResponse
5. 按 videoIds 原始顺序保持 items 列表
6. 构建 RecommendedFeedResponse（List<VideoPostResponse> items, nextCursor, hasMore, strategy）
```

> `VideoRepository.findPostById(id, viewerId)` 已包含 `like_count`（实时 COUNT）、`view_count`（实时 COUNT）、`liked`（EXISTS）、`viewed`（EXISTS）、`owner`（author_id == viewerId），无需额外编写详情 SQL。

### 5.5 FeedController

```java
@RestController
@RequestMapping("/api/v1")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/feeds/recommended/videos")
    public ResponseEntity<ApiResponse<RecommendedFeedResponse>> recommendedVideos(
            HttpServletRequest request,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                feedService.listRecommended(request, cursor, limit)));
    }
}
```

### 5.6 鉴权配置

在 `BearerAuthenticationFilter.requiresAuthentication()` 中增加：

```java
|| ("GET".equalsIgnoreCase(method) && "/api/v1/feeds/recommended/videos".equals(path))
```

### 5.7 响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": 2001,
        "author": { "id": 1001, "username": "alice", "nickname": "Alice", "avatarUrl": null },
        "caption": "...",
        "videoUrl": "/uploads/videos/2001.mp4",
        "likeCount": 100,
        "viewCount": 500,
        "commentCount": 3,
        "viewerState": { "liked": false, "viewed": false, "owner": false },
        "createdAt": "2026-06-05T08:00:00Z"
      }
    ],
    "nextCursor": "eyJsYXN0TGlrZUNvdW50Ijo...",
    "hasMore": true,
    "strategy": "like_count_desc_exclude_viewed"
  },
  "requestId": "req_2026061001..."
}
```

### 5.8 验收标准

```bash
# 1. 启动 Recommend Service
mvn -pl backend/recommend-service spring-boot:run

# 2. 启动 API Server
mvn -pl backend/api-server spring-boot:run

# 3. 调推荐接口
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/v1/feeds/recommended/videos?limit=5"
```

---

## 6. T20：推荐规则测试

### 6.1 测试策略

| 层级 | 策略 | 测试目标 |
|---|---|---|
| RecommendRepository（T16） | 真实 MySQL（`@SpringBootTest`） | 排序、过滤、分页、游标正确性 |
| FeedController（T17） | Mock FeedService | 鉴权、统一响应、日志、gRPC 异常映射 |

> T20 聚焦 R01-R08 和 E10 测试用例，不额外编写 gRPC Service 单元测试（RecommendServiceImpl 逻辑简单，由 Repository 集成测试和 Controller 测试双重覆盖）。

### 6.2 对应测试用例（来自 `docs/test-plan.md`）

| 编号 | 测试类 | 用例 |
|---|---|---|
| R01 | `RecommendRepositoryTest` | 三条视频 like_count 100/50/10 → 返回顺序 100/50/10 |
| R02 | `RecommendRepositoryTest` | 同 like_count 不同 created_at → 新视频在前 |
| R03 | `RecommendRepositoryTest` | 当前用户已访问高赞视频 → 不再返回 |
| R04 | `RecommendRepositoryTest` | 用户访问全部视频 → 空 items，hasMore=false |
| R05 | `RecommendRepositoryTest` | limit=2 分两页 → 无重复、排序连续 |
| R06 | `RecommendRepositoryTest` | 软删除高赞视频 → 不返回 |
| R07 | `RecommendRepositoryTest` | visibility=private → 不返回 |
| R08 | `FeedControllerTest` | Mock gRPC client → 验证 Controller 调用了 gRPC |
| E10 | `FeedControllerTest` | gRPC 异常 → 500，业务码 50001 |

### 6.3 RecommendRepositoryTest 结构

```java
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecommendRepositoryTest {

    @Autowired RecommendRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach void setUp() { /* 插入测试 users/videos/video_likes/video_views */ }
    @AfterEach void tearDown() { /* 清理 */ }

    @Test void sortsByLikeCountDesc() { ... }     // R01
    @Test void sortsByCreatedAtWhenLikesEqual() { ... } // R02
    @Test void excludesViewedVideos() { ... }      // R03
    @Test void returnsEmptyWhenAllViewed() { ... } // R04
    @Test void paginatesWithoutDuplicates() { ... } // R05
    @Test void excludesDeletedVideos() { ... }     // R06
    @Test void excludesPrivateVideos() { ... }     // R07
}
```

### 6.4 FeedControllerTest 结构

```java
class FeedControllerTest {
    // MockMvc + mock(FeedService.class)
    // 类比现有 LikeControllerTest / ViewControllerTest 模式

    @Test void returnsRecommendedVideos() { ... }
    @Test void returns401WithoutToken() { ... }
    @Test void returns400WithInvalidLimit() { ... }
    @Test void logsRequestInfo() { ... }
    @Test void returns500WhenGrpcFails() { ... }  // E10
}
```

### 6.5 验收标准

```bash
$env:MYSQL_PASSWORD="1280760251a"
mvn -pl backend/recommend-service -q test   # RecommendRepositoryTest 通过
mvn -pl backend/api-server -q test          # FeedControllerTest 通过
```

---

## 7. 分阶段执行计划

### 阶段一：T15 — gRPC 推荐契约

> 目标：`mvn -q compile` 通过，`RecommendServiceGrpc` 类可被引用

| 步骤 | 操作 | 文件 | 说明 |
|---|---|---|---|
| 1.1 | 新建 | `backend/recommend-service/src/main/proto/recommend.proto` | 写入 proto 定义（3.3 节） |
| 1.2 | 修改 | `pom.xml` | `properties` 加 `<os-maven-plugin.version>1.7.1</os-maven-plugin.version>`；`pluginManagement` 加 `protobuf-maven-plugin`（3.4 节） |
| 1.3 | 修改 | `backend/recommend-service/pom.xml` | `<build>` 中加 `os-maven-plugin` extension + `protobuf-maven-plugin` |
| 1.4 | 修改 | `backend/api-server/pom.xml` | `<dependencies>` 中加对 `recommend-service` 的依赖（3.5 节） |
| 1.5 | 验证 | — | `mvn -q compile`，确认 `target/generated-sources/` 下有 `RecommendServiceGrpc.java` |
| 1.6 | 验证 | — | `git diff --check` + `git diff --name-only -- frontend` |

---

### 阶段二：T16 — 推荐规则实现

> 目标：Recommend Service 启动后监听 9090 端口，gRPC 调用返回 videoIds

| 步骤 | 操作 | 文件 | 说明 |
|---|---|---|---|
| 2.1 | 新建 | `backend/recommend-service/src/main/java/com/simpledouyin/recommend/CursorCodec.java` | 游标编解码工具：`encode(likeCount, createdAt, videoId)` → Base64 字符串；`decode(cursor)` → `Cursor` record（3 字段） |
| 2.2 | 新建 | `backend/recommend-service/src/main/java/com/simpledouyin/recommend/RecommendRepository.java` | 核心 SQL 查询（4.4 节）：参数绑定 `NOT EXISTS` + cursor 条件（6 个占位符：`lastLikeCount` 3 次、`lastCreatedAt` 2 次、`lastVideoId` 1 次）；注入 `JdbcTemplate` + `CursorCodec` |
| 2.3 | 新建 | `backend/recommend-service/src/main/java/com/simpledouyin/recommend/RecommendServiceImpl.java` | 继承 `RecommendServiceGrpc.RecommendServiceImplBase`，实现 `listRecommendedVideos`：校验 `userId > 0` / `limit 1-30`，解码 cursor，调 repository，编码 nextCursor，构建 response |
| 2.4 | 修改 | `backend/recommend-service/src/main/java/com/simpledouyin/recommend/RecommendServiceApplication.java` | 实现 `ApplicationRunner`：`ServerBuilder.forPort(grpcPort).addService(recommendService).build().start()` + shutdown hook |
| 2.5 | 验证 | — | `mvn -q compile` |
| 2.6 | 验证 | — | `mvn -pl backend/recommend-service spring-boot:run`，确认日志输出 gRPC 端口 9090 |
| 2.7 | 提交 | — | `git add` + `git commit`，分支建议 `feature/T16-grpc-recommend` |

---

### 阶段三：T17 — 推荐流 REST 端点

> 目标：`GET /api/v1/feeds/recommended/videos` 返回完整 VideoPost 列表

| 步骤 | 操作 | 文件 | 说明 |
|---|---|---|---|
| 3.1 | 新建 | `backend/api-server/src/main/java/com/simpledouyin/api/recommend/client/RecommendGrpcClient.java` | `@Component`，注入 `ManagedChannel`（复用成员 A 的 `GrpcConfig` Bean），构建 `RecommendServiceGrpc.RecommendServiceBlockingStub`，封装 `listRecommended(requestId, userId, cursor, limit)` |
| 3.2 | 新建 | `backend/api-server/src/main/java/com/simpledouyin/api/feed/dto/RecommendedFeedResponse.java` | `record RecommendedFeedResponse(List<VideoPostResponse> items, String nextCursor, boolean hasMore, String strategy)` |
| 3.3 | 新建 | `backend/api-server/src/main/java/com/simpledouyin/api/feed/service/FeedService.java` | `@Service`，注入 `RecommendGrpcClient` + `VideoRepository` + `VideoPostAssembler`；`listRecommended(request, cursor, limit)`：校验 limit 1-30 → 调 gRPC client → 逐 videoId 调 `findPostById(videoId, userId)` → `VideoPostAssembler.toResponse(post, userId)` → 按原序组装 → 构建 `RecommendedFeedResponse` |
| 3.4 | 新建 | `backend/api-server/src/main/java/com/simpledouyin/api/feed/controller/FeedController.java` | `@RestController` + `@RequestMapping("/api/v1")`，`GET /feeds/recommended/videos`，注入 `FeedService`，返回 `ResponseEntity<ApiResponse<RecommendedFeedResponse>>` |
| 3.5 | 修改 | `backend/api-server/src/main/java/com/simpledouyin/api/auth/security/BearerAuthenticationFilter.java` | `requiresAuthentication()` 中加：`\|\| ("GET".equalsIgnoreCase(method) && "/api/v1/feeds/recommended/videos".equals(path))` |
| 3.6 | 验证 | — | `mvn -q compile` |
| 3.7 | 验证 | — | 分别启动 recommend-service 和 api-server；curl 测试（5.8 节） |
| 3.8 | 提交 | — | `git add` + `git commit`，分支建议 `feature/T17-feed-rest` |

---

### 阶段四：T20 — 推荐规则测试

> 目标：`mvn -q test` 全部通过（含 R01-R08、E10）

| 步骤 | 操作 | 文件 | 说明 |
|---|---|---|---|
| 4.1 | 新建 | `backend/recommend-service/src/test/resources/application-test.yml` | 同 api-server 的 test 配置：`spring.datasource.password: ${MYSQL_PASSWORD:password}` |
| 4.2 | 新建 | `backend/recommend-service/src/test/java/com/simpledouyin/recommend/RecommendRepositoryTest.java` | `@SpringBootTest` + `@ActiveProfiles("test")`；`@BeforeEach` 插入测试 users/videos/video_likes/video_views；`@AfterEach` 清理 |
| 4.3 | 编写 | 同上 — R01 | `sortByLikeCountDesc`：插入 3 条视频（like_count 100/50/10），调 `findRecommended`，断言顺序 |
| 4.4 | 编写 | 同上 — R02 | `sortByCreatedAtWhenLikesEqual`：2 条同 like_count 不同 created_at，断言新视频在前 |
| 4.5 | 编写 | 同上 — R03 | `excludesViewedVideos`：插入 video_views 覆盖高赞视频，断言该视频不返回 |
| 4.6 | 编写 | 同上 — R04 | `returnsEmptyWhenAllViewed`：全部视频写入 video_views，断言空结果 |
| 4.7 | 编写 | 同上 — R05 | `paginatesWithoutDuplicates`：插入 5 条视频，limit=2，分 3 页，断言无重复、hasMore 正确 |
| 4.8 | 编写 | 同上 — R06 | `excludesDeletedVideos`：软删除一条高赞视频，断言不返回 |
| 4.9 | 编写 | 同上 — R07 | `excludesPrivateVideos`：设置 visibility=private，断言不返回 |
| 4.10 | 新建 | `backend/api-server/src/test/java/com/simpledouyin/api/feed/controller/FeedControllerTest.java` | MockMvc + `mock(FeedService.class)`，类比 `LikeControllerTest` 模式 |
| 4.11 | 编写 | 同上 — 核心用例 | 正常返回推荐列表、`hasMore`/`nextCursor` 传递、401 无 token、400 invalid limit、日志完整性 |
| 4.12 | 编写 | 同上 — R08 | `feedCallsGrpcService`：Mock FeedService 内部调用 gRPC client，验证 gRPC 调用发生 |
| 4.13 | 编写 | 同上 — E10 | `returns500WhenGrpcFails`：Mock FeedService 抛 `BusinessException(ErrorCode.INTERNAL_ERROR)`，断言 500 + 50001 |
| 4.14 | 验证 | — | `$env:MYSQL_PASSWORD="1280760251a"; mvn -q test` |
| 4.15 | 验证 | — | `git diff --check` + `git diff --name-only -- frontend` |
| 4.16 | 提交 | — | `git add` + `git commit`，可合并到 T16/T17 分支或独立 `feature/T20-recommend-tests` |

---

## 8. 阶段间验证清单

每个阶段结束时执行：

```bash
mvn -q compile          # 编译通过
mvn -q test             # 已有测试不回归
git diff --check        # 无空白错误
git diff --name-only -- frontend  # 未修改前端
```

---

## 9. 文件总览

### 新建文件（8 个）

| # | 文件 |
|---|---|
| 1 | `backend/recommend-service/src/main/proto/recommend.proto` |
| 2 | `backend/recommend-service/src/main/java/.../recommend/RecommendServiceImpl.java` |
| 3 | `backend/recommend-service/src/main/java/.../recommend/RecommendRepository.java` |
| 4 | `backend/recommend-service/src/main/java/.../recommend/CursorCodec.java` |
| 5 | `backend/api-server/src/main/java/.../feed/controller/FeedController.java` |
| 6 | `backend/api-server/src/main/java/.../feed/service/FeedService.java` |
| 7 | `backend/api-server/src/main/java/.../feed/dto/RecommendedFeedResponse.java` |
| 8 | `backend/api-server/src/main/java/.../recommend/client/RecommendGrpcClient.java` |

### 测试文件（3 个）

| # | 文件 |
|---|---|
| 1 | `backend/recommend-service/src/test/java/.../recommend/RecommendRepositoryTest.java` |
| 2 | `backend/api-server/src/test/java/.../feed/controller/FeedControllerTest.java` |
| 3 | `backend/recommend-service/src/test/resources/application-test.yml` |

### 修改文件（5 个）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `pom.xml` | `pluginManagement` 增加 protobuf-maven-plugin；`properties` 增加 `os-maven-plugin.version` |
| 2 | `backend/recommend-service/pom.xml` | 增加 os-maven-plugin extension + protobuf-maven-plugin |
| 3 | `backend/api-server/pom.xml` | 增加 recommend-service 依赖（无需 proto 插件，stub 类由依赖传递） |
| 4 | `backend/recommend-service/.../RecommendServiceApplication.java` | 启动 gRPC server |
| 5 | `backend/api-server/.../auth/security/BearerAuthenticationFilter.java` | 增加 `/feeds/recommended/videos` 鉴权 |

---

## 10. 风险与注意事项

| 风险 | 应对 |
|---|---|
| `protobuf-maven-plugin` 版本兼容 | 父 POM 已锁定 protobuf 3.25.3 + gRPC 1.65.1，使用 `os-maven-plugin` 1.7.1 自动检测 OS |
| 游标编码 Base64 含特殊字符 | 使用 `getUrlEncoder().withoutPadding()`，确保 URL-safe |
| MySQL 降序索引在 8.0 之前不支持 | 项目已锁定 MySQL 8 |
| api-server 通过依赖引入 recommend-service 可能耦合过重 | 课程要求简单，不做公共模块；后续如需拆分，将 proto 提为独立 api 模块即可 |
| gRPC 服务启动后阻塞 Spring Boot 端口 | 按 ApplicationRunner 方式，不阻塞 Spring Boot 的 Tomcat（实际 gRPC 是独立端口 9090） |
| 推荐流接口的 `videoCount` 从实时 COUNT 获取 | 与现有 `FIND_POST_BY_ID_SQL` 一致，确保语义统一 |
| 禁止越界实现 Bonus | 不实现 `feed/following`、`search`、`metrics`、复杂推荐算法等 |

---

## 11. 检查命令清单

```bash
# 编译（含 proto 生成）
mvn -q compile

# 全部测试
$env:MYSQL_PASSWORD="1280760251a"
mvn -q test

# 启动 Recommend Service
mvn -pl backend/recommend-service spring-boot:run

# 启动 API Server
mvn -pl backend/api-server spring-boot:run

# 验证接口
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/v1/feeds/recommended/videos?limit=3"

# 验证 health 中的 recommendService 状态
curl http://localhost:8080/api/v1/health

# 合规检查
git diff --check
git diff --name-only -- frontend
```
