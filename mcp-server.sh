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
SERVER_PORT=""
EMBEDDING_MODEL="local-nomic"
CHAT_MODEL="local"
FORCE_RELOAD_DATA=false
RE_EMBED_DATA=false
RAG_PROCESS=false
SOURCE_DIR="./local_data/source"

# Deployment scenarios
DEPLOYMENT_SCENARIO=""
CF_SERVICE_MODE=""  # "bound" or "hosted" for CF deployments

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "=== DEPLOYMENT SCENARIOS ==="
    echo ""
    echo "1) Local Development with Local Models (DEFAULT):"
    echo "   $0 --local-dev [--build] [--docker]"
    echo "   • Runs locally with Nomic embedding + Qwen chat on localhost:1234"
    echo "   • Uses local PostgreSQL (via Docker)"
    echo ""
    echo "2) Local Development with Hosted Models:"
    echo "   $0 --local-hosted [--build] [--docker]"
    echo "   • Runs locally with OpenAI embedding + chat (requires OPENAI_API_KEY)"
    echo "   • Uses local PostgreSQL (via Docker)"
    echo ""
    echo "3) Cloud Foundry with Bound Models:"
    echo "   $0 --cf-bound [--build]"
    echo "   • Deploys to CF with bound AI services (chat-model, embed-model)"
    echo "   • Uses bound PostgreSQL service (embed-db)"
    echo "   • Models configured via VCAP_SERVICES"
    echo ""
    echo "4) Cloud Foundry with Hosted Models:"
    echo "   $0 --cf-hosted [--build]"
    echo "   • Deploys to CF with API-based OpenAI models (requires OPENAI_API_KEY)"
    echo "   • Uses bound PostgreSQL service (embed-db)"
    echo "   • Models configured via environment variables"
    echo ""
    echo "=== GRANULAR OPTIONS ==="
    echo ""
    echo "Build & Environment:"
    echo "  --build         Build the application before running"
    echo "  --skip-tests    Skip tests during build (used with --build)"
    echo "  --docker        Start local PostgreSQL Docker container before running"
    echo "  --stop-docker   Stop and remove the local PostgreSQL Docker container"
    echo "  --port PORT     Specify server port for local runs (default: 8082)"
    echo ""
    echo "Model Selection (for custom configurations):"
    echo "  --local-embed   Use local Nomic embedding model"
    echo "  --openai-embed  Use OpenAI embedding model (requires OPENAI_API_KEY)"
    echo "  --local-chat    Use local chat model for query rewriting"
    echo "  --openai-chat   Use OpenAI chat model for query rewriting"
    echo ""
    echo "Data Management:"
    echo "  --reload-data   Force reload of vector data (clears existing data)"
    echo "  --re-embed      Re-embed all documents with current embedding model"
    echo "  --rag           Process PDF files and populate vector store, then exit"
    echo "  --source-dir    Directory containing PDF files (default: ./local_data/source)"
    echo ""
    echo "Legacy & Utilities:"
    echo "  --local         Equivalent to --local-dev (legacy)"
    echo "  --cf            Equivalent to --cf-bound (legacy)"
    echo "  --local-nomic   Legacy: both embed and chat local"
    echo "  --help          Show this help message"
    echo ""
    echo "=== EXAMPLES ==="
    echo ""
    echo "Quick Start (Scenario 1 - Local Dev):"
    echo "  $0 --local-dev --build --docker"
    echo ""
    echo "Production Deployment (Scenario 3 - CF Bound):"
    echo "  $0 --cf-bound --build"
    echo ""
    echo "Custom Configuration:"
    echo "  $0 --local-dev --openai-embed --local-chat --reload-data"
    echo ""
    echo "Re-embedding with Different Models:"
    echo "  $0 --local-dev --re-embed                    # Re-embed with local models"
    echo "  $0 --local-hosted --re-embed                 # Re-embed with OpenAI models"
    echo ""
    echo "Utilities:"
    echo "  $0 --stop-docker    # Stop local database"
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

