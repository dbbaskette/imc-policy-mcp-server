#!/bin/bash

# MCP Server Management Script
# Usage: ./mcp-server.sh [--sse|--stdio] [start|stop|restart|status] [OPTIONS]

set -e

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Unicode symbols
CHECK_MARK="✅"
CROSS_MARK="❌"
ROCKET="🚀"
GEAR="⚙️"
STOP_SIGN="🛑"
INFO="ℹ️"

# Default values
TRANSPORT_MODE=""
ACTION=""
BUILD_FIRST=false
USE_TEST_PROFILE=false
JAR_FILE="target/imc-policy-mcp-server-0.0.1-SNAPSHOT.jar"
PID_FILE_STDIO="/tmp/imc-policy-mcp-server-stdio.pid"
PID_FILE_SSE="/tmp/imc-policy-mcp-server-sse.pid"
PID_FILE_STDIO_TEST="/tmp/imc-policy-mcp-server-stdio-test.pid"
PID_FILE_SSE_TEST="/tmp/imc-policy-mcp-server-sse-test.pid"
LOG_FILE_STDIO="/tmp/imc-policy-mcp-server-stdio.log"
LOG_FILE_SSE="/tmp/imc-policy-mcp-server-sse.log"
LOG_FILE_STDIO_TEST="/tmp/imc-policy-mcp-server-stdio-test.log"
LOG_FILE_SSE_TEST="/tmp/imc-policy-mcp-server-sse-test.log"

# Function to show help
show_help() {
    echo -e "${CYAN}MCP Server Management Script${NC}"
    echo ""
    echo "Usage: ./mcp-server.sh [TRANSPORT] [ACTION] [OPTIONS]"
    echo ""
    echo "Transport Modes:"
    echo "  --sse           Server-Sent Events transport (web clients)"
    echo "  --stdio         Standard I/O transport (Claude Desktop)"
    echo ""
    echo "Profile Options:"
    echo "  --test          Use test profiles with H2 database and sample data"
    echo "                  (default for development, includes insurance sample data)"
    echo "  --prod          Use production profiles (requires PostgreSQL setup)"
    echo ""
    echo "Actions:"
    echo "  start           Start the MCP server"
    echo "  stop            Stop the MCP server"
    echo "  restart         Restart the MCP server"
    echo "  status          Show server status"
    echo ""
    echo "Options:"
    echo "  --build         Build the project before starting"
    echo "  --help, -h      Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./mcp-server.sh --sse --test start                    # Start SSE server with test database"
    echo "  ./mcp-server.sh --stdio --test start --build          # Build and start STDIO server with sample data"
    echo "  ./mcp-server.sh --sse --prod start                    # Start SSE server with production database"
    echo "  ./mcp-server.sh --stdio --test stop                   # Stop STDIO test server"
    echo "  ./mcp-server.sh --sse --test status                   # Check SSE test server status"
    echo "  ./mcp-server.sh --stdio --test restart                # Restart STDIO test server"
    echo ""
    echo "Transport Details:"
    echo "  SSE Mode:"
    echo "    - Web-based transport for MCP clients"
    echo "    - Accessible at http://localhost:8080"
    echo "    - SSE endpoint: http://localhost:8080/sse"
    echo "    - MCP endpoint: http://localhost:8080/mcp/message"
    echo ""
    echo "  STDIO Mode:"
    echo "    - Command-line transport for Claude Desktop"
    echo "    - Uses process input/output for communication"
    echo "    - Compatible with Claude Desktop MCP configuration"
    echo ""
    echo "Profile Details:"
    echo "  Test Profiles (--test):"
    echo "    - Uses H2 in-memory database with sample insurance data"
    echo "    - 5 sample customers (IDs: 100001-100005)"
    echo "    - 10 sample accidents with G-force measurements"
    echo "    - Automatic database initialization"
    echo "    - Perfect for development and testing"
    echo ""
    echo "  Production Profiles (--prod):"
    echo "    - Requires PostgreSQL database setup"
    echo "    - Production-ready configuration"
    echo "    - No sample data included"
    echo ""
}

