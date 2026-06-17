# simple-douyin Backend Deployment Handoff

This document is for the next agent or teammate who needs to reproduce the backend deployment on a new server. It records the working offline deployment flow used for simple-douyin.

## 1. Deployment Goal

Deploy only the simple-douyin backend:

- `simple-douyin-api-server`
- `simple-douyin-recommend-service`
- `simple-douyin-mysql`

Do not deploy the frontend. Do not modify host Nginx.

The API is exposed through host port `18090`. MySQL and the gRPC recommend service use Docker internal networking by default:

- MySQL listens on container port `3306` only.
- Recommend gRPC listens on container port `9090` only.
- Recommend admin port `8081` is not mapped to the host by default.

## 2. Service Topology

```text
client
  |
host:18090
  |
simple-douyin-api-server:8080
  | gRPC
simple-douyin-recommend-service:9090
  | JDBC
simple-douyin-mysql:3306
```

Deployment resources:

```text
Containers:
  simple-douyin-api-server
  simple-douyin-recommend-service
  simple-douyin-mysql

Docker network:
  simple-douyin-net

Docker volume:
  simple-douyin-mysql-data

Server uploads directory:
  /opt/simple-douyin-backend/uploads
```

## 3. New Server Prerequisites

The target server needs:

- Linux `x86_64` / `amd64`.
- Docker available.
- Docker Compose available.
- SSH access.
- Target API port available; default is `18090`.
- Enough disk space for image tar files, Docker images, MySQL volume, and uploads.

The server does not need stable Docker Hub access because images can be imported offline with `docker load`.

If the server architecture is not `amd64`, update the local build command and image tags accordingly. The current scripts use:

```bash
docker buildx build --platform linux/amd64 --load
docker pull --platform linux/amd64 mysql:8.0
```

## 4. Files To Keep Locally And Share

### A. Files That Must Be Committed To Git

These files define the reproducible deployment workflow:

```text
Dockerfile.api-server
Dockerfile.recommend-service
deploy/simple-douyin/docker-compose.yml
deploy/simple-douyin/.env.example
deploy/simple-douyin/build-images-local.sh
deploy/simple-douyin/README-deploy.md
deploy/simple-douyin/DEPLOYMENT-HANDOFF.md
backend/recommend-service/pom.xml
backend/recommend-service/src/main/java/com/simpledouyin/recommend/RecommendServiceApplication.java
sql/schema.sql
```

`backend/recommend-service/pom.xml` is important because it keeps the normal Maven artifact and also creates the executable `-exec.jar`.

`RecommendServiceApplication.java` is important because the gRPC server must block after startup with `server.awaitTermination()`.

### B. Files To Transfer During Deployment

These files are transferred to the server during deployment. The generated image tar files are deployment artifacts and do not need to be committed to Git.

```text
deploy/simple-douyin/docker-compose.yml
sql/schema.sql
deploy/simple-douyin/images/mysql-8.0-amd64.tar
deploy/simple-douyin/images/simple-douyin-api-server-prod.tar
deploy/simple-douyin/images/simple-douyin-recommend-service-prod.tar
```

The server also needs:

```text
/opt/simple-douyin-backend/.env
```

Do not copy real `.env` values from Git. Generate real passwords and secrets on the server.

## 5. Files That Must Not Be Committed

Do not commit:

```text
deploy/simple-douyin/images/*.tar
deploy/simple-douyin/.env
/opt/simple-douyin-backend/.env
any real password, token, private key, or secret
```

The repository `.gitignore` should include:

```gitignore
deploy/simple-douyin/images/
deploy/simple-douyin/.env
*.tar
```

Do not ignore these source-controlled deployment files:

```text
deploy/simple-douyin/.env.example
deploy/simple-douyin/docker-compose.yml
deploy/simple-douyin/build-images-local.sh
deploy/simple-douyin/README-deploy.md
deploy/simple-douyin/DEPLOYMENT-HANDOFF.md
```

## 6. Local Build Flow

Run from the repository root on the local machine:

```bash
chmod +x deploy/simple-douyin/build-images-local.sh
./deploy/simple-douyin/build-images-local.sh
```

The script generates:

```text
deploy/simple-douyin/images/mysql-8.0-amd64.tar
deploy/simple-douyin/images/simple-douyin-api-server-prod.tar
deploy/simple-douyin/images/simple-douyin-recommend-service-prod.tar
```

Internally, the script:

- Runs Maven package with `-DskipTests`.
- Builds `linux/amd64` application images with Docker buildx.
- Pulls `linux/amd64` `mysql:8.0`.
- Saves images to tar files with `docker save`.

The script skips tests because the current local or sandbox environment may reject the recommend-service test when it tries to bind gRPC `0.0.0.0:9090`, causing `Operation not permitted`. If the environment allows it, run full tests separately before deployment:

