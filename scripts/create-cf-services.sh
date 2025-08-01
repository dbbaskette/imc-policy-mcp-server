#!/bin/bash

# ============================================================================
# Cloud Foundry Service Creation Script
# ============================================================================
# This script creates the required services for the IMC Policy MCP Server
# deployment on Cloud Foundry.
#
# USAGE:
#   ./scripts/create-cf-services.sh
#
# SERVICES CREATED:
#   - embed-model: AI embedding service (user-provided)
#   - chat-model: AI chat service (user-provided)  
#   - embed-db: PostgreSQL database with pgvector
# ============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if CF CLI is installed
if ! command -v cf &> /dev/null; then
    print_error "Cloud Foundry CLI not found. Please install cf CLI first."
    exit 1
fi

# Check if logged in
if ! cf target &> /dev/null; then
    print_error "Not logged into Cloud Foundry. Please run 'cf login' first."
    exit 1
fi

print_status "Creating Cloud Foundry services for IMC Policy MCP Server..."

# Check for required environment variables
if [ -z "$OPENAI_API_KEY" ]; then
    print_warning "OPENAI_API_KEY not set. You'll need to provide it when prompted."
    read -p "Enter your OpenAI API Key: " OPENAI_API_KEY
fi

# 1. Create PostgreSQL database service with pgvector
print_status "Creating PostgreSQL database service (embed-db)..."

if cf service embed-db &> /dev/null; then
    print_warning "Service 'embed-db' already exists. Skipping creation."
else
    # Try different PostgreSQL service names based on common CF marketplace offerings
    if cf marketplace | grep -q "postgres"; then
        SERVICE_NAME="postgres"
        PLAN="xlarge"
    elif cf marketplace | grep -q "postgresql"; then
        SERVICE_NAME="postgresql"
        PLAN="large"
    elif cf marketplace | grep -q "elephantsql"; then
        SERVICE_NAME="elephantsql"
        PLAN="turtle"
    else
        print_warning "No PostgreSQL service found in marketplace. Creating user-provided service."
        cf create-user-provided-service embed-db -p '{
            "uri": "postgres://username:password@hostname:5432/database",
            "extensions": ["pgvector"]
        }'
        print_success "Created user-provided service 'embed-db'"
    fi
    
    if [ ! -z "$SERVICE_NAME" ]; then
        cf create-service $SERVICE_NAME $PLAN embed-db -c '{"extensions": ["pgvector"]}'
        print_success "Created PostgreSQL service 'embed-db' with pgvector extension"
    fi
fi

# 2. Create OpenAI Embedding Service
print_status "Creating AI embedding service (embed-model)..."

if cf service embed-model &> /dev/null; then
    print_warning "Service 'embed-model' already exists. Skipping creation."
else
    cf create-user-provided-service embed-model -p "{
        \"api_key\": \"$OPENAI_API_KEY\",
        \"base_url\": \"https://api.openai.com\",
        \"model\": \"text-embedding-3-small\",
        \"type\": \"openai-embedding\"
    }"
    print_success "Created AI embedding service 'embed-model'"
fi

# 3. Create OpenAI Chat Service
print_status "Creating AI chat service (chat-model)..."

if cf service chat-model &> /dev/null; then
    print_warning "Service 'chat-model' already exists. Skipping creation."
else
    cf create-user-provided-service chat-model -p "{
        \"api_key\": \"$OPENAI_API_KEY\",
        \"base_url\": \"https://api.openai.com\",
        \"model\": \"gpt-4.1-nano\",
        \"type\": \"openai-chat\"
    }"
    print_success "Created AI chat service 'chat-model'"
fi

# 4. Verify all services
print_status "Verifying created services..."

echo ""
print_status "Service Summary:"
cf services | grep -E "(embed-db|embed-model|chat-model)" || print_warning "No services found with expected names"

echo ""
print_success "Service creation completed!"
print_status "You can now deploy your application using: cf push -f manifest.yml"

# 5. Optional: Show next steps
echo ""
print_status "Next Steps:"
echo "1. Build your application: mvn clean package"
echo "2. Deploy to Cloud Foundry: cf push -f manifest.yml"
echo "3. Monitor deployment: cf logs imc-policy-mcp-server"
echo "4. Test health endpoint: curl https://imc-policy-mcp-server.apps.internal/actuator/health"