#!/bin/bash
# =============================================================================
# deploy.sh
# Blue-Green Deployment Script — Local Mode (No Docker Hub)
# =============================================================================
set -euo pipefail

# --- Configuration ---
DEPLOY_BASE_DIR="${DEPLOY_BASE_DIR:-/opt/robot_deploy}"
DOCKER_DIR="${DEPLOY_BASE_DIR}/docker"
NGINX_CONF_DIR="${DEPLOY_BASE_DIR}/nginx/conf.d"
NGINX_UPSTREAM_FILE="${NGINX_CONF_DIR}/upstream.inc"
NGINX_CONTAINER="robot-nginx"
COMPOSE_APP_FILE="${DOCKER_DIR}/app-stack.yml"
NETWORK_NAME="robot-network"

# --- Port Assignments ---
BLUE_BE_PORT=20881
BLUE_FE_PORT=20301
GREEN_BE_PORT=20882
GREEN_FE_PORT=20302

# --- Validate Required Env Vars ---
: "${BE_IMAGE:?Error: BE_IMAGE env var is required}"
: "${FE_IMAGE:?Error: FE_IMAGE env var is required}"
: "${DB_PASSWORD:?Error: DB_PASSWORD env var is required}"

# --- Ensure Docker Network Exists ---
docker network inspect "${NETWORK_NAME}" >/dev/null 2>&1 || \
  docker network create "${NETWORK_NAME}"

# --- Ensure deploy dir has latest compose files ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

mkdir -p "${DOCKER_DIR}"
mkdir -p "${NGINX_CONF_DIR}"
touch "${NGINX_UPSTREAM_FILE}"  # Ensure file exists to prevent Nginx startup failure
cp -f "${REPO_ROOT}/docker/app-stack.yml"       "${DOCKER_DIR}/app-stack.yml"
cp -f "${REPO_ROOT}/nginx/conf.d/default.conf"  "${NGINX_CONF_DIR}/default.conf"

# --- Determine Target Stack (Blue-Green Swap) ---
if [[ -f "${NGINX_UPSTREAM_FILE}" ]] && grep -q "${BLUE_FE_PORT}" "${NGINX_UPSTREAM_FILE}"; then
  TARGET_STACK="green"
  TARGET_FE_PORT="${GREEN_FE_PORT}"
  TARGET_BE_PORT="${GREEN_BE_PORT}"
  OLD_STACK="blue"
else
  TARGET_STACK="blue"
  TARGET_FE_PORT="${BLUE_FE_PORT}"
  TARGET_BE_PORT="${BLUE_BE_PORT}"
  OLD_STACK="green"
fi

echo "=========================================="
echo " Deploying to [${TARGET_STACK}] stack"
echo "   BE: :${TARGET_BE_PORT}  image=${BE_IMAGE}"
echo "   FE: :${TARGET_FE_PORT}  image=${FE_IMAGE}"
echo "=========================================="

# --- Start Target Stack ---
echo "[1/4] Starting ${TARGET_STACK} stack..."
export STACK="${TARGET_STACK}"
export BE_PORT="${TARGET_BE_PORT}"
export FE_PORT="${TARGET_FE_PORT}"

docker-compose -f "${COMPOSE_APP_FILE}" --project-name "robot_${TARGET_STACK}" up -d

# --- Health Check ---
echo "[2/4] Health check on port ${TARGET_BE_PORT}..."
MAX_RETRIES=15
COUNT=0
HEALTHY=false

while [ "${COUNT}" -lt "${MAX_RETRIES}" ]; do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "http://host.docker.internal:${TARGET_BE_PORT}/actuator/health" 2>/dev/null || echo "000")
  if [ "${HTTP_CODE}" == "200" ]; then
    echo "  ✅ Health check passed!"
    HEALTHY=true
    break
  fi
  echo "  ⏳ Waiting... (${COUNT}/${MAX_RETRIES}) - HTTP ${HTTP_CODE}"
  sleep 5
  COUNT=$((COUNT + 1))
done

if [ "${HEALTHY}" = false ]; then
  echo "  ❌ Health check failed. Printing logs for debugging:"
  echo "----- [BACKEND LOGS] -----"
  docker logs --tail 50 "robot-backend-${TARGET_STACK}" || true
  echo "----- [FRONTEND LOGS] -----"
  docker logs --tail 50 "robot-frontend-${TARGET_STACK}" || true
  
  echo "Rolling back ${TARGET_STACK} stack..."
  docker-compose -f "${COMPOSE_APP_FILE}" --project-name "robot_${TARGET_STACK}" down
  exit 1
fi

# --- Switch Nginx Upstream ---
echo "[3/4] Switching Nginx upstream to ${TARGET_STACK}..."
cat <<EOF > "${NGINX_UPSTREAM_FILE}"
upstream backend_servers {
    server robot-backend-${TARGET_STACK}:8080;
}
upstream frontend_servers {
    server robot-frontend-${TARGET_STACK}:3000;
}
EOF
docker exec "${NGINX_CONTAINER}" nginx -s reload
echo "  ✅ Nginx switched to ${TARGET_STACK}."

# --- Stop Old Stack ---
echo "[4/4] Stopping old [${OLD_STACK}] stack..."
sleep 5
docker-compose -f "${COMPOSE_APP_FILE}" --project-name "robot_${OLD_STACK}" down || true

echo ""
echo "=========================================="
echo " ✅ Done! Active: ${TARGET_STACK}"
echo "    Access: http://localhost:8080"
echo "=========================================="