# Function to get PID file based on transport mode and profile
get_pid_file() {
    if [ "$USE_TEST_PROFILE" = true ]; then
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            echo "$PID_FILE_STDIO_TEST"
        else
            echo "$PID_FILE_SSE_TEST"
        fi
    else
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            echo "$PID_FILE_STDIO"
        else
            echo "$PID_FILE_SSE"
        fi
    fi
}

# Function to get log file based on transport mode and profile
get_log_file() {
    if [ "$USE_TEST_PROFILE" = true ]; then
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            echo "$LOG_FILE_STDIO_TEST"
        else
            echo "$LOG_FILE_SSE_TEST"
        fi
    else
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            echo "$LOG_FILE_STDIO"
        else
            echo "$LOG_FILE_SSE"
        fi
    fi
}

# Function to check if server is running
is_server_running() {
    local pid_file=$(get_pid_file)
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 "$pid" 2>/dev/null; then
            return 0  # Server is running
        else
            # PID file exists but process is dead, clean up
            rm -f "$pid_file"
            return 1  # Server is not running
        fi
    else
        return 1  # Server is not running
    fi
}

# Function to build the project
build_project() {
    echo -e "${BLUE}${GEAR} Building project...${NC}"
    if ./mvnw clean install -DskipTests; then
        echo -e "${GREEN}${CHECK_MARK} Build successful${NC}"
    else
        echo -e "${RED}${CROSS_MARK} Build failed${NC}"
        exit 1
    fi
}

