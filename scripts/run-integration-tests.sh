#!/bin/bash

# Integration Test Runner for Lambda Consensus Federation
# Optimized for Mac Silicon environments

set -e

echo "=== Lambda Consensus Federation Integration Tests ==="
echo "Optimized for Mac Silicon (ARM64)"
echo

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop."
    exit 1
fi

echo "✅ Docker is running"

# Build the project first
echo "🔨 Building project..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✅ Build successful"

# Function to run a specific test class
run_test_class() {
    local test_class=$1
    local description=$2
    
    echo
    echo "🧪 Running $description..."
    echo "   Test class: $test_class"
    
    mvn test -Dtest="$test_class" -q
    
    if [ $? -eq 0 ]; then
        echo "✅ $description passed"
    else
        echo "❌ $description failed"
        return 1
    fi
}

# Parse command line arguments
TEST_TYPE=${1:-"all"}

case $TEST_TYPE in
    "local")
        echo "Running local simulation tests (fastest, no Docker containers)..."
        run_test_class "LocalConsensusSimulationTest" "Local Consensus Simulation"
        ;;
    
    "optimized")
        echo "Running Mac Silicon optimized tests (3 nodes, extended timeouts)..."
        run_test_class "MacSiliconOptimizedIntegrationTest" "Mac Silicon Optimized Integration Tests"
        ;;
    
    "performance")
        echo "Running performance tests (timing and latency measurements)..."
        run_test_class "ConsensusPerformanceIntegrationTest" "Consensus Performance Tests"
        ;;
    
    "full")
        echo "Running full integration tests (all 5 nodes, comprehensive scenarios)..."
        run_test_class "MultiNodeConsensusIntegrationTest" "Multi-Node Consensus Integration Tests"
        ;;
    
    "e2e")
        echo "Running end-to-end system tests (comprehensive scenarios, rolling restarts, chaos)..."
        run_test_class "EndToEndSystemTest" "End-to-End System Tests"
        ;;
    
    "chaos")
        echo "Running advanced chaos testing (random failures, partitions, cascades)..."
        run_test_class "AdvancedChaosTest" "Advanced Chaos Testing"
        ;;
    
    "convergence")
        echo "Running convergence validation tests (consistency verification)..."
        run_test_class "SystemConvergenceValidationTest" "System Convergence Validation Tests"
        ;;
    
    "all")
        echo "Running all integration tests..."
        
        echo
        echo "Phase 1: Local simulation tests (fastest)"
        run_test_class "LocalConsensusSimulationTest" "Local Consensus Simulation"
        
        echo
        echo "Phase 2: Mac Silicon optimized tests"
        run_test_class "MacSiliconOptimizedIntegrationTest" "Mac Silicon Optimized Integration Tests"
        
        echo
        echo "Phase 3: Performance tests"
        run_test_class "ConsensusPerformanceIntegrationTest" "Consensus Performance Tests"
        
        echo
        echo "Phase 4: Full integration tests"
        run_test_class "MultiNodeConsensusIntegrationTest" "Multi-Node Consensus Integration Tests"
        
        echo
        echo "Phase 5: End-to-end system tests"
        run_test_class "EndToEndSystemTest" "End-to-End System Tests"
        
        echo
        echo "Phase 6: Convergence validation tests"
        run_test_class "SystemConvergenceValidationTest" "System Convergence Validation Tests"
        
        echo
        echo "Phase 7: Advanced chaos testing"
        run_test_class "AdvancedChaosTest" "Advanced Chaos Testing"
        ;;
    
    *)
        echo "Usage: $0 [local|optimized|performance|full|e2e|chaos|convergence|all]"
        echo
        echo "Test types:"
        echo "  local      - Fast local simulation tests (no Docker)"
        echo "  optimized  - Mac Silicon optimized tests (3 nodes)"
        echo "  performance- Performance and timing tests"
        echo "  full       - Complete integration tests (5 nodes)"
        echo "  e2e        - End-to-end system tests (comprehensive scenarios)"
        echo "  chaos      - Advanced chaos testing (failures, partitions)"
        echo "  convergence- Convergence validation tests (consistency)"
        echo "  all        - Run all test types (default)"
        echo
        exit 1
        ;;
esac

echo
echo "🎉 Integration tests completed successfully!"
echo
echo "Tips for Mac Silicon:"
echo "  - Use 'local' tests for fastest development cycles"
echo "  - Use 'optimized' tests for most integration scenarios"
echo "  - Use 'performance' tests to measure consensus timing"
echo "  - Use 'full' tests for comprehensive validation"
echo
echo "To view logs during tests:"
echo "  docker-compose logs -f"
echo
echo "To clean up Docker resources:"
echo "  docker-compose down -v"
echo "  docker system prune -f"