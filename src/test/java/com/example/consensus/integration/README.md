# Multi-Node Consensus Integration Tests

This directory contains comprehensive integration tests for the Lambda Consensus Federation system, specifically optimized for Mac Silicon (ARM64) environments.

## Test Classes Overview

### 1. LocalConsensusSimulationTest
- **Purpose**: Fast local simulation without Docker containers
- **Runtime**: ~30 seconds
- **Use Case**: Development cycles, unit-style integration testing
- **Features**:
  - In-memory message bus simulation
  - Simulated node failures and recovery
  - Concurrent request testing
  - Quorum behavior simulation

### 2. MacSiliconOptimizedIntegrationTest
- **Purpose**: Docker-based testing optimized for Mac Silicon
- **Runtime**: ~5-10 minutes
- **Use Case**: Integration testing with realistic container environment
- **Features**:
  - 3-node consensus testing (reduced load)
  - Extended timeouts for ARM64 performance
  - Node failure simulation
  - Sequential operations testing

### 3. ConsensusPerformanceIntegrationTest
- **Purpose**: Performance measurement and timing analysis
- **Runtime**: ~8-15 minutes
- **Use Case**: Performance validation and optimization
- **Features**:
  - Consensus latency measurement
  - Recovery performance testing
  - System stability over time
  - Performance metrics logging

### 4. MultiNodeConsensusIntegrationTest
- **Purpose**: Comprehensive 5-node integration testing
- **Runtime**: ~15-25 minutes
- **Use Case**: Full system validation
- **Features**:
  - All 5 nodes consensus testing
  - Concurrent increment requests
  - Network partition simulation
  - Complete failure/recovery scenarios

### 5. EndToEndSystemTest
- **Purpose**: Comprehensive end-to-end system validation
- **Runtime**: ~20-35 minutes
- **Use Case**: Complete system testing with all scenarios
- **Features**:
  - Comprehensive consensus scenario validation
  - Automated rolling restart testing
  - Performance metrics measurement
  - Chaos testing with random node failures
  - Convergence validation ensuring all nodes reach same count

### 6. AdvancedChaosTest
- **Purpose**: Advanced chaos engineering scenarios
- **Runtime**: ~15-25 minutes
- **Use Case**: System resilience validation under extreme conditions
- **Features**:
  - Random node failure simulation
  - Network partition scenarios (majority/minority splits)
  - Cascading failure simulation
  - Combined chaos scenarios

### 7. SystemConvergenceValidationTest
- **Purpose**: Detailed convergence property validation
- **Runtime**: ~25-40 minutes
- **Use Case**: Ensuring system consistency under all conditions
- **Features**:
  - Basic convergence validation with single operations
  - Concurrent operations convergence testing
  - Recovery scenario convergence validation
  - Long-term stability and convergence maintenance

## Running the Tests

### Quick Start
```bash
# Run all tests
./scripts/run-integration-tests.sh

# Run specific test type
./scripts/run-integration-tests.sh local      # Fastest
./scripts/run-integration-tests.sh optimized # Mac Silicon optimized
./scripts/run-integration-tests.sh performance # Performance tests
./scripts/run-integration-tests.sh full      # Complete integration
./scripts/run-integration-tests.sh e2e       # End-to-end system tests
./scripts/run-integration-tests.sh chaos     # Advanced chaos testing
./scripts/run-integration-tests.sh convergence # Convergence validation
```

### Manual Execution
```bash
# Build project
mvn clean package -DskipTests

# Run specific test class
mvn test -Dtest="LocalConsensusSimulationTest"
mvn test -Dtest="MacSiliconOptimizedIntegrationTest"
mvn test -Dtest="ConsensusPerformanceIntegrationTest"
mvn test -Dtest="MultiNodeConsensusIntegrationTest"
mvn test -Dtest="EndToEndSystemTest"
mvn test -Dtest="AdvancedChaosTest"
mvn test -Dtest="SystemConvergenceValidationTest"
```

## Mac Silicon Optimizations

### Performance Considerations
- **Extended Timeouts**: All Docker-based tests use extended timeouts (5-6 minutes startup)
- **Reduced Concurrency**: Concurrent tests use fewer threads to reduce ARM64 load
- **Longer Wait Times**: Consensus operations allow 15-30 seconds for completion
- **Subset Testing**: Some tests use 3 nodes instead of 5 for efficiency

### Resource Management
- **Memory**: Each Lambda container uses ~512MB, total ~2.5GB for full tests
- **CPU**: Tests are designed to work with ARM64 performance characteristics
- **Network**: Local Docker networking optimized for container communication

## Test Scenarios Covered

### Consensus Operations
- ✅ Basic increment consensus with all nodes
- ✅ Concurrent increment requests and conflict resolution
- ✅ Sequential operations maintaining consistency
- ✅ Consensus timing and latency measurement

### Failure Scenarios
- ✅ Single node failure and recovery
- ✅ Multiple node failures (minority/majority partitions)
- ✅ Network partition simulation
- ✅ Quorum behavior validation

### Recovery Mechanisms
- ✅ Node restart and state synchronization
- ✅ Recovery request/response handling
- ✅ Quorum validation during recovery
- ✅ State consistency after recovery

### Performance Testing
- ✅ Consensus latency measurement
- ✅ Recovery time measurement
- ✅ System stability over time
- ✅ Resource usage patterns