# Function to start the server
start_server() {
    local pid_file=$(get_pid_file)
    local log_file=$(get_log_file)
    
    if is_server_running; then
        local pid=$(cat "$pid_file")
        echo -e "${YELLOW}${INFO} MCP server ($TRANSPORT_MODE mode) is already running (PID: $pid)${NC}"
        return 0
    fi
    
    # Check if JAR exists
    if [ ! -f "$JAR_FILE" ]; then
        echo -e "${RED}${CROSS_MARK} JAR file not found: $JAR_FILE${NC}"
        if [ "$BUILD_FIRST" = true ]; then
            build_project
        else
            echo -e "${YELLOW}${GEAR} Run with --build option to build the project first${NC}"
            exit 1
        fi
    fi
    
    # Build if requested
    if [ "$BUILD_FIRST" = true ]; then
        build_project
    fi
    
    local profile_desc="$([ "$USE_TEST_PROFILE" = true ] && echo "test" || echo "production")"
    echo -e "${BLUE}${ROCKET} Starting IMC Policy MCP server in $TRANSPORT_MODE $profile_desc mode...${NC}"
    
    # Set JVM arguments based on transport mode and profile
    if [ "$USE_TEST_PROFILE" = true ]; then
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            JVM_ARGS="-Dspring.profiles.active=stdio-test"
            echo -e "${CYAN}Mode: STDIO Test (Claude Desktop compatible with H2 sample data)${NC}"
        else
            JVM_ARGS="-Dspring.profiles.active=sse-test"
            echo -e "${CYAN}Mode: SSE Test (Web transport with H2 sample data)${NC}"
            echo -e "${CYAN}URL: http://localhost:8080${NC}"
            echo -e "${CYAN}SSE: http://localhost:8080/sse${NC}"
            echo -e "${CYAN}MCP: http://localhost:8080/mcp/message${NC}"
        fi
        echo -e "${CYAN}Database: H2 in-memory with sample insurance data${NC}"
        echo -e "${CYAN}Sample Customers: 100001-100005${NC}"
        echo -e "${CYAN}Sample Accidents: 10 records with G-force data${NC}"
    else
        if [ "$TRANSPORT_MODE" = "stdio" ]; then
            JVM_ARGS="-Dspring.profiles.active=stdio"
            echo -e "${CYAN}Mode: STDIO Production (Claude Desktop compatible)${NC}"
        else
            JVM_ARGS="-Dspring.profiles.active=sse"
            echo -e "${CYAN}Mode: SSE Production (Web transport)${NC}"
            echo -e "${CYAN}URL: http://localhost:8080${NC}"
            echo -e "${CYAN}SSE: http://localhost:8080/sse${NC}"
            echo -e "${CYAN}MCP: http://localhost:8080/mcp/message${NC}"
        fi
        echo -e "${CYAN}Database: PostgreSQL (production)${NC}"
    fi
    echo -e "${CYAN}Logs: $log_file${NC}"
    
    # Start server in background
    nohup java $JVM_ARGS -jar "$JAR_FILE" > "$log_file" 2>&1 &
    local server_pid=$!
    
    # Save PID to file
    echo "$server_pid" > "$pid_file"
    
    # Wait a moment and check if server started successfully
    sleep 3
    
    if kill -0 "$server_pid" 2>/dev/null; then
        echo -e "${GREEN}${CHECK_MARK} IMC Policy MCP server started successfully (PID: $server_pid)${NC}"
        
        if [ "$TRANSPORT_MODE" = "sse" ]; then
            echo -e "${BLUE}${INFO} Waiting for server to be ready...${NC}"
            sleep 2
            
            # Test if SSE endpoint is accessible
            local health_check=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 --max-time 5 http://localhost:8080/ 2>/dev/null || echo "000")
            if [ "$health_check" = "200" ] || [ "$health_check" = "404" ]; then
                echo -e "${GREEN}${CHECK_MARK} Server is responding to HTTP requests${NC}"
            else
                echo -e "${YELLOW}${INFO} Server may still be starting up (HTTP $health_check)${NC}"
            fi
        fi
        
        echo ""
        echo -e "${GREEN}Server is running. Use the following commands:${NC}"
        echo -e "  ${YELLOW}Status: ./mcp-server.sh --$TRANSPORT_MODE status${NC}"
        echo -e "  ${YELLOW}Stop:   ./mcp-server.sh --$TRANSPORT_MODE stop${NC}"
        echo -e "  ${YELLOW}Logs:   tail -f $log_file${NC}"
    else
        echo -e "${RED}${CROSS_MARK} Failed to start MCP server${NC}"
        echo -e "${YELLOW}Check logs: tail $log_file${NC}"
        rm -f "$pid_file"
        exit 1
    fi
}

# Function to stop the server
stop_server() {
    local pid_file=$(get_pid_file)
    
    if ! is_server_running; then
        echo -e "${YELLOW}${INFO} MCP server ($TRANSPORT_MODE mode) is not running${NC}"
        return 0
    fi
    
    local pid=$(cat "$pid_file")
    echo -e "${BLUE}${STOP_SIGN} Stopping MCP server ($TRANSPORT_MODE mode, PID: $pid)...${NC}"
    
    # Graceful shutdown
    kill "$pid" 2>/dev/null || true
    
    # Wait for graceful shutdown
    local count=0
    while kill -0 "$pid" 2>/dev/null && [ $count -lt 10 ]; do
        sleep 1
        ((count++))
    done
    
    # Force kill if still running
    if kill -0 "$pid" 2>/dev/null; then
        echo -e "${YELLOW}${INFO} Forcing server shutdown...${NC}"
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
    fi
    
    # Clean up PID file
    rm -f "$pid_file"
    
    if kill -0 "$pid" 2>/dev/null; then
        echo -e "${RED}${CROSS_MARK} Failed to stop server${NC}"
        exit 1
    else
        echo -e "${GREEN}${CHECK_MARK} MCP server stopped successfully${NC}"
    fi
}

