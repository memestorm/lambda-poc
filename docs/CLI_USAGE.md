# Lambda Consensus Federation CLI Usage Guide

The CLI trigger utility provides comprehensive testing capabilities for the Lambda consensus federation system. This guide covers all available commands, usage scenarios, and best practices.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Installation](#installation)
3. [Basic Commands](#basic-commands)
4. [Batch Testing](#batch-testing)
5. [Load Testing](#load-testing)
6. [Monitoring](#monitoring)
7. [Configuration](#configuration)
8. [Testing Scenarios](#testing-scenarios)
9. [Troubleshooting](#troubleshooting)

## Quick Start

```bash
# Build the project
mvn clean package

# Start the consensus federation
docker-compose up -d

# Check system status
./scripts/consensus-cli.sh status

# Send a single increment request
./scripts/consensus-cli.sh send node1

# Monitor consensus operations
./scripts/consensus-cli.sh monitor 60
```

## Installation

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- Docker and Docker Compose
- Bash shell (for shell script wrapper)

### Build Steps

1. **Clone and build the project:**
   ```bash
   git clone <repository-url>
   cd lambda-consensus-federation
   mvn clean package
   ```

2. **Start the consensus federation:**
   ```bash
   docker-compose up -d
   ```

3. **Verify installation:**
   ```bash
   ./scripts/consensus-cli.sh status
   ```

## Basic Commands

### Send Command

Send increment requests to a specific Lambda node.

```bash
# Send one increment request to node1
./scripts/consensus-cli.sh send node1

# Send 5 increment requests to node3
./scripts/consensus-cli.sh send node3 5
```

**Parameters:**
- `nodeId`: Target node identifier (node1, node2, node3, node4, node5)
- `count`: Number of requests to send (optional, default: 1)

### Broadcast Command

Send increment requests to all available nodes.

```bash
# Send one increment request to each node
./scripts/consensus-cli.sh broadcast

# Send 3 increment requests to each node
./scripts/consensus-cli.sh broadcast 3
```

**Parameters:**
- `count`: Number of requests per node (optional, default: 1)

### Status Command

Check the health and availability of the consensus federation.

```bash
./scripts/consensus-cli.sh status
```

This command checks:
- SQS service availability
- Docker container status
- Message sending capability

## Batch Testing

Batch testing allows you to send multiple requests with controlled concurrency for performance testing.

### Basic Batch Test

```bash
# Send 100 requests with 10 concurrent threads
./scripts/consensus-cli.sh batch 100 10

# Send 50 requests with 5 concurrent threads to node2
./scripts/consensus-cli.sh batch 50 5 node2

# Send 200 requests with 20 concurrent threads to random nodes
./scripts/consensus-cli.sh batch 200 20 random
```

**Parameters:**
- `requests`: Total number of requests to send
- `concurrency`: Number of concurrent threads
- `target`: Target strategy (optional)
  - `random`: Send to random nodes (default)
  - `all`: Distribute across all nodes
  - `nodeX`: Send all requests to specific node

### Batch Test Results

The CLI provides detailed metrics after each batch test:

```
=== Batch Test Results ===
Total requests: 100
Successful: 98
Failed: 2
Success rate: 98.00%
Average duration: 45.23 ms
Min duration: 12 ms
Max duration: 234 ms
```

## Load Testing

Load testing simulates sustained traffic over a specified duration.

### Basic Load Test

```bash
# Run load test: 5 requests/second for 60 seconds with 10 concurrent threads
./scripts/consensus-cli.sh load-test 60 5 10

# High load test: 20 requests/second for 30 seconds with 50 concurrent threads
./scripts/consensus-cli.sh load-test 30 20 50
```

**Parameters:**
- `duration`: Test duration in seconds
- `requests-per-second`: Target request rate
- `concurrency`: Number of concurrent threads

### Load Test Metrics

Load tests provide comprehensive performance metrics:

```
=== Load Test Results ===
Duration: 60 seconds
Total requests: 300
Successful: 295
Failed: 5
Success rate: 98.33%
Average response time: 52.1 ms
Requests per second: 4.92
P50 response time: 45 ms
P95 response time: 89 ms
P99 response time: 156 ms
```

## Monitoring

The CLI provides real-time monitoring of consensus operations through log analysis.

### Basic Monitoring

```bash
# Monitor for 60 seconds (default)
./scripts/consensus-cli.sh monitor

# Monitor for 5 minutes
./scripts/consensus-cli.sh monitor 300
```

### Monitoring Output

```
=== Log Monitoring Summary ===
Timestamp: 2024-01-15T10:30:45Z

Node       Lines    Consensus    Increments  Errors   Last Activity
------------------------------------------------------------------------
node1      1245     23           45          2        2024-01-15T10:30:42Z
node2      1189     21           43          1        2024-01-15T10:30:44Z
node3      1203     22           44          0        2024-01-15T10:30:43Z
node4      1167     20           41          3        2024-01-15T10:30:41Z
node5      1198     22           42          1        2024-01-15T10:30:45Z
------------------------------------------------------------------------
TOTAL      -        108          215         7
```

## Configuration

### Environment Variables

Configure the CLI behavior using environment variables:

```bash
# Set custom SQS endpoint
export SQS_ENDPOINT="http://localhost:9325"

# Configure available nodes
export NODES="node1,node2,node3"

# Set custom JAR path
export JAR_PATH="target/my-consensus-app.jar"

# Run CLI with custom configuration
./scripts/consensus-cli.sh send node1
```

### System Properties

You can also use Java system properties:

```bash
java -Dsqs.endpoint=http://localhost:9324 \
     -Dnodes=node1,node2,node3,node4,node5 \
     -cp target/lambda-consensus-federation-1.0.0.jar \
     com.example.consensus.cli.ConsensusCLI send node1
```

## Testing Scenarios

### Scenario 1: Basic Functionality Test

Test basic consensus functionality with a single increment:

```bash
# 1. Check system status
./scripts/consensus-cli.sh status

# 2. Send single increment
./scripts/consensus-cli.sh send node1

# 3. Monitor for consensus completion
./scripts/consensus-cli.sh monitor 30
```

### Scenario 2: Node Failure Recovery Test

Test recovery behavior when nodes are restarted:

```bash
# 1. Start monitoring in background
./scripts/consensus-cli.sh monitor 300 &

# 2. Send increment requests
./scripts/consensus-cli.sh broadcast 3

# 3. Restart a node (in another terminal)
docker-compose restart lambda-node2

# 4. Send more requests to test recovery
./scripts/consensus-cli.sh send node2 5
```

### Scenario 3: Concurrent Load Test

Test system behavior under concurrent load:

```bash
# 1. Start monitoring
./scripts/consensus-cli.sh monitor 120 &

# 2. Run concurrent batch tests
./scripts/consensus-cli.sh batch 50 10 random &
./scripts/consensus-cli.sh batch 50 10 random &
./scripts/consensus-cli.sh batch 50 10 random &

# 3. Wait for completion and check results
wait
```

### Scenario 4: Sustained Load Test

Test system performance under sustained load:

```bash
# 1. Start monitoring
./scripts/consensus-cli.sh monitor 600 &

# 2. Run sustained load test
./scripts/consensus-cli.sh load-test 300 10 20

# 3. Analyze results
```

### Scenario 5: Network Partition Simulation

Test consensus behavior during network partitions:

```bash
# 1. Start monitoring
./scripts/consensus-cli.sh monitor 180 &

# 2. Send initial requests
./scripts/consensus-cli.sh broadcast 2

# 3. Simulate network partition (stop 2 nodes)
docker-compose stop lambda-node4 lambda-node5

# 4. Test with remaining nodes
./scripts/consensus-cli.sh batch 20 5 random

# 5. Restore network (restart nodes)
docker-compose start lambda-node4 lambda-node5

# 6. Test recovery
./scripts/consensus-cli.sh broadcast 3
```

## Interactive Mode

The CLI supports an interactive mode for exploratory testing:

```bash
./scripts/consensus-cli.sh interactive
```

Interactive commands:
- `send node1 5` - Send 5 increments to node1
- `broadcast 3` - Broadcast 3 increments to all nodes
- `batch 50 10` - Run batch test
- `status` - Check system status
- `help` - Show help
- `quit` - Exit interactive mode

## Troubleshooting

### Common Issues

#### 1. JAR File Not Found

```
[ERROR] JAR file not found: target/lambda-consensus-federation-1.0.0.jar
```

**Solution:** Build the project first:
```bash
mvn clean package
```

#### 2. SQS Not Available

```
[WARNING] SQS may not be available at http://localhost:9324
```

**Solution:** Start Docker Compose:
```bash
docker-compose up -d
```

#### 3. Message Sending Failures

```
Failed to send INCREMENT_REQUEST to node 'node1'
```

**Possible causes:**
- SQS service is down
- Node queues don't exist
- Network connectivity issues

**Solution:**
1. Check SQS status: `curl http://localhost:9324`
2. Restart Docker services: `docker-compose restart`
3. Check Docker logs: `docker-compose logs`

#### 4. No Consensus Activity

If monitoring shows no consensus operations:

1. **Check Lambda containers:**
   ```bash
   docker-compose ps
   docker-compose logs lambda-node1
   ```

2. **Verify queue creation:**
   ```bash
   curl http://localhost:9324/000000000000/lambda-node1-queue
   ```

3. **Test direct message sending:**
   ```bash
   ./scripts/consensus-cli.sh send node1 1
   ```

### Debug Mode

Enable debug logging for detailed troubleshooting:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG \
     -Dsqs.endpoint=http://localhost:9324 \
     -Dnodes=node1,node2,node3,node4,node5 \
     -cp target/lambda-consensus-federation-1.0.0.jar \
     com.example.consensus.cli.ConsensusCLI send node1
```

### Log Analysis

Check individual Lambda logs for detailed error information:

```bash
# View logs for specific node
docker-compose logs lambda-node1

# Follow logs in real-time
docker-compose logs -f lambda-node1

# View all Lambda logs
docker-compose logs lambda-node1 lambda-node2 lambda-node3 lambda-node4 lambda-node5
```

## Performance Tuning

### Optimal Concurrency Settings

- **Batch tests:** Start with concurrency = number of nodes (5)
- **Load tests:** Use concurrency = 2-3x target RPS
- **Monitor system resources** to avoid overwhelming the test environment

### Rate Limiting

For sustained testing, consider rate limiting to avoid overwhelming the system:

```bash
# Conservative load test
./scripts/consensus-cli.sh load-test 300 2 5

# Moderate load test  
./scripts/consensus-cli.sh load-test 180 5 10

# Aggressive load test (monitor system resources)
./scripts/consensus-cli.sh load-test 60 20 40
```

## Best Practices

1. **Always check system status** before running tests
2. **Start with small tests** and gradually increase load
3. **Monitor system resources** during load testing
4. **Use batch tests** for functional validation
5. **Use load tests** for performance validation
6. **Keep monitoring active** during all test scenarios
7. **Allow time for consensus completion** between test runs
8. **Check Docker logs** if tests fail unexpectedly

## Advanced Usage

### Custom Test Scripts

Create custom test scripts using the CLI as a building block:

```bash
#!/bin/bash
# custom-test.sh

echo "Running comprehensive consensus test..."

# Phase 1: Basic functionality
./scripts/consensus-cli.sh send node1 1
sleep 10

# Phase 2: Load testing
./scripts/consensus-cli.sh batch 100 10 random
sleep 30

# Phase 3: Sustained load
./scripts/consensus-cli.sh load-test 60 5 10

echo "Test completed!"
```

### Integration with CI/CD

Use the CLI in automated testing pipelines:

```yaml
# .github/workflows/consensus-test.yml
- name: Test Consensus Federation
  run: |
    docker-compose up -d
    sleep 30
    ./scripts/consensus-cli.sh status
    ./scripts/consensus-cli.sh batch 50 5 random
    docker-compose down
```

This comprehensive CLI utility provides all the testing capabilities needed to validate the Lambda consensus federation system according to requirements 4.1 and 8.1.