# Lambda Consensus Federation - Docker Setup

This document describes how to run the Lambda Consensus Federation locally using Docker containers.

## Prerequisites

- Docker and Docker Compose installed
- Java 21 or higher
- Maven 3.6 or higher

## Quick Start

1. **Build and start the federation:**
   ```bash
   ./scripts/start-consensus.sh
   ```

2. **Test the consensus mechanism:**
   ```bash
   ./scripts/test-consensus.sh
   ```

3. **View logs:**
   ```bash
   ./scripts/view-logs.sh
   ```

4. **Stop the federation:**
   ```bash
   ./scripts/stop-consensus.sh
   ```

## Architecture

The Docker setup includes:

- **5 Lambda containers** (`lambda-1` to `lambda-5`) running on ports 9001-9005
- **ElasticMQ SQS service** on port 9324 (API) and 9325 (management UI)
- **Trigger service** that sends random increment requests
- **Shared network** for inter-container communication

## Services

### Lambda Nodes
- **lambda-1**: http://localhost:9001
- **lambda-2**: http://localhost:9002
- **lambda-3**: http://localhost:9003
- **lambda-4**: http://localhost:9004
- **lambda-5**: http://localhost:9005

Each Lambda node:
- Runs AWS Lambda Runtime Interface Emulator (RIE)
- Has a unique `NODE_ID` environment variable
- Connects to the shared SQS service
- Participates in consensus operations

### SQS Service (ElasticMQ)
- **API Endpoint**: http://localhost:9324
- **Management UI**: http://localhost:9325
- **Queues**: `lambda-1-queue` through `lambda-5-queue`, plus `broadcast-queue`

### Trigger Service
- Sends random increment requests to Lambda nodes
- Configurable interval (5-30 seconds by default)
- Helps demonstrate consensus behavior

## Environment Variables

### Lambda Containers
- `NODE_ID`: Unique identifier (lambda-1, lambda-2, etc.)
- `SQS_ENDPOINT`: SQS service endpoint (http://sqs:9324)
- `KNOWN_NODES`: Comma-separated list of all node IDs
- `LOG_LEVEL`: Logging level (INFO, DEBUG, etc.)
- `AWS_*`: AWS credentials for local development

### Trigger Service
- `SQS_ENDPOINT`: SQS service endpoint
- `LAMBDA_ENDPOINTS`: Comma-separated list of Lambda endpoints
- `TRIGGER_INTERVAL_MIN/MAX`: Random interval bounds in seconds

## Manual Operations

### Send Increment Request
```bash
curl -X POST "http://localhost:9001/2015-03-31/functions/function/invocations" \
     -H "Content-Type: application/json" \
     -d '{
       "type": "INCREMENT_REQUEST",
       "sourceNodeId": "test-client",
       "targetNodeId": "lambda-1",
       "proposedValue": null,
       "proposalId": null,
       "metadata": {}
     }'
```

### Restart Individual Node
```bash
./scripts/restart-node.sh 1  # Restarts lambda-1
```

### View Specific Service Logs
```bash
./scripts/view-logs.sh lambda-1  # View lambda-1 logs only
```

## Testing Scenarios

### Normal Consensus
1. Start all services
2. Send increment requests
3. Observe consensus in logs
4. Verify all nodes converge to same count

### Node Recovery
1. Stop a node: `docker-compose stop lambda-3`
2. Send increment requests
3. Restart node: `docker-compose up -d lambda-3`
4. Observe recovery process in logs

### Network Partition Simulation
1. Stop 2 nodes to create minority partition
2. Send increment requests
3. Observe quorum behavior
4. Restart nodes and observe state synchronization

## Troubleshooting

### Container Won't Start
- Check if ports are available: `netstat -an | grep 9001`
- Verify Docker daemon is running
- Check build logs: `docker-compose logs lambda-1`

### SQS Connection Issues
- Verify SQS container is running: `docker-compose ps sqs`
- Check SQS logs: `docker-compose logs sqs`
- Test SQS endpoint: `curl http://localhost:9324`

### Consensus Not Working
- Check all nodes are healthy: `docker-compose ps`
- Verify SQS queues exist: Visit http://localhost:9325
- Check Lambda logs for errors: `./scripts/view-logs.sh`

### Build Issues
- Ensure Java 21 is installed: `java -version`
- Clean and rebuild: `mvn clean package`
- Check Maven dependencies: `mvn dependency:tree`

## Development Workflow

1. **Make code changes**
2. **Rebuild and restart:**
   ```bash
   ./scripts/stop-consensus.sh
   ./scripts/start-consensus.sh
   ```
3. **Test changes:**
   ```bash
   ./scripts/test-consensus.sh
   ```
4. **Monitor logs:**
   ```bash
   ./scripts/view-logs.sh
   ```

## Performance Monitoring

- **SQS Management UI**: http://localhost:9325 - View queue depths and message rates
- **Container Stats**: `docker stats` - Monitor CPU and memory usage
- **Log Analysis**: Use the built-in log analyzer tools for consensus metrics

## Cleanup

To completely clean up the environment:
```bash
./scripts/stop-consensus.sh
docker system prune -f
docker volume prune -f
```