# Function to show server status
show_status() {
    local pid_file=$(get_pid_file)
    local log_file=$(get_log_file)
    
    echo -e "${CYAN}${INFO} MCP Server Status ($TRANSPORT_MODE mode)${NC}"
    echo ""
    
    if is_server_running; then
        local pid=$(cat "$pid_file")
        echo -e "${GREEN}${CHECK_MARK} Server Status: RUNNING${NC}"
        echo -e "${CYAN}Process ID: $pid${NC}"
        echo -e "${CYAN}PID File: $pid_file${NC}"
        echo -e "${CYAN}Log File: $log_file${NC}"
        
        if [ "$TRANSPORT_MODE" = "sse" ]; then
            echo -e "${CYAN}Server URL: http://localhost:8080${NC}"
            echo -e "${CYAN}SSE Endpoint: http://localhost:8080/sse${NC}"
            echo -e "${CYAN}MCP Endpoint: http://localhost:8080/mcp/message${NC}"
            
            # Test connectivity
            local health_check=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 --max-time 3 http://localhost:8080/ 2>/dev/null || echo "000")
            if [ "$health_check" = "200" ] || [ "$health_check" = "404" ]; then
                echo -e "${GREEN}${CHECK_MARK} HTTP Status: Responding (HTTP $health_check)${NC}"
            else
                echo -e "${YELLOW}${INFO} HTTP Status: Not responding (HTTP $health_check)${NC}"
            fi
        else
            echo -e "${CYAN}Transport: STDIO (Claude Desktop compatible)${NC}"
        fi
        
        # Show recent log entries
        if [ -f "$log_file" ]; then
            echo ""
            echo -e "${BLUE}Recent log entries:${NC}"
            tail -5 "$log_file" | sed 's/^/  /'
        fi
    else
        echo -e "${RED}${CROSS_MARK} Server Status: NOT RUNNING${NC}"
        
        # Check if log file exists and show last entries
        if [ -f "$log_file" ]; then
            echo -e "${YELLOW}${INFO} Last log entries:${NC}"
            tail -5 "$log_file" | sed 's/^/  /'
        fi
    fi
}

# Function to restart the server
restart_server() {
    echo -e "${BLUE}${GEAR} Restarting MCP server ($TRANSPORT_MODE mode)...${NC}"
    stop_server
    sleep 2
    start_server
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --sse)
            TRANSPORT_MODE="sse"
            shift
            ;;
        --stdio)
            TRANSPORT_MODE="stdio"
            shift
            ;;
        --test)
            USE_TEST_PROFILE=true
            shift
            ;;
        --prod)
            USE_TEST_PROFILE=false
            shift
            ;;
        start|stop|restart|status)
            ACTION="$1"
            shift
            ;;
        --build)
            BUILD_FIRST=true
            shift
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}${CROSS_MARK} Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set default profile if not specified
if [ "$USE_TEST_PROFILE" = false ] && ! [[ "$*" == *"--prod"* ]]; then
    USE_TEST_PROFILE=true  # Default to test profile for ease of use
fi

# Validate arguments
if [ -z "$TRANSPORT_MODE" ]; then
    echo -e "${RED}${CROSS_MARK} Transport mode required (--sse or --stdio)${NC}"
    show_help
    exit 1
fi

if [ -z "$ACTION" ]; then
    echo -e "${RED}${CROSS_MARK} Action required (start, stop, restart, or status)${NC}"
    show_help
    exit 1
fi

# Print header
echo -e "${PURPLE}╔══════════════════════════════════════════╗${NC}"
echo -e "${PURPLE}║      IMC Policy MCP Server Manager       ║${NC}"
echo -e "${PURPLE}╚══════════════════════════════════════════╝${NC}"
echo ""

# Execute the requested action
case $ACTION in
    start)
        start_server
        ;;
    stop)
        stop_server
        ;;
    restart)
        restart_server
        ;;
    status)
        show_status
        ;;
    *)
        echo -e "${RED}${CROSS_MARK} Invalid action: $ACTION${NC}"
        exit 1
        ;;
esac