kill_process_on_port() {
    local port=$1
    log_info "Checking for any process running on port $port..."
    # The [^0-9] part of the regex is to avoid lsof showing its own PID.
    local pid=$(lsof -ti :$port -sTCP:LISTEN)

    if [ -n "$pid" ]; then
        log_warning "Process with PID $pid found on port $port. Killing it..."
        kill -9 "$pid"
        log_success "Process killed."
    else
        log_info "No process found on port $port."
    fi
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
    # Set default port for local runs
    local port=${SERVER_PORT:-8082}
    
    kill_process_on_port $port
    log_info "Running application locally with profile: $PROFILE on port: $port"
    
    # Load environment variables
    load_env_file
    
    # Find the JAR file
    local jar_file=$(find_jar)
    log_info "Using JAR file: $jar_file"
    
    # Set up environment variables based on model choices
    local env_vars=""
    
    # Configure embedding model
    if [ "$EMBEDDING_MODEL" = "local-nomic" ]; then
        log_info "Using local Nomic embedding model (v2) on localhost:1234"
        env_vars="$env_vars -DEMBEDDING_BASE_URL=http://localhost:1234 -DEMBEDDING_MODEL=text-embedding-nomic-embed-text-v2 -DEMBEDDING_DIMENSIONS=768"
    elif [ "$EMBEDDING_MODEL" = "openai" ]; then
        log_info "Using OpenAI embedding model"
        env_vars="$env_vars -DEMBEDDING_BASE_URL=https://api.openai.com -DEMBEDDING_MODEL=text-embedding-3-small -DEMBEDDING_DIMENSIONS=768"
    fi
    
    # Configure chat model
    if [ "$CHAT_MODEL" = "local" ]; then
        log_info "Using local Qwen chat model on localhost:1234"
        env_vars="$env_vars -DCHAT_BASE_URL=http://localhost:1234 -DCHAT_MODEL=qwen/qwen3-4b-2507"
    elif [ "$CHAT_MODEL" = "openai" ]; then
        log_info "Using OpenAI chat model"
        env_vars="$env_vars -DCHAT_BASE_URL=https://api.openai.com -DCHAT_MODEL=gpt-4o-mini"
    fi
    
    # Add force reload data flag if specified
    if [ "$FORCE_RELOAD_DATA" = true ]; then
        env_vars="$env_vars -DFORCE_RELOAD_DATA=true"
    fi
    
    # Add re-embed data flag if specified
    if [ "$RE_EMBED_DATA" = true ]; then
        env_vars="$env_vars -DAPP_DATA_RE_EMBED=true"
        log_info "Re-embedding mode enabled - application will re-embed all documents and shutdown"
    fi
    
    # Add RAG processing flag if specified
    if [ "$RAG_PROCESS" = true ]; then
        env_vars="$env_vars -DAPP_RAG_PROCESS=true -DAPP_RAG_SOURCE_DIR=$SOURCE_DIR"
        log_info "RAG processing mode enabled - application will process PDF files and shutdown"
        log_info "Source directory: $SOURCE_DIR"
    fi
    
    # Run the application
    log_info "Starting IMC Policy MCP Server..."
    java -Dspring.profiles.active=$PROFILE -Dserver.port=$port $env_vars -jar "$jar_file"
}

create_cf_hosted_manifest() {
    log_info "Creating manifest for CF hosted deployment..."
    cat > manifest-hosted.yml << 'EOF'
---
applications:
- name: imc-policy-mcp-server
  memory: 1G
  instances: 1
  buildpacks:
    - java_buildpack
  path: ./target/imc-policy-mcp-server-0.0.2.jar
  env:
    SPRING_PROFILES_ACTIVE: cloud
    JBP_CONFIG_OPEN_JDK_JRE: '{ jre: { version: 21.+ } }'
    # API-based OpenAI configuration (requires OPENAI_API_KEY to be set)
    OPENAI_API_KEY: ((openai-api-key))
    OPENAI_EMBEDDING_MODEL: text-embedding-3-small
    OPENAI_EMBEDDING_DIMENSIONS: 768
    OPENAI_MODEL: gpt-4o-mini
  services:
    - embed-db
  routes:
    - route: imc-policy-mcp-server-hosted.apps.internal
EOF
    log_success "Created manifest-hosted.yml for API-based deployment"
}