```bash
mvn -q test
```

## 7. New Server Deployment Flow

Use variables so this can be reused on a different server:

```bash
SSH_KEY=~/.ssh/your_key.pem
SSH_TARGET=root@your.server.ip
REMOTE_DIR=/opt/simple-douyin-backend
API_PORT=18090
```

### 7.1 Read-only Preflight

Check old containers, occupied ports, and existing `/opt` directories before modifying anything:

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" \
  "docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}' && ss -lntp && ls -lah /opt"
```

Stop if `$API_PORT` is already occupied or if there are conflicting `simple-douyin-*` containers, networks, or volumes that you do not own.

Optional conflict checks:

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" \
  "docker ps -a --format '{{.Names}}' | grep -E '^simple-douyin' || true; \
   docker network ls --format '{{.Name}}' | grep -E '^simple-douyin' || true; \
   docker volume ls --format '{{.Name}}' | grep -E '^simple-douyin' || true; \
   ss -lntp | grep ':18090' || true"
```

### 7.2 Create Isolated Directories

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" \
  "mkdir -p $REMOTE_DIR/images $REMOTE_DIR/sql $REMOTE_DIR/uploads"
```

Do not modify other directories under `/opt`.

### 7.3 Upload Files

```bash
scp -i "$SSH_KEY" deploy/simple-douyin/images/*.tar "$SSH_TARGET:$REMOTE_DIR/images/"
scp -i "$SSH_KEY" deploy/simple-douyin/docker-compose.yml "$SSH_TARGET:$REMOTE_DIR/docker-compose.yml"
scp -i "$SSH_KEY" sql/schema.sql "$SSH_TARGET:$REMOTE_DIR/sql/schema.sql"
```

Do not upload `.env.example` as the final `.env`. Generate real `.env` values on the server.

### 7.4 Generate Server `.env`

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" 'bash -s' <<'EOF'
set -euo pipefail
cd /opt/simple-douyin-backend

MYSQL_PASSWORD="$(openssl rand -hex 24)"
MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
AUTH_TOKEN_SECRET="$(openssl rand -hex 32)"

cat > .env <<ENVEOF
MYSQL_DATABASE=simple_douyin
MYSQL_USER=simple_douyin_user
MYSQL_PASSWORD=${MYSQL_PASSWORD}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}

API_SERVER_PORT=8080
RECOMMEND_ADMIN_PORT=8081
RECOMMEND_GRPC_PORT=9090
RECOMMEND_GRPC_HOST=simple-douyin-recommend-service

MYSQL_URL=jdbc:mysql://simple-douyin-mysql:3306/simple_douyin?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=simple_douyin_user
SPRING_DATASOURCE_URL=jdbc:mysql://simple-douyin-mysql:3306/simple_douyin?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=simple_douyin_user
SPRING_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}

UPLOADS_DIR=/app/uploads
AUTH_TOKEN_SECRET=${AUTH_TOKEN_SECRET}
ENVEOF

chmod 600 .env
ls -l .env
EOF
```

Do not print `.env` contents in logs or final reports.

### 7.5 Load Images

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" 'bash -s' <<'EOF'
set -euo pipefail

docker load -i /opt/simple-douyin-backend/images/mysql-8.0-amd64.tar
docker load -i /opt/simple-douyin-backend/images/simple-douyin-recommend-service-prod.tar
docker load -i /opt/simple-douyin-backend/images/simple-douyin-api-server-prod.tar

docker images | grep -E "simple-douyin|mysql"
EOF
```

If `docker load` fails, stop and fix that first. Do not start containers with missing images.

### 7.6 Validate Compose And Start

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" 'bash -s' <<'EOF'
set -euo pipefail
cd /opt/simple-douyin-backend

docker compose -f docker-compose.yml config --quiet
docker compose -f docker-compose.yml up -d
EOF
```

Use `config --quiet` to avoid printing secrets from `.env`.

### 7.7 Health Check

```bash
ssh -i "$SSH_KEY" "$SSH_TARGET" \
  "curl -i http://127.0.0.1:18090/api/v1/health"
```

Expected result:

```text
HTTP/1.1 200
apiServer=UP
mysql=UP
recommendService=UP
```

## 8. `.env` Template Notes

The server `.env` must include:

```dotenv
MYSQL_DATABASE=simple_douyin
MYSQL_USER=simple_douyin_user
MYSQL_PASSWORD=<strong-password>
MYSQL_ROOT_PASSWORD=<strong-password>
API_SERVER_PORT=8080
RECOMMEND_ADMIN_PORT=8081
RECOMMEND_GRPC_PORT=9090
RECOMMEND_GRPC_HOST=simple-douyin-recommend-service
MYSQL_URL=jdbc:mysql://simple-douyin-mysql:3306/simple_douyin?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
MYSQL_USERNAME=simple_douyin_user
SPRING_DATASOURCE_URL=jdbc:mysql://simple-douyin-mysql:3306/simple_douyin?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=simple_douyin_user
SPRING_DATASOURCE_PASSWORD=<same-as-MYSQL_PASSWORD>
UPLOADS_DIR=/app/uploads
AUTH_TOKEN_SECRET=<strong-random-secret>
```

Rules:

- `MYSQL_PASSWORD` and `SPRING_DATASOURCE_PASSWORD` must be identical.
- Use `chmod 600 .env`.
- Never commit real `.env` files.
- Never print secrets in deployment reports.

## 9. Verification Commands

Run on the server:

```bash
docker ps --filter name=simple-douyin
docker logs --tail=120 simple-douyin-api-server
docker logs --tail=120 simple-douyin-recommend-service
docker logs --tail=80 simple-douyin-mysql
ss -lntp | grep 18090
curl -i http://127.0.0.1:18090/api/v1/health
```

Success standard:

```text
HTTP/1.1 200
apiServer=UP
mysql=UP
recommendService=UP
```

Also confirm old containers are still running:

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
```

## 10. Common Troubleshooting

### Problem 1: `recommend-service` Keeps Restarting

Likely cause:

- The gRPC server starts, then the main process exits.
- The service needs `server.awaitTermination()` after `server.start()`.

Fix:

- Check `backend/recommend-service/src/main/java/com/simpledouyin/recommend/RecommendServiceApplication.java`.
- Rebuild only `simple-douyin-recommend-service:prod`.
- Upload and replace only `simple-douyin-recommend-service-prod.tar`.
- Recreate only the recommend service:

```bash
cd /opt/simple-douyin-backend
docker load -i images/simple-douyin-recommend-service-prod.tar
docker compose -f docker-compose.yml up -d --no-deps --force-recreate simple-douyin-recommend-service
```

### Problem 2: `api-server` Cannot Compile `com.simpledouyin.recommend.proto`

Likely cause:

- The main recommend-service Maven artifact was converted into a Spring Boot fat jar.
- Classes inside a Boot fat jar are under `BOOT-INF/classes`, so it is not suitable as a normal Maven compile dependency.

Fix:

- Keep `recommend-service-0.1.0-SNAPSHOT.jar` as a normal jar.
- Configure `spring-boot-maven-plugin` with `classifier=exec`.
- Use `recommend-service-0.1.0-SNAPSHOT-exec.jar` only for Docker runtime.

### Problem 3: Server Cannot Pull Docker Images

Fix:

- Build and pull images locally.
- Use `docker save` locally.
- Upload tar files.
- Use `docker load` on the server.

### Problem 4: Health Check Shows `recommendService=DOWN`

Check:

- Is `simple-douyin-recommend-service` running?
- Is gRPC port `9090` available inside the Docker network?
- Is `RECOMMEND_GRPC_HOST=simple-douyin-recommend-service`?
- Does API Server log show DNS or gRPC connection failures?
- Does recommend-service log show `gRPC server started on port 9090`?

If the service was just recreated, wait a few seconds and retry:

```bash
curl -i http://127.0.0.1:18090/api/v1/health
```

### Problem 5: MySQL Initialization Did Not Run

Explanation:

- `/docker-entrypoint-initdb.d/01-schema.sql` runs only when the MySQL data directory is empty.
- With the named volume `simple-douyin-mysql-data`, initialization runs only on first creation.
- Do not delete a production volume casually.

If a clean reinitialization is truly required, first confirm data loss is acceptable, then explicitly remove the simple-douyin volume only. Never remove other project volumes.

## 11. Rollback

Rollback only the simple-douyin deployment:

```bash
cd /opt/simple-douyin-backend
docker compose -f docker-compose.yml down
```

By default, do not delete:

```text
simple-douyin-mysql-data
/opt/simple-douyin-backend/uploads
```

Delete them only when you explicitly intend to clear simple-douyin data.

## 12. Principles For Not Affecting Existing Projects

This deployment is isolated by design:

- Uses independent directory `/opt/simple-douyin-backend`.
- Uses independent container names with the `simple-douyin-` prefix.
- Uses independent Docker network `simple-douyin-net`.
- Uses independent volume `simple-douyin-mysql-data`.
- Does not map MySQL `3306` to the host.
- Does not map recommend gRPC `9090` to the host.
- Exposes only API port `18090`.
- Does not modify host Nginx.
- Does not stop old containers.
- Does not delete old containers, images, volumes, databases, directories, or Compose files.
- Does not run `docker system prune`.

Before each deployment, record old containers and occupied ports. After deployment, verify the old containers are still running.
