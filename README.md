# Lambda Consensus Federation

A distributed consensus system that simulates AWS Lambda functions running locally using Docker containers with AWS Runtime Interface Emulator (RIE). The system implements a simplified Raft-like consensus algorithm for maintaining a consistent global count across five Java-based Lambda functions that communicate exclusively through SQS queues.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Usage](#usage)
- [Monitoring](#monitoring)
- [Testing](#testing)
- [Troubleshooting](#troubleshooting)
- [Performance](#performance)
- [Development](#development)

## Overview

The Lambda Consensus Federation demonstrates:

- **Distributed Consensus**: Five Lambda functions maintain consensus on a global count value
- **Fault Tolerance**: Automatic recovery when individual Lambda containers are restarted
- **Message-Based Communication**: All communication happens through local SQS queues
- **Quorum-Based Recovery**: Minimum 3 out of 5 nodes required for recovery operations
- **Performance Optimization**: Connection pooling, retry logic, and graceful shutdown handling

### Key Features

- ✅ **5-Node Consensus**: Distributed agreement across multiple Lambda instances
- ✅ **SQS Communication**: Local SQS queues for inter-Lambda messaging
- ✅ **Automatic Recovery**: Failed nodes recover state when restarted
- ✅ **Comprehensive Logging**: Structured JSON logging for all operations
- ✅ **Performance Monitoring**: Built-in metrics and performance tracking
- ✅ **CLI Tools**: Command-line utilities for testing and monitoring
- ✅ **Docker Integration**: Complete containerized environment

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Docker Environment                           │
├─────────────┬─────────────┬─────────────┬─────────────┬─────────┤
│  Lambda 1   │  Lambda 2   │  Lambda 3   │  Lambda 4   │Lambda 5 │
│ Java + RIE  │ Java + RIE  │ Java + RIE  │ Java + RIE  │Java+RIE │
└─────────────┴─────────────┴─────────────┴─────────────┴─────────┘
       │             │             │             │             │
       └─────────────┼─────────────┼─────────────┼─────────────┘
                     │             │             │
┌─────────────────────────────────────────────────────────────────┐
│                     Local SQS (ElasticMQ)                      │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │Queue-1  │ │Queue-2  │ │Queue-3  │ │Queue-4  │ │Queue-5  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Consensus Protocol

1. **Increment Request**: External trigger initiates count increment
2. **Proposal Phase**: Receiving Lambda proposes new count to all peers
3. **Voting Phase**: Each Lambda validates and votes on the proposal
4. **Commit Phase**: If majority accepts, all Lambdas update their count
5. **Recovery Phase**: Restarted Lambdas request current state from peers

## Prerequisites

- **Docker**: Version 20.0 or higher
- **Docker Compose**: Version 2.0 or higher
- **Java**: JDK 21 (for local development)
- **Maven**: Version 3.8 or higher (for building)

### System Requirements

- **Memory**: Minimum 4GB RAM (8GB recommended)
- **CPU**: 2+ cores recommended
- **Disk**: 2GB free space
- **Network**: Docker networking enabled

## Quick Start

### 1. Clone and Build

```bash
# Clone the repository
git clone <repository-url>
cd lambda-consensus-federation

# Build the project
mvn clean package

# Verify the build
ls -la target/lambda-consensus-federation-1.0.0.jar
```

### 2. Start the Federation

```bash
# Start all services
docker-compose up -d

# Verify all containers are running
docker-compose ps

# Check logs
docker-compose logs -f
```

### 3. Test Consensus

```bash
# Send increment request to any Lambda
./scripts/consensus-cli.sh increment lambda-node-1

# Check current count across all nodes
./scripts/consensus-cli.sh status

# Monitor logs in real-time
./scripts/view-logs.sh
```

### 4. Test Recovery

```bash
# Restart a single node
./scripts/restart-node.sh lambda-node-3

# Verify recovery
./scripts/consensus-cli.sh status
```

## Configuration

### Environment Variables

Each Lambda container supports the following environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `NODE_ID` | Unique identifier for the Lambda instance | `lambda-node-{timestamp}` |
| `SQS_ENDPOINT` | Local SQS endpoint URL | `http://localhost:9324` |
| `KNOWN_NODES` | Comma-separated list of peer node IDs | `lambda-node-1,lambda-node-2,...` |
| `LOG_LEVEL` | Logging verbosity level | `INFO` |

### Docker Compose Configuration

The `docker-compose.yml` file defines:

- **5 Lambda containers**: Each with unique NODE_ID
- **ElasticMQ service**: Local SQS implementation
- **Trigger service**: Sends random increment requests
- **Shared network**: For inter-container communication
- **Volume mounts**: For log aggregation

### SQS Configuration

ElasticMQ configuration in `elasticmq.conf`:

```hocon
include classpath("application.conf")

node-address {
    protocol = http
    host = "*"
    port = 9324
    context-path = ""
}

rest-sqs {
    enabled = true
    bind-port = 9324
    bind-hostname = "0.0.0.0"
    sqs-limits = strict
}
```

## Usage

### CLI Commands

The system includes several CLI utilities:

#### Consensus CLI

```bash
# Send increment request
./scripts/consensus-cli.sh increment <node-id>

# Check status of all nodes
./scripts/consensus-cli.sh status

# Send batch requests for load testing
./scripts/consensus-cli.sh batch 10

# Monitor specific node
./scripts/consensus-cli.sh monitor lambda-node-1
```

#### System Management

```bash
# Start the consensus system
./scripts/start-consensus.sh

# Stop the consensus system
./scripts/stop-consensus.sh

# Restart specific node
./scripts/restart-node.sh <node-id>

# View aggregated logs
./scripts/view-logs.sh

# Run integration tests
./scripts/run-integration-tests.sh
```

### Programmatic Usage

#### Java API

```java
// Create consensus request
ConsensusRequest request = new ConsensusRequest(
    MessageType.INCREMENT_REQUEST,
    "source-node",
    "target-node",
    42L,
    "proposal-123",
    Map.of("metadata", "value")
);

// Send via SQS handler
SQSMessageHandler handler = new SQSMessageHandlerImpl("node-1");
boolean success = handler.sendMessage("lambda-node-2", request);
```

#### REST API (via CLI)

```bash
# Increment count
curl -X POST http://localhost:8080/increment \
  -H "Content-Type: application/json" \
  -d '{"targetNode": "lambda-node-1"}'

# Get status
curl http://localhost:8080/status
```

## Monitoring

### Structured Logging

All operations are logged in structured JSON format:

```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "ConsensusLambdaHandler",
  "nodeId": "lambda-node-1",
  "operation": "CONSENSUS_OPERATION",
  "proposalId": "prop-123",
  "proposedValue": 42,
  "phase": "commit",
  "metadata": {
    "success": true,
    "duration": 1250
  }
}
```

### Performance Metrics

Built-in performance tracking includes:

- **Consensus Duration**: Time to complete consensus operations
- **Message Counts**: Sent/received message statistics
- **Success Rates**: Operation success/failure ratios
- **Recovery Times**: Node recovery duration metrics

### Log Analysis

Use the built-in log analyzer:

```bash
# Analyze consensus performance
java -cp target/lambda-consensus-federation-1.0.0.jar \
  com.example.consensus.logging.LogAnalyzerCLI \
  --analyze-consensus logs/

# Generate performance report
java -cp target/lambda-consensus-federation-1.0.0.jar \
  com.example.consensus.logging.LogAnalyzerCLI \
  --performance-report logs/
```

### Real-time Monitoring

```bash
# Monitor all nodes
./scripts/view-logs.sh | grep "CONSENSUS_OPERATION"

# Monitor specific operations
docker-compose logs -f lambda-node-1 | jq '.operation'

# Watch consensus success rate
watch -n 5 './scripts/consensus-cli.sh status'
```

## Testing

### Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=ConsensusLambdaHandlerTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Run integration tests
mvn test -Dtest="*IntegrationTest"

# Run specific integration test
mvn test -Dtest=MultiNodeConsensusIntegrationTest

# Run with Docker containers
./scripts/run-integration-tests.sh
```

### End-to-End Tests

```bash
# Full system test
mvn test -Dtest=EndToEndSystemTest

# Chaos testing
mvn test -Dtest=AdvancedChaosTest

# Performance testing
mvn test -Dtest=ConsensusPerformanceIntegrationTest
```

### Load Testing

```bash
# Generate load with CLI
./scripts/consensus-cli.sh batch 100

# Concurrent load testing
for i in {1..5}; do
  ./scripts/consensus-cli.sh batch 20 &
done
wait
```

## Troubleshooting

### Common Issues

#### 1. Containers Not Starting

**Symptoms**: Docker containers exit immediately or fail to start

**Solutions**:
```bash
# Check Docker daemon
docker info

# Verify image build
docker-compose build --no-cache

# Check resource limits
docker system df
docker system prune
```

#### 2. SQS Connection Issues

**Symptoms**: "Connection refused" or SQS timeout errors

**Solutions**:
```bash
# Verify ElasticMQ is running
docker-compose ps elasticmq

# Check SQS endpoint
curl http://localhost:9324/

# Restart SQS service
docker-compose restart elasticmq
```

#### 3. Consensus Failures

**Symptoms**: Nodes don't reach consensus or have different count values

**Solutions**:
```bash
# Check node status
./scripts/consensus-cli.sh status

# Verify all nodes are reachable
for i in {1..5}; do
  docker-compose exec lambda-node-$i echo "Node $i OK"
done

# Check for network partitions
docker network ls
docker network inspect lambda-consensus-federation_consensus-network
```

#### 4. Recovery Issues

**Symptoms**: Restarted nodes don't recover or get stuck in recovery state

**Solutions**:
```bash
# Check quorum availability
./scripts/consensus-cli.sh status | grep -c "IDLE"

# Manually trigger recovery
./scripts/restart-node.sh lambda-node-1

# Verify recovery logs
docker-compose logs lambda-node-1 | grep "RECOVERY"
```

### Debug Mode

Enable debug logging:

```bash
# Set debug level
export LOG_LEVEL=DEBUG

# Restart with debug logging
docker-compose down
docker-compose up -d

# View debug logs
docker-compose logs -f | grep "DEBUG"
```

### Performance Issues

#### High Latency

```bash
# Check system resources
docker stats

# Monitor message queue lengths
curl http://localhost:9324/ | grep "ApproximateNumberOfMessages"

# Analyze consensus timing
grep "consensus_duration" logs/*.log | sort -n
```

#### Memory Issues

```bash
# Check memory usage
docker stats --format "table {{.Container}}\t{{.MemUsage}}\t{{.MemPerc}}"

# Increase container memory limits
# Edit docker-compose.yml:
# mem_limit: 1g
```

### Log Analysis

```bash
# Find consensus failures
grep "consensus.*failed" logs/*.log

# Analyze recovery patterns
grep "RECOVERY" logs/*.log | cut -d' ' -f1-3 | sort | uniq -c

# Check message flow
grep "MESSAGE.*SENT\|MESSAGE.*RECEIVED" logs/*.log | head -20
```

## Performance

### Benchmarks

Typical performance characteristics:

| Metric | Value | Notes |
|--------|-------|-------|
| Consensus Latency | < 5 seconds | 95th percentile |
| Throughput | 1 op/10 seconds | Sequential operations |
| Recovery Time | < 30 seconds | With 3+ nodes available |
| Memory Usage | ~512MB/node | Including JVM overhead |
| CPU Usage | < 10% idle | Burst during consensus |

### Optimization Tips

#### 1. SQS Configuration

```bash
# Increase message batch size
export SQS_MAX_MESSAGES=10

# Reduce polling wait time for faster response
export SQS_WAIT_TIME=5
```

#### 2. JVM Tuning

```dockerfile
# In Dockerfile, add JVM options:
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

#### 3. Connection Pooling

The system automatically uses optimized connection pooling:

- **SQS Client**: Reused connections with timeout management
- **Thread Pool**: Configurable pool size for message processing
- **Queue Caching**: URL caching to reduce SQS API calls

### Scaling Considerations

#### Horizontal Scaling

The current implementation supports exactly 5 nodes. To scale:

1. Update `KNOWN_NODES` environment variable
2. Modify quorum calculations in `ConsensusManagerImpl`
3. Add additional containers to `docker-compose.yml`

#### Vertical Scaling

```yaml
# In docker-compose.yml
services:
  lambda-node-1:
    mem_limit: 1g
    cpus: '0.5'
```

## Development

### Building from Source

```bash
# Full build with tests
mvn clean package

# Skip tests for faster build
mvn clean package -DskipTests

# Build Docker images
docker-compose build
```

### Code Structure

```
src/
├── main/java/com/example/consensus/
│   ├── handler/           # Lambda function handlers
│   ├── manager/           # Consensus algorithm implementation
│   ├── messaging/         # SQS message handling
│   ├── model/             # Data models and serialization
│   ├── state/             # Node state management
│   ├── logging/           # Structured logging and metrics
│   ├── cli/               # Command-line utilities
│   └── trigger/           # Trigger service for testing
└── test/java/com/example/consensus/
    ├── integration/       # Integration tests
    ├── handler/           # Handler unit tests
    ├── manager/           # Manager unit tests
    └── ...               # Other test packages
```

### Contributing

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/amazing-feature`
3. **Commit** changes: `git commit -m 'Add amazing feature'`
4. **Push** to branch: `git push origin feature/amazing-feature`
5. **Open** a Pull Request

### Code Style

- **Java**: Follow Google Java Style Guide
- **Logging**: Use structured logging with appropriate levels
- **Testing**: Maintain >80% code coverage
- **Documentation**: Update README for new features

### Release Process

```bash
# Update version
mvn versions:set -DnewVersion=1.1.0

# Build and test
mvn clean package

# Create release
git tag -a v1.1.0 -m "Release version 1.1.0"
git push origin v1.1.0
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For questions, issues, or contributions:

- **Issues**: [GitHub Issues](https://github.com/your-repo/lambda-consensus-federation/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-repo/lambda-consensus-federation/discussions)
- **Documentation**: [Wiki](https://github.com/your-repo/lambda-consensus-federation/wiki)

---

**Happy Consensus Building! 🚀**