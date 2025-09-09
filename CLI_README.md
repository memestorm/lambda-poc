# Lambda Consensus Federation CLI Tool

A comprehensive command-line interface for testing and monitoring the Lambda consensus federation system.

## Quick Start

```bash
# Build the project
mvn clean package

# Show help
./scripts/consensus-cli.sh help

# Check system status
./scripts/consensus-cli.sh status

# Send increment request to node1
./scripts/consensus-cli.sh send node1

# Run batch test
./scripts/consensus-cli.sh batch 50 10 random
```

## Features

### ✅ Command-line tool for sending increment requests to any Lambda
- Send requests to specific nodes: `send node1 5`
- Validate node IDs and provide helpful error messages
- Support for multiple requests to the same node

### ✅ Support for targeting specific nodes or broadcasting to all nodes
- Target specific nodes: `send node2 3`
- Broadcast to all nodes: `broadcast 5`
- Random node selection for load testing

### ✅ Batch testing capabilities for load testing consensus operations
- Configurable request count and concurrency: `batch 100 20 random`
- Detailed performance metrics and success rates
- Support for different targeting strategies (specific node, all nodes, random)

### ✅ Log monitoring and result aggregation for test runs
- Real-time log monitoring: `monitor 120`
- Comprehensive result aggregation with CLIResultAggregator
- Performance metrics including P50, P95, P99 response times
- Historical test data tracking

### ✅ Documentation for CLI usage and testing scenarios
- Complete usage guide in `docs/CLI_USAGE.md`
- Shell script wrapper with helpful commands
- Interactive mode for exploratory testing
- Comprehensive troubleshooting guide

## Architecture

The CLI tool consists of several key components:

1. **ConsensusCLI** - Main CLI application with command parsing and execution
2. **CLIResultAggregator** - Collects and analyzes test results and metrics
3. **LogMonitor** - Real-time log monitoring and analysis
4. **Shell Script Wrapper** - User-friendly interface with status checking

## Commands

| Command | Description | Example |
|---------|-------------|---------|
| `send` | Send increment requests to specific node | `send node1 5` |
| `broadcast` | Send increment requests to all nodes | `broadcast 3` |
| `batch` | Run batch test with specified concurrency | `batch 100 10 random` |
| `load-test` | Run sustained load test | `load-test 60 5 10` |
| `monitor` | Monitor consensus operations | `monitor 120` |
| `status` | Check system health | `status` |
| `help` | Show usage information | `help` |

## Configuration

### Environment Variables
- `SQS_ENDPOINT` - SQS service endpoint (default: http://localhost:9324)
- `NODES` - Comma-separated list of available nodes (default: node1,node2,node3,node4,node5)
- `JAR_PATH` - Path to the JAR file (default: target/lambda-consensus-federation-1.0.0.jar)

### System Properties
```bash
java -Dsqs.endpoint=http://localhost:9324 \
     -Dnodes=node1,node2,node3 \
     -jar target/lambda-consensus-federation-1.0.0.jar help
```

## Testing Scenarios

### Basic Functionality Test
```bash
./scripts/consensus-cli.sh status
./scripts/consensus-cli.sh send node1
./scripts/consensus-cli.sh monitor 30
```

### Load Testing
```bash
./scripts/consensus-cli.sh batch 100 10 random
./scripts/consensus-cli.sh load-test 60 5 10
```

### Node Recovery Testing
```bash
# Start monitoring
./scripts/consensus-cli.sh monitor 300 &

# Send requests
./scripts/consensus-cli.sh broadcast 3

# Restart a node (in another terminal)
docker-compose restart lambda-node2

# Test recovery
./scripts/consensus-cli.sh send node2 5
```

## Implementation Details

### Message Sending
- Uses AWS SDK v2 SQS client with local endpoint configuration
- Supports retry logic with exponential backoff
- Proper error handling and logging

### Result Aggregation
- Tracks success/failure rates per node
- Calculates response time percentiles (P50, P95, P99)
- Maintains historical test data
- Provides comprehensive reporting

### Log Monitoring
- Real-time log file monitoring using file watchers
- Pattern matching for consensus operations, errors, and increment requests
- Node activity tracking with timestamps
- Configurable monitoring duration

### Performance Metrics
- Request success/failure rates
- Response time statistics (min, max, average, percentiles)
- Requests per second calculations
- Node-specific performance breakdown

## Requirements Satisfied

This CLI implementation satisfies the following requirements from the specification:

**Requirement 4.1**: "WHEN a direct message is sent to any Lambda THEN it SHALL initiate a count increment operation"
- ✅ Implemented via `send` command that sends INCREMENT_REQUEST messages to specific Lambda nodes

**Requirement 8.1**: "WHEN any Lambda operation occurs THEN it SHALL log the operation with timestamp and Lambda ID"
- ✅ Implemented via `monitor` command that tracks and aggregates log operations in real-time

## Files Created

### Core Implementation
- `src/main/java/com/example/consensus/cli/ConsensusCLI.java` - Main CLI application
- `src/main/java/com/example/consensus/cli/CLIResultAggregator.java` - Result collection and analysis
- `src/main/java/com/example/consensus/cli/LogMonitor.java` - Log monitoring functionality

### Testing
- `src/test/java/com/example/consensus/cli/ConsensusCLITest.java` - Unit tests for CLI
- `src/test/java/com/example/consensus/cli/CLIResultAggregatorTest.java` - Unit tests for result aggregation
- `src/test/java/com/example/consensus/cli/LogMonitorTest.java` - Unit tests for log monitoring
- `src/test/java/com/example/consensus/cli/CLIIntegrationTest.java` - Integration tests

### Scripts and Documentation
- `scripts/consensus-cli.sh` - Shell script wrapper with enhanced functionality
- `docs/CLI_USAGE.md` - Comprehensive usage documentation
- `CLI_README.md` - This overview document

## Usage Examples

### Send Single Request
```bash
./scripts/consensus-cli.sh send node1
```

### Broadcast to All Nodes
```bash
./scripts/consensus-cli.sh broadcast 5
```

### Batch Testing
```bash
./scripts/consensus-cli.sh batch 100 10 random
```

### Load Testing
```bash
./scripts/consensus-cli.sh load-test 60 5 10
```

### System Monitoring
```bash
./scripts/consensus-cli.sh monitor 120
```

### Interactive Mode
```bash
./scripts/consensus-cli.sh interactive
```

The CLI tool provides a complete testing and monitoring solution for the Lambda consensus federation, enabling comprehensive validation of consensus operations, performance testing, and real-time system monitoring.