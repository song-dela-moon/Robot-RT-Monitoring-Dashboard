#!/bin/bash
# =============================================================================
# manage.sh - Robot Monitor Environment Manager
# =============================================================================

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA_FILE="${PROJECT_ROOT}/docker/infrastructure.yml"
JENKINS_FILE="${PROJECT_ROOT}/docker/jenkins-compose.yml"
NETWORK_NAME="robot-network"

function usage() {
    echo "Usage: ./manage.sh [command]"
    echo ""
    echo "Commands:"
    echo "  infra-up     Start MySQL and Nginx (Port 3306, 8080)"
    echo "  jenkins-up   Start Jenkins (Port 18080)"
    echo "  clean        Stop and remove all apps, MySQL, and Nginx (Jenkins stays)"
    echo "  reset-all    Stop and remove EVERYTHING including Jenkins and Data"
    echo "  status       Show status of all robot-monitor containers"
    echo "  logs         Tail logs for infra (MySQL and Nginx)"
}

function infra_up() {
    echo "🚀 Starting Infrastructure (MySQL, Nginx)..."
    docker network create "${NETWORK_NAME}" 2>/dev/null || true
    docker-compose -f "${INFRA_FILE}" up -d
}

function jenkins_up() {
    echo "🚀 Starting Jenkins..."
    docker network create "${NETWORK_NAME}" 2>/dev/null || true
    docker-compose -f "${JENKINS_FILE}" up -d
}

function clean() {
    echo "🧹 Cleaning up Applications and Infrastructure..."
    docker ps -a -q --filter "name=robot-backend" --filter "name=robot-frontend" | xargs -r docker rm -f
    docker rm -f robot-mysql robot-nginx 2>/dev/null || true
    echo "✅ Clean finished. (Jenkins was NOT removed)"
}

function reset_all() {
    echo "⚠️  WARNING: This will remove EVERY container and Jenkins data."
    read -p "Are you sure? (y/N) " confirm
    if [[ $confirm == [yY] ]]; then
        docker-compose -f "${INFRA_FILE}" down -v 2>/dev/null || true
        docker-compose -f "${JENKINS_FILE}" down -v 2>/dev/null || true
        docker ps -a -q --filter "name=robot-" | xargs -r docker rm -f
        echo "✅ Full reset complete."
    else
        echo "Canceled."
    fi
}

function status() {
    echo "📊 Container Status:"
    docker ps -a --filter "name=robot-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

case "$1" in
    infra-up)    infra_up ;;
    jenkins-up)  jenkins_up ;;
    clean)       clean ;;
    reset-all)   reset_all ;;
    status)      status ;;
    logs)        docker-compose -f "${INFRA_FILE}" logs -f ;;
    *)           usage; exit 1 ;;
esac
