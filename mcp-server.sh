#!/bin/bash

# IMC Policy MCP Server - Run Script
# Usage: ./mcp-server.sh [options]

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Default values
BUILD=false
SKIP_TESTS=false
PROFILE="local"
DOCKER=false
JAR_PATTERN="target/imc-policy-mcp-server-*.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --build         Build the application before running"
    echo "  --skip-tests    Skip tests during build (used with --build)"
    echo "  --local         Run with local profile (default)"
    echo "  --docker        Start local PostgreSQL Docker container before running"
    echo "  --stop-docker   Stop and remove the local PostgreSQL Docker container"
    echo "  --cf            Deploy to Cloud Foundry"
    echo "  --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --build --local --docker  Build and run locally with Docker DB"
    echo "  $0 --stop-docker             Stop the local Docker DB"
    echo "  $0 --build --local          Build and run locally"
    echo "  $0 --build --skip-tests     Build without tests and run locally"
    echo "  $0 --cf                     Deploy to Cloud Foundry"
    echo "  $0 --build --cf             Build and deploy to Cloud Foundry"
}

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

load_env_file() {
    if [ -f ".env" ]; then
        log_info "Loading environment variables from .env file"
        set -a  # automatically export all variables
        source .env
        set +a  # stop automatically exporting
    else
        log_warning ".env file not found. Some configurations may be missing."
        log_info "Copy .env.example to .env and configure your environment variables."
    fi
}

start_local_db() {
  load_env_file
  log_info "Checking for local PostgreSQL container..."
  if [ ! "$(docker ps -q -f name=imc-postgres)" ]; then
    if [ "$(docker ps -aq -f status=exited -f name=imc-postgres)" ]; then
      log_info "Restarting existing container 'imc-postgres'..."
      docker start imc-postgres
    else
      log_info "Starting new PostgreSQL container 'imc-postgres'..."
      docker run -d --name imc-postgres \
        -e POSTGRES_USER=${DB_USER:-imc_user} \
        -e POSTGRES_PASSWORD=${DB_PASSWORD:-imc_password} \
        -e POSTGRES_DB=${DB_NAME:-imc_policy} \
        -p ${DB_PORT:-5432}:5432 \
        pgvector/pgvector:pg15
    fi
    log_info "Waiting for PostgreSQL to be ready..."
    sleep 5
  else
    log_info "PostgreSQL container 'imc-postgres' is already running."
  fi
}

stop_local_db() {
  log_info "Checking for container 'imc-postgres'..."
  if [ ! "$(docker ps -a -q -f name=imc-postgres)" ]; then
    log_warning "Container 'imc-postgres' not found."
    exit 0
  fi
  log_info "Stopping and removing local PostgreSQL container 'imc-postgres'..."
  docker stop imc-postgres
  docker rm imc-postgres
  log_success "Container 'imc-postgres' stopped and removed."
}

find_jar() {
    local jar_file=$(find target -name "imc-policy-mcp-server-*.jar" 2>/dev/null | head -1)
    if [ -z "$jar_file" ]; then
        log_error "No JAR file found in target directory. Please build first with --build option."
        exit 1
    fi
    echo "$jar_file"
}

build_application() {
    log_info "Building application..."
    
    local maven_opts=""
    if [ "$SKIP_TESTS" = true ]; then
        maven_opts="-DskipTests"
        log_info "Skipping tests during build"
    fi
    
    if ! ./mvnw clean package $maven_opts; then
        log_error "Build failed!"
        exit 1
    fi
    
    log_success "Build completed successfully"
}

run_local() {
    log_info "Running application locally with profile: $PROFILE"
    
    # Load environment variables
    load_env_file
    
    # Find the JAR file
    local jar_file=$(find_jar)
    log_info "Using JAR file: $jar_file"
    
    # Run the application
    log_info "Starting IMC Policy MCP Server..."
    java -Dspring.profiles.active=$PROFILE -jar "$jar_file"
}

deploy_cf() {
    log_info "Deploying to Cloud Foundry..."
    
    # Check if manifest.yml exists
    if [ ! -f "manifest.yml" ]; then
        log_error "manifest.yml not found. Cannot deploy to Cloud Foundry."
        exit 1
    fi
    
    # Find the JAR file
    local jar_file=$(find_jar)
    log_info "Using JAR file: $jar_file"
    
    # Load environment variables for cf push
    load_env_file
    
    # Deploy to Cloud Foundry
    log_info "Pushing application to Cloud Foundry..."
    if ! cf push -f manifest.yml; then
        log_error "Cloud Foundry deployment failed!"
        exit 1
    fi
    
    log_success "Application deployed successfully to Cloud Foundry"
    log_info "Check application status: cf apps"
    log_info "View logs: cf logs imc-policy-mcp-server --recent"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --local)
            PROFILE="local"
            shift
            ;;
        --docker)
            DOCKER=true
            shift
            ;;
        --stop-docker)
            stop_local_db
            exit 0
            ;;
        --cf)
            PROFILE="cloud"
            shift
            ;;
        --help)
            print_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Main execution
log_info "IMC Policy MCP Server - Starting..."

# Build if requested
if [ "$BUILD" = true ]; then
    build_application
fi

# Start docker if requested
if [ "$DOCKER" = true ]; then
    start_local_db
fi

# Run based on profile
if [ "$PROFILE" = "cloud" ]; then
    deploy_cf
else
    run_local
fi
