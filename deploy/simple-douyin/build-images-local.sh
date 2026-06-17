#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
IMAGE_DIR="${SCRIPT_DIR}/images"

cd "${REPO_ROOT}"

mvn -pl backend/api-server,backend/recommend-service -am clean package -DskipTests

docker buildx build --platform linux/amd64 --load \
  -f Dockerfile.api-server \
  -t simple-douyin-api-server:prod \
  .

docker buildx build --platform linux/amd64 --load \
  -f Dockerfile.recommend-service \
  -t simple-douyin-recommend-service:prod \
  .

docker pull --platform linux/amd64 mysql:8.0

mkdir -p "${IMAGE_DIR}"

docker save -o "${IMAGE_DIR}/simple-douyin-api-server-prod.tar" simple-douyin-api-server:prod
docker save -o "${IMAGE_DIR}/simple-douyin-recommend-service-prod.tar" simple-douyin-recommend-service:prod
docker save -o "${IMAGE_DIR}/mysql-8.0-amd64.tar" mysql:8.0

cat <<EOF
Local image tar files are ready:
  ${IMAGE_DIR}/simple-douyin-api-server-prod.tar
  ${IMAGE_DIR}/simple-douyin-recommend-service-prod.tar
  ${IMAGE_DIR}/mysql-8.0-amd64.tar

After review and deployment confirmation, upload them with:
  scp -i ~/.ssh/ShiXun01.pem ${IMAGE_DIR}/*.tar root@47.95.238.140:/opt/simple-douyin-backend/images/
EOF