deploy_cf() {
    log_info "Deploying to Cloud Foundry..."
    
    local manifest_file="manifest.yml"
    
    # Determine which manifest to use
    if [ "$CF_SERVICE_MODE" = "hosted" ]; then
        create_cf_hosted_manifest
        manifest_file="manifest-hosted.yml"
        log_info "Using hosted models configuration (API-based)"
        
        # Check for OPENAI_API_KEY
        if [ -z "$OPENAI_API_KEY" ]; then
            log_warning "OPENAI_API_KEY not set. Make sure it's configured in your CF deployment."
        fi
    else
        log_info "Using bound services configuration (VCAP_SERVICES)"
        if [ ! -f "manifest.yml" ]; then
            log_error "manifest.yml not found. Cannot deploy to Cloud Foundry."
            exit 1
        fi
    fi
    
    # Find the JAR file
    local jar_file=$(find_jar)
    log_info "Using JAR file: $jar_file"
    
    # Load environment variables for cf push
    load_env_file
    
    # Deploy to Cloud Foundry
    log_info "Pushing application to Cloud Foundry using $manifest_file..."
    if ! cf push -f "$manifest_file"; then
        log_error "Cloud Foundry deployment failed!"
        exit 1
    fi
    
    log_success "Application deployed successfully to Cloud Foundry"
    log_info "Check application status: cf apps"
    log_info "View logs: cf logs imc-policy-mcp-server --recent"
    
    # Cleanup temporary manifest if created
    if [ "$CF_SERVICE_MODE" = "hosted" ] && [ -f "manifest-hosted.yml" ]; then
        log_info "Cleaning up temporary manifest file"
        rm -f manifest-hosted.yml
    fi
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        # === DEPLOYMENT SCENARIOS ===
        --local-dev)
            DEPLOYMENT_SCENARIO="local-dev"
            PROFILE="local"
            EMBEDDING_MODEL="local-nomic"
            CHAT_MODEL="local"
            shift
            ;;
        --local-hosted)
            DEPLOYMENT_SCENARIO="local-hosted"
            PROFILE="local"
            EMBEDDING_MODEL="openai"
            CHAT_MODEL="openai"
            shift
            ;;
        --cf-bound)
            DEPLOYMENT_SCENARIO="cf-bound"
            PROFILE="cloud"
            CF_SERVICE_MODE="bound"
            shift
            ;;
        --cf-hosted)
            DEPLOYMENT_SCENARIO="cf-hosted"
            PROFILE="cloud"
            CF_SERVICE_MODE="hosted"
            shift
            ;;
        
        # === BUILD & ENVIRONMENT ===
        --build)
            BUILD=true
            shift
            ;;
        --skip-tests)
            SKIP_TESTS=true
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
        --port)
            SERVER_PORT="$2"
            shift 2
            ;;
        
        # === MODEL SELECTION (for custom configurations) ===
        --local-embed)
            EMBEDDING_MODEL="local-nomic"
            shift
            ;;
        --openai-embed)
            EMBEDDING_MODEL="openai"
            shift
            ;;
        --local-chat)
            CHAT_MODEL="local"
            shift
            ;;
        --openai-chat)
            CHAT_MODEL="openai"
            shift
            ;;
        
        # === DATA MANAGEMENT ===
        --reload-data)
            FORCE_RELOAD_DATA=true
            shift
            ;;
        --re-embed)
            RE_EMBED_DATA=true
            shift
            ;;
        --rag)
            RAG_PROCESS=true
            shift
            ;;
        --source-dir)
            SOURCE_DIR="$2"
            shift 2
            ;;
        
        # === LEGACY & UTILITIES ===
        --local)
            # Legacy: equivalent to --local-dev
            DEPLOYMENT_SCENARIO="local-dev"
            PROFILE="local"
            EMBEDDING_MODEL="local-nomic"
            CHAT_MODEL="local"
            shift
            ;;
        --cf)
            # Legacy: equivalent to --cf-bound
            DEPLOYMENT_SCENARIO="cf-bound"
            PROFILE="cloud"
            CF_SERVICE_MODE="bound"
            shift
            ;;
        --local-nomic)
            # Legacy flag - sets both embedding and chat to local
            EMBEDDING_MODEL="local-nomic"
            CHAT_MODEL="local"
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

# Set defaults if no deployment scenario specified
if [ -z "$DEPLOYMENT_SCENARIO" ]; then
    DEPLOYMENT_SCENARIO="local-dev"
    log_info "No deployment scenario specified, defaulting to --local-dev"
fi

# Main execution
log_info "IMC Policy MCP Server - Starting..."
log_info "Deployment Scenario: $DEPLOYMENT_SCENARIO"

case $DEPLOYMENT_SCENARIO in
    "local-dev")
        log_info "=== LOCAL DEVELOPMENT WITH LOCAL MODELS ==="
        log_info "• Embedding: Nomic v2 on localhost:1234"
        log_info "• Chat: Qwen on localhost:1234"
        log_info "• Database: Local PostgreSQL (Docker)"
        ;;
    "local-hosted")
        log_info "=== LOCAL DEVELOPMENT WITH HOSTED MODELS ==="
        log_info "• Embedding: OpenAI API"
        log_info "• Chat: OpenAI API"
        log_info "• Database: Local PostgreSQL (Docker)"
        if [ -z "$OPENAI_API_KEY" ]; then
            log_error "OPENAI_API_KEY is required for hosted models. Please set it in your .env file."
            exit 1
        fi
        ;;
    "cf-bound")
        log_info "=== CLOUD FOUNDRY WITH BOUND SERVICES ==="
        log_info "• Embedding: Bound service (embed-model)"
        log_info "• Chat: Bound service (chat-model)"
        log_info "• Database: Bound service (embed-db)"
        ;;
    "cf-hosted")
        log_info "=== CLOUD FOUNDRY WITH HOSTED MODELS ==="
        log_info "• Embedding: OpenAI API"
        log_info "• Chat: OpenAI API"
        log_info "• Database: Bound service (embed-db)"
        ;;
esac

# Build if requested
if [ "$BUILD" = true ]; then
    build_application
fi

# Start docker if requested for local scenarios
if [ "$DOCKER" = true ] && [ "$PROFILE" = "local" ]; then
    start_local_db
elif [ "$DOCKER" = true ] && [ "$PROFILE" = "cloud" ]; then
    log_warning "Docker flag ignored for Cloud Foundry deployments"
fi

# Run based on profile
if [ "$PROFILE" = "cloud" ]; then
    deploy_cf
else
    run_local
fi
