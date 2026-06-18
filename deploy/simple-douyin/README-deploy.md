# simple-douyin Offline Backend Deployment

This deployment is designed for a server that cannot reliably access Docker Hub. Build and export all images locally, upload the tar files, then import them on the server.

For the full handoff checklist and troubleshooting guide, see `DEPLOYMENT-HANDOFF.md`.

## Local Build

Run from the repository root:

```bash
chmod +x deploy/simple-douyin/build-images-local.sh
./deploy/simple-douyin/build-images-local.sh
```

The script will:

1. Build the Maven modules.
2. Build `linux/amd64` application images:
   - `simple-douyin-api-server:prod`
   - `simple-douyin-recommend-service:prod`
3. Pull `linux/amd64` `mysql:8.0`.
4. Export image tar files under `deploy/simple-douyin/images/`.

The script skips tests during packaging with `-DskipTests`. The reason is that
the current local/sandbox environment can reject the recommend-service test
startup when it tries to bind the gRPC port, producing `Operation not
permitted`. If your environment allows local port binding and has the required
test dependencies, run a full test pass separately before deployment:

```bash
mvn -q test
```

After the containers start, always verify the deployed integration through the
health endpoint. `GET /api/v1/health` must report API Server, MySQL, and the
gRPC recommend service as `UP`.

Do not commit generated image tar files.

## Server Directory

After deployment is confirmed, prepare this isolated server directory:

```bash
mkdir -p /opt/simple-douyin-backend/images
mkdir -p /opt/simple-douyin-backend/sql
mkdir -p /opt/simple-douyin-backend/uploads
```

Upload these files into `/opt/simple-douyin-backend`:

```text
docker-compose.yml
sql/schema.sql
images/simple-douyin-api-server-prod.tar
images/simple-douyin-recommend-service-prod.tar
images/mysql-8.0-amd64.tar
```

Use `.env.example` as a template for `/opt/simple-douyin-backend/.env`, then replace all `change_me` values with real secrets:

```bash
chmod 600 /opt/simple-douyin-backend/.env
```

## Upload Example

```bash
scp -i ~/.ssh/ShiXun01.pem deploy/simple-douyin/images/*.tar root@47.95.238.140:/opt/simple-douyin-backend/images/
scp -i ~/.ssh/ShiXun01.pem deploy/simple-douyin/docker-compose.yml root@47.95.238.140:/opt/simple-douyin-backend/docker-compose.yml
scp -i ~/.ssh/ShiXun01.pem sql/schema.sql root@47.95.238.140:/opt/simple-douyin-backend/sql/schema.sql
```

Generate `/opt/simple-douyin-backend/.env` on the server with real secrets before starting containers. Do not upload `.env.example` as the final runtime `.env`.

## Server Image Import

```bash
docker load -i /opt/simple-douyin-backend/images/mysql-8.0-amd64.tar
docker load -i /opt/simple-douyin-backend/images/simple-douyin-recommend-service-prod.tar
docker load -i /opt/simple-douyin-backend/images/simple-douyin-api-server-prod.tar
```

## Start

```bash
docker compose -f /opt/simple-douyin-backend/docker-compose.yml up -d
```

The MySQL container initializes `simple_douyin` from `./sql/schema.sql` only when the `simple-douyin-mysql-data` volume is empty on first startup.

## Verify

```bash
docker ps --filter name=simple-douyin
docker logs --tail=120 simple-douyin-api-server
docker logs --tail=120 simple-douyin-recommend-service
ss -lntp | grep 18090
curl -i http://127.0.0.1:18090/api/v1/health
```

Expected result: API Server reports `UP` for API, MySQL, and the gRPC recommend service.

## Rollback

Stop only the simple-douyin deployment:

```bash
docker compose -f /opt/simple-douyin-backend/docker-compose.yml down
```

This keeps `simple-douyin-mysql-data` and `/opt/simple-douyin-backend/uploads` by default. Do not remove volumes unless you intentionally want to delete simple-douyin data.

## Isolation

This deployment does not affect old projects because:

- It uses only `/opt/simple-douyin-backend`.
- All container names use the `simple-douyin-` prefix.
- The Docker network is `simple-douyin-net`.
- The MySQL volume is `simple-douyin-mysql-data`.
- MySQL port `3306` is not mapped to the host.
- Recommend service ports `8081` and `9090` are not mapped to the host.
- Only API port `18090` is mapped, and it was checked as unused.
- It does not modify host Nginx.
- It does not stop, remove, or overwrite old containers, images, volumes, databases, directories, or Compose files.
