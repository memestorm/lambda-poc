#!/bin/bash

# Lambda Consensus Federation CLI Script
# Provides easy access to CLI testing utilities

set -e

# Default configuration
DEFAULT_SQS_ENDPOINT="http://localhost:9324"
DEFAULT_NODES="node1,node2,node3,node4,node5"
DEFAULT_JAR_PATH="target/lambda-consensus-federation-1.0.0.jar"

# Parse command line arguments
SQS_ENDPOINT="${SQS_ENDPOINT:-$DEFAULT_SQS_ENDPOINT}"
NODES="${NODES:-$DEFAULT_NODES}"
JAR_PATH="${JAR_PATH:-$DEFAULT_JAR_PATH}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
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

# Function to check if JAR exists
check_jar() {
    if [ ! -f "$JAR_PATH" ]; then
        print_error "JAR file not found: $JAR_PATH"
        print_info "Please build the project first: mvn clean package"
        exit 1
    fi
}

# Function to check if SQS is available
check_sqs() {
    print_info "Checking SQS availability at $SQS_ENDPOINT..."
    
    if curl -s "$SQS_ENDPOINT" > /dev/null 2>&1; then
        print_success "SQS is available"
    else
        print_warning "SQS may not be available at $SQS_ENDPOINT"
        print_info "Make sure Docker Compose is running: docker-compose up -d"
    fi
}

# Function to run CLI command
run_cli() {
    check_jar
    
    print_info "Running CLI command: $*"
    print_info "SQS Endpoint: $SQS_ENDPOINT"
    print_info "Available Nodes: $NODES"
    
    java -Dsqs.endpoint="$SQS_ENDPOINT" \
         -Dnodes="$NODES" \
         -cp "$JAR_PATH" \
         com.example.consensus.cli.ConsensusCLI "$@"
}

# Function to show usage
show_usage() {
    echo "Lambda Consensus Federation CLI"
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  send <nodeId> [count]              Send increment request(s) to specific node"
    echo "  broadcast [count]                  Send increment request(s) to all nodes"
    echo "  batch <requests> <concurrency> [target]  Run batch test"
    echo "  load-test <duration> <rps> <concurrency>  Run load test"
    echo "  monitor [duration]                 Monitor consensus operations"
    echo "  status                            Check system status"
    echo "  help                              Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  SQS_ENDPOINT                      SQS endpoint URL (default: $DEFAULT_SQS_ENDPOINT)"
    echo "  NODES                             Comma-separated list of nodes (default: $DEFAULT_NODES)"
    echo "  JAR_PATH                          Path to JAR file (default: $DEFAULT_JAR_PATH)"
    echo ""
    echo "Examples:"
    echo "  $0 send node1                     Send one increment to node1"
    echo "  $0 broadcast 5                    Send 5 increments to each node"
    echo "  $0 batch 100 10 random            Run batch test with 100 requests, 10 concurrent"
    echo "  $0 load-test 60 5 10               Load test: 5 req/s for 60s with 10 concurrent"
    echo "  $0 monitor 120                     Monitor for 2 minutes"
    echo "  $0 status                          Check if system is ready"
}

# Function to check system status
check_status() {
    print_info "Checking Lambda Consensus Federation status..."
    
    check_sqs
    
    # Check if Docker containers are running
    if command -v docker-compose > /dev/null 2>&1; then
        print_info "Checking Docker containers..."
        
        if docker-compose ps | grep -q "Up"; then
            print_success "Docker containers are running"
            docker-compose ps
        else
            print_warning "Docker containers may not be running"
            print_info "Start with: docker-compose up -d"
        fi
    else
        print_warning "docker-compose not found, cannot check container status"
    fi
    
    # Try to send a test message
    print_info "Testing message sending capability..."
    if run_cli send node1 1 > /dev/null 2>&1; then
        print_success "Message sending test passed"
    else
        print_warning "Message sending test failed - check logs"
    fi
}

# Function to run interactive mode
interactive_mode() {
    echo "=== Lambda Consensus Federation CLI - Interactive Mode ==="
    echo "Type 'help' for available commands, 'quit' to exit"
    echo ""
    
    while true; do
        echo -n "consensus-cli> "
        read -r input
        
        if [ -z "$input" ]; then
            continue
        fi
        
        case "$input" in
            "quit"|"exit"|"q")
                print_info "Goodbye!"
                break
                ;;
            "help"|"h")
                show_usage
                ;;
            "status")
                check_status
                ;;
            *)
                # Split input into array and run CLI
                IFS=' ' read -ra ADDR <<< "$input"
                run_cli "${ADDR[@]}"
                ;;
        esac
        echo ""
    done
}

# Main script logic
if [ $# -eq 0 ]; then
    show_usage
    exit 1
fi

case "$1" in
    "help"|"-h"|"--help")
        show_usage
        ;;
    "status")
        check_status
        ;;
    "interactive"|"-i")
        interactive_mode
        ;;
    *)
        run_cli "$@"
        ;;
esac