### End-to-End System Testing
- ✅ Comprehensive consensus scenario validation
- ✅ Automated rolling restart testing
- ✅ Performance metrics measurement (latency and throughput)
- ✅ Chaos testing with random node failures
- ✅ Convergence validation ensuring all nodes reach same count

### Advanced Chaos Testing
- ✅ Random node failure simulation with configurable parameters
- ✅ Network partition scenarios (majority/minority, even splits)
- ✅ Cascading failure simulation
- ✅ Combined chaos scenarios testing system resilience

### Convergence Validation Testing
- ✅ Basic convergence validation with single operations
- ✅ Concurrent operations convergence testing
- ✅ Recovery scenario convergence validation
- ✅ Long-term stability and convergence maintenance
- ✅ Detailed convergence metrics and timing analysis

## Expected Results

### Local Simulation Tests
- **Runtime**: 20-40 seconds
- **Success Rate**: 100% (no external dependencies)
- **Coverage**: Core consensus logic validation

### Mac Silicon Optimized Tests
- **Runtime**: 5-10 minutes
- **Success Rate**: 95%+ (depends on Docker performance)
- **Coverage**: Real container integration with optimized timing

### Performance Tests
- **Consensus Latency**: 15-45 seconds per operation on Mac Silicon
- **Recovery Time**: 20-60 seconds depending on scenario
- **Success Rate**: 90%+ (performance dependent)

### Full Integration Tests
- **Runtime**: 15-25 minutes
- **Success Rate**: 85%+ (comprehensive scenarios)
- **Coverage**: Complete system validation

### End-to-End System Tests
- **Runtime**: 20-35 minutes
- **Success Rate**: 90%+ (comprehensive validation)
- **Coverage**: All consensus scenarios, rolling restarts, performance, chaos, convergence

### Advanced Chaos Tests
- **Runtime**: 15-25 minutes
- **Success Rate**: 85%+ (resilience dependent)
- **Coverage**: Random failures, network partitions, cascading failures

### Convergence Validation Tests
- **Runtime**: 25-40 minutes
- **Success Rate**: 95%+ (consistency focused)
- **Coverage**: Detailed convergence properties and long-term stability

## Troubleshooting

### Common Issues on Mac Silicon

#### Slow Container Startup
```bash
# Increase Docker Desktop resources:
# - Memory: 8GB+
# - CPU: 4+ cores
# - Disk: 64GB+
```

#### Test Timeouts
```bash
# Tests are already optimized for Mac Silicon
# If timeouts occur, check Docker Desktop performance
docker stats  # Monitor resource usage
```

#### Port Conflicts
```bash
# Check for port conflicts
netstat -an | grep 9001  # Lambda ports 9001-9005
netstat -an | grep 9324  # SQS port

# Stop conflicting services
docker-compose down
```

#### Memory Issues
```bash
# Clean up Docker resources
docker system prune -f
docker volume prune -f

# Restart Docker Desktop if needed
```

### Debug Mode
```bash
# Run with debug logging
mvn test -Dtest="MacSiliconOptimizedIntegrationTest" -X

# View container logs during tests
docker-compose logs -f lambda-1
```

## Development Workflow

### Fast Development Cycle
1. Make code changes
2. Run `./scripts/run-integration-tests.sh local` (fastest)
3. If local tests pass, run `./scripts/run-integration-tests.sh optimized`
4. For final validation, run full integration tests

### Performance Optimization
1. Run performance tests to establish baseline
2. Make optimizations
3. Re-run performance tests to measure improvement
4. Validate with full integration tests

### CI/CD Integration
```bash
# For CI environments, use local simulation tests
mvn test -Dtest="LocalConsensusSimulationTest"

# For staging environments, use optimized tests
mvn test -Dtest="MacSiliconOptimizedIntegrationTest"
```

## Requirements Coverage

The integration tests cover the following requirements from the specification:

### Core Requirements (All Test Classes)
- **Requirement 3.2**: All Lambdas maintain consensus on global count value
- **Requirement 3.3**: Consensus achievement and consistency validation
- **Requirement 4.4**: Timeout handling and consensus failure scenarios
- **Requirement 5.4**: Message exchange and commit operations
- **Requirement 6.2**: Node recovery and state synchronization
- **Requirement 6.3**: Recovery retry logic and failure handling

### Extended Requirements (End-to-End and Advanced Tests)
- **Requirement 7.1**: Quorum validation (minimum 3 out of 5 nodes)
- **Requirement 7.2**: Unanimous agreement with exactly 3 nodes
- **Requirement 7.3**: Simple majority agreement with 4+ nodes

### Comprehensive Coverage (New Test Classes)
- **EndToEndSystemTest**: Covers all requirements with comprehensive scenarios
- **AdvancedChaosTest**: Validates system resilience under extreme conditions
- **SystemConvergenceValidationTest**: Ensures convergence properties are maintained
- **ChaosTestingUtility**: Provides advanced failure simulation capabilities

## Contributing

When adding new integration tests:

1. Consider Mac Silicon performance characteristics
2. Use appropriate timeouts (15-30 seconds for consensus operations)
3. Include both positive and negative test cases
4. Add performance measurements where relevant
5. Document expected behavior and timing
6. Test with both local simulation and Docker containers