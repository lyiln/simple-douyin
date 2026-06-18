# Local Docker Startup Guide

This guide describes the local one-command startup flow for the Simple Douyin course project.

## Services

The local Docker stack starts these services:

| Service | Container | Host port | Purpose |
| --- | --- | --- | --- |
| Web | `simple-douyin-web` | `5173` | Static web UI and reverse proxy |
| API Server | `simple-douyin-api-server` | `8080` | Spring Boot REST API |
| Recommend Service | `simple-douyin-recommend-service` | `8081`, `9090` | gRPC recommendation service |
| MySQL | `simple-douyin-mysql` | `3306` | MySQL 8 database |

The web service proxies:

- `/api/**` to `api-server:8080`
- `/uploads/**` to `api-server:8080`

## One-command startup

Run from the repository root:

```bash
docker compose up --build -d
```

Then open:

```text
http://localhost:5173
```

## Health checks

Check the backend directly:

```bash
curl.exe http://localhost:8080/api/v1/health
```

Check the web reverse proxy:

```bash
curl.exe http://localhost:5173/api/v1/health
```

Expected result: the response data reports `apiServer`, `mysql`, and `recommendService` as `UP`.

## Common operations

Show containers:

```bash
docker compose ps
```

Show logs:

```bash
docker compose logs api-server recommend-service web mysql
```

Follow logs:

```bash
docker compose logs -f api-server recommend-service web mysql
```

Stop services but keep database/upload volumes:

```bash
docker compose down
```

Stop services and clear local MySQL/upload volumes:

```bash
docker compose down -v
```

Rebuild and restart:

```bash
docker compose up --build -d
```

## Local defaults

The Compose file uses local-only defaults:

| Variable | Value |
| --- | --- |
| `MYSQL_URL` | `jdbc:mysql://mysql:3306/simple_douyin?...` |
| `MYSQL_USERNAME` | `root` |
| `MYSQL_PASSWORD` | `simple_douyin_local` |
| `RECOMMEND_GRPC_HOST` | `recommend-service` |
| `RECOMMEND_GRPC_PORT` | `9090` |
| `UPLOADS_DIR` | `/app/uploads` |

These values are for local development and demo only. Do not reuse the default password or token secret for public deployment.

## Smoke test flow

1. Start the stack with `docker compose up --build -d`.
2. Verify `curl.exe http://localhost:8080/api/v1/health`.
3. Verify `curl.exe http://localhost:5173/api/v1/health`.
4. Open `http://localhost:5173`.
5. Register or log in from the web UI.
6. Publish a short MP4 from the upload page.
7. Open the profile page and confirm the video appears.
8. Return to the recommendation page and check feed, like, comments, and delete flows as needed.

## Troubleshooting

If a port is already occupied, stop the existing local process or edit the left side of the port mapping in `docker-compose.yml`.

If health reports `recommendService` as `DOWN`, wait a few seconds and retry. The API Server checks the gRPC channel and may need a short time after startup.

If the database needs a clean state, run:

```bash
docker compose down -v
docker compose up --build -d
```
