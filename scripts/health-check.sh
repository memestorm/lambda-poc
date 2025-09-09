#!/bin/bash

# Lambda Consensus Federation Health Check Script
# This script performs comprehensive health checks on the consensus system

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
SQS_ENDPOINT="http://localhost:9324"
TIMEOUT=10
VERBOSE=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -t|--timeout)
            TIMEOUT="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 [-v|--verbose] [-t|--timeout SECONDS] [-h|--help]"
            echo "  -v, --verbose    Enable verbose output"
            echo "  -t, --timeout    Set timeout in seconds (default: 10)"
            echo "  -h, --help       Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option $1"
            exit 1
            ;;
    esac
done

# Logging functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "[DEBUG] $1"
    fi
}

# Health check functions
check_docker() {
    log_verbose "Checking Docker daemon..."
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker daemon is not running"
        return 1
    fi
    log_info "Docker daemon is running"
    return 0
}

check_containers() {
    log_verbose "Checking container status..."
    local containers_up=0
    local expected_containers=6  # 5 lambda nodes + 1 elasticmq
    
    # Check if docker-compose is available
    if ! command -v docker-compose >/dev/null 2>&1; then
        log_error "docker-compose not found"
        return 1
    fi
    
    # Get container status
    local status_output
    if ! status_output=$(docker-compose ps --format json 2>/dev/null); then
        log_error "Failed to get container status"
        return 1
    fi
    
    # Count running containers
    containers_up=$(echo "$status_output" | jq -r 'select(.State == "running")' | wc -l)
    
    if [ "$containers_up" -eq "$expected_containers" ]; then
        log_info "All $containers_up containers are running"
        return 0
    else
        log_warn "$containers_up/$expected_containers containers are running"
        
        if [ "$VERBOSE" = true ]; then
            echo "$status_output" | jq -r '.Name + ": " + .State'
        fi
        
        return 1
    fi
}

check_sqs() {
    log_verbose "Checking SQS service..."
    
    if ! curl -s --max-time "$TIMEOUT" "$SQS_ENDPOINT" >/dev/null; then
        log_error "SQS service is not responding at $SQS_ENDPOINT"
        return 1
    fi
    
    log_info "SQS service is responding"
    
    # Check queue creation
    local queue_count
    if queue_count=$(curl -s --max-time "$TIMEOUT" "$SQS_ENDPOINT" | grep -c "consensus-lambda-node" || true); then
        if [ "$queue_count" -gt 0 ]; then
            log_info "Found $queue_count consensus queues"
        else
            log_warn "No consensus queues found"
        fi
    fi
    
    return 0
}

check_node_connectivity() {
    log_verbose "Checking node connectivity..."
    local failed_nodes=0
    
    for i in {1..5}; do
        local node_name="lambda-node-$i"
        
        if docker-compose exec -T "$node_name" echo "ping" >/dev/null 2>&1; then
            log_verbose "Node $node_name is reachable"
        else
            log_warn "Node $node_name is not reachable"
            ((failed_nodes++))
        fi
    done
    
    if [ "$failed_nodes" -eq 0 ]; then
        log_info "All nodes are reachable"
        return 0
    else
        log_warn "$failed_nodes/5 nodes are not reachable"
        return 1
    fi
}

check_consensus_status() {
    log_verbose "Checking consensus status..."
    
    # Check if CLI script exists and is executable
    if [ ! -x "./scripts/consensus-cli.sh" ]; then
        log_warn "Consensus CLI script not found or not executable"
        return 1
    fi
    
    # Try to get status
    local status_output
    if status_output=$(timeout "$TIMEOUT" ./scripts/consensus-cli.sh status 2>/dev/null); then
        local idle_nodes
        idle_nodes=$(echo "$status_output" | grep -c "IDLE" || true)
        
        if [ "$idle_nodes" -ge 3 ]; then
            log_info "Consensus system is healthy ($idle_nodes nodes in IDLE state)"
            return 0
        else
            log_warn "Consensus system may have issues ($idle_nodes nodes in IDLE state)"
            return 1
        fi
    else
        log_warn "Unable to get consensus status"
        return 1
    fi
}

check_resource_usage() {
    log_verbose "Checking resource usage..."
    
    # Check memory usage
    local high_memory_containers=0
    
    while IFS= read -r line; do
        local container_name memory_usage memory_percent
        container_name=$(echo "$line" | awk '{print $1}')
        memory_usage=$(echo "$line" | awk '{print $3}')
        memory_percent=$(echo "$line" | awk '{print $4}' | sed 's/%//')
        
        if [ "${memory_percent%.*}" -gt 80 ]; then
            log_warn "High memory usage in $container_name: $memory_usage ($memory_percent%)"
            ((high_memory_containers++))
        fi
        
        log_verbose "$container_name: $memory_usage ($memory_percent%)"
        
    done < <(docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" | tail -n +2)
    
    if [ "$high_memory_containers" -eq 0 ]; then
        log_info "Resource usage is within normal limits"
        return 0
    else
        log_warn "$high_memory_containers containers have high memory usage"
        return 1
    fi
}

check_recent_errors() {
    log_verbose "Checking for recent errors..."
    
    local error_count
    error_count=$(docker-compose logs --tail=100 2>/dev/null | grep -i "error\|exception\|failed" | wc -l)
    
    if [ "$error_count" -eq 0 ]; then
        log_info "No recent errors found"
        return 0
    else
        log_warn "Found $error_count recent error messages"
        
        if [ "$VERBOSE" = true ]; then
            echo "Recent errors:"
            docker-compose logs --tail=20 2>/dev/null | grep -i "error\|exception\|failed" | head -5
        fi
        
        return 1
    fi
}

# Main health check execution
main() {
    echo "=== Lambda Consensus Federation Health Check ==="
    echo "Started at: $(date)"
    echo
    
    local total_checks=0
    local passed_checks=0
    local failed_checks=0
    
    # Define checks to run
    local checks=(
        "check_docker:Docker Daemon"
        "check_containers:Container Status"
        "check_sqs:SQS Service"
        "check_node_connectivity:Node Connectivity"
        "check_consensus_status:Consensus Status"
        "check_resource_usage:Resource Usage"
        "check_recent_errors:Recent Errors"
    )
    
    # Run all checks
    for check in "${checks[@]}"; do
        local check_func="${check%:*}"
        local check_name="${check#*:}"
        
        echo "Checking: $check_name"
        ((total_checks++))
        
        if $check_func; then
            ((passed_checks++))
        else
            ((failed_checks++))
        fi
        
        echo
    done
    
    # Summary
    echo "=== Health Check Summary ==="
    echo "Total checks: $total_checks"
    echo "Passed: $passed_checks"
    echo "Failed: $failed_checks"
    echo "Completed at: $(date)"
    
    # Exit with appropriate code
    if [ "$failed_checks" -eq 0 ]; then
        log_info "All health checks passed! System is healthy."
        exit 0
    else
        log_error "$failed_checks/$total_checks health checks failed."
        exit 1
    fi
}

# Run main function
main "$@"