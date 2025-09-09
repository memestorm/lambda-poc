# Lambda Consensus Federation - Troubleshooting Guide

This guide provides comprehensive troubleshooting information for common issues encountered when running the Lambda Consensus Federation system.

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Container Issues](#container-issues)
- [Network and Communication](#network-and-communication)
- [Consensus Problems](#consensus-problems)
- [Recovery Issues](#recovery-issues)
- [Performance Problems](#performance-problems)
- [Logging and Monitoring](#logging-and-monitoring)
- [Development Issues](#development-issues)
- [Advanced Debugging](#advanced-debugging)

## Quick Diagnostics

### System Health Check

Run this comprehensive health check script:

```bash
#!/bin/bash
echo "=== Lambda Consensus Federation Health Check ==="

# Check Docker
echo "1. Docker Status:"
docker --version
docker-compose --version

# Check containers
echo "2. Container Status:"
docker-compose ps

# Check SQS
echo "3. SQS Status:"
curl -s http://localhost:9324/ > /dev/null && echo "✅ SQS Available" || echo "❌ SQS Unavailable"

# Check node status
echo "4. Node Status:"
./scripts/consensus-cli.sh status 2>/dev/null || echo "❌ CLI not responding"

# Check logs for errors
echo "5. Recent Errors:"
docker-compose logs --tail=10 | grep -i error | head -5

echo "=== Health Check Complete ==="
```

### Common Commands

```bash
# Quick status check
docker-compose ps && ./scripts/consensus-cli.sh status

# View recent logs
docker-compose logs --tail=50

# Restart everything
docker-compose down && docker-compose up -d

# Check resource usage
docker stats --no-stream
```

## Container Issues

### Issue: Containers Won't Start

**Symptoms:**
- Containers exit immediately with code 1 or 125
- "Port already in use" errors
- "No such file or directory" errors

**Diagnosis:**
```bash
# Check container logs
docker-compose logs lambda-node-1

# Check port conflicts
netstat -tulpn | grep :9324
lsof -i :9324

# Verify image build
docker images | grep lambda-consensus
```

**Solutions:**

1. **Port Conflicts:**
```bash
# Kill processes using ports
sudo lsof -ti:9324 | xargs kill -9

# Or change ports in docker-compose.yml
ports:
  - "9325:9324"  # Use different external port
```

2. **Build Issues:**
```bash
# Rebuild images
docker-compose build --no-cache

# Clean Docker system
docker system prune -f
docker volume prune -f
```

3. **Permission Issues:**
```bash
# Fix file permissions
chmod +x scripts/*.sh

# Check Docker permissions
sudo usermod -aG docker $USER
# Then logout and login again
```

### Issue: Container Memory Issues

**Symptoms:**
- Containers killed with exit code 137
- Out of memory errors in logs
- Slow performance

**Diagnosis:**
```bash
# Check memory usage
docker stats --format "table {{.Container}}\t{{.MemUsage}}\t{{.MemPerc}}"

# Check system memory
free -h
```

**Solutions:**

1. **Increase Container Memory:**
```yaml
# In docker-compose.yml
services:
  lambda-node-1:
    mem_limit: 1g
    memswap_limit: 1g
```

2. **Optimize JVM Settings:**
```dockerfile
# In Dockerfile
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"
```

3. **System Memory:**
```bash
# Free up system memory
sudo sysctl vm.drop_caches=3
```

### Issue: Container Networking Problems

**Symptoms:**
- Containers can't communicate with each other
- SQS connection refused errors
- DNS resolution failures

**Diagnosis:**
```bash
# Check network
docker network ls
docker network inspect lambda-consensus-federation_consensus-network

# Test connectivity
docker-compose exec lambda-node-1 ping elasticmq
docker-compose exec lambda-node-1 curl http://elasticmq:9324/
```

**Solutions:**

1. **Recreate Network:**
```bash
docker-compose down
docker network prune
docker-compose up -d
```

2. **Check Network Configuration:**
```yaml
# In docker-compose.yml
networks:
  consensus-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Network and Communication

### Issue: SQS Connection Failures

**Symptoms:**
- "Connection refused" errors
- SQS timeout exceptions
- Messages not being delivered

**Diagnosis:**
```bash
# Test SQS directly
curl http://localhost:9324/

# Check ElasticMQ logs
docker-compose logs elasticmq

# Test from inside container
docker-compose exec lambda-node-1 curl http://elasticmq:9324/
```

**Solutions:**

1. **Restart SQS Service:**
```bash
docker-compose restart elasticmq
```

2. **Check SQS Configuration:**
```bash
# Verify elasticmq.conf
cat elasticmq.conf

# Check environment variables
docker-compose exec lambda-node-1 env | grep SQS
```

3. **Network Connectivity:**
```bash
# Test internal networking
docker-compose exec lambda-node-1 nslookup elasticmq
```

### Issue: Message Delivery Problems

**Symptoms:**
- Messages sent but not received
- Duplicate message processing
- Message ordering issues

**Diagnosis:**
```bash
# Check queue status
curl http://localhost:9324/ | grep -A5 -B5 "ApproximateNumberOfMessages"

# Monitor message flow
docker-compose logs -f | grep "MESSAGE.*SENT\|MESSAGE.*RECEIVED"

# Check for dead letter queues
curl http://localhost:9324/ | grep -i dead
```

**Solutions:**

1. **Queue Management:**
```bash
# Purge queues if needed
curl -X POST "http://localhost:9324/queue/consensus-lambda-node-1-queue" \
  -d "Action=PurgeQueue"
```

2. **Message Visibility:**
```bash
# Check visibility timeout settings
# In SQSMessageHandlerImpl, verify:
# VISIBILITY_TIMEOUT_SECONDS = "30"
```

3. **Serialization Issues:**
```bash
# Check for serialization errors in logs
docker-compose logs | grep -i "serialization\|deserialize"
```

## Consensus Problems

### Issue: Nodes Don't Reach Consensus

**Symptoms:**
- Different count values across nodes
- Consensus timeout errors
- Nodes stuck in PROPOSING state

**Diagnosis:**
```bash
# Check node states
./scripts/consensus-cli.sh status

# Look for consensus failures
docker-compose logs | grep -i "consensus.*fail"

# Check proposal timeouts
docker-compose logs | grep -i "timeout"
```

**Solutions:**

1. **Verify All Nodes Are Running:**
```bash
# Ensure all 5 nodes are healthy
docker-compose ps | grep -c "Up"  # Should be 6 (5 nodes + elasticmq)
```

2. **Check Network Partitions:**
```bash
# Test connectivity between nodes
for i in {1..5}; do
  for j in {1..5}; do
    if [ $i -ne $j ]; then
      docker-compose exec lambda-node-$i ping -c1 lambda-node-$j
    fi
  done
done
```

3. **Reset Consensus State:**
```bash
# Restart all nodes to reset state
docker-compose restart lambda-node-1 lambda-node-2 lambda-node-3 lambda-node-4 lambda-node-5
```

### Issue: Split-Brain Scenarios

**Symptoms:**
- Multiple nodes think they're the leader
- Conflicting proposals
- Inconsistent state across nodes

**Diagnosis:**
```bash
# Check for multiple active proposals
docker-compose logs | grep "PROPOSE" | grep "initiated" | tail -10

# Verify quorum calculations
docker-compose logs | grep -i "quorum"
```

**Solutions:**

1. **Ensure Proper Quorum:**
```bash
# Verify KNOWN_NODES configuration
docker-compose exec lambda-node-1 env | grep KNOWN_NODES
```

2. **Restart Problematic Nodes:**
```bash
# Identify and restart nodes with conflicting state
./scripts/restart-node.sh lambda-node-X
```

### Issue: Consensus Timeouts

**Symptoms:**
- "Consensus timeout" errors
- Operations taking longer than 60 seconds
- Nodes stuck waiting for votes

**Diagnosis:**
```bash
# Check consensus timing
docker-compose logs | grep "consensus_duration" | tail -10

# Look for slow nodes
docker-compose logs | grep -i "slow\|timeout"
```

**Solutions:**

1. **Increase Timeout Values:**
```java
// In ConsensusManagerImpl.java
private static final int CONSENSUS_TIMEOUT_SECONDS = 120; // Increase from 60
```

2. **Check System Performance:**
```bash
# Monitor system resources during consensus
docker stats --no-stream
```

## Recovery Issues

### Issue: Node Recovery Failures

**Symptoms:**
- Restarted nodes don't recover
- "Insufficient quorum" errors
- Nodes stuck in RECOVERING state

**Diagnosis:**
```bash
# Check recovery logs
docker-compose logs lambda-node-1 | grep -i "recovery"

# Verify quorum availability
./scripts/consensus-cli.sh status | grep -c "IDLE"  # Should be >= 3
```

**Solutions:**

1. **Ensure Sufficient Nodes:**
```bash
# Start enough nodes for quorum
docker-compose up -d lambda-node-1 lambda-node-2 lambda-node-3
```

2. **Manual Recovery Trigger:**
```bash
# Restart node to trigger recovery
./scripts/restart-node.sh lambda-node-X
```

3. **Check Recovery Responses:**
```bash
# Look for recovery response issues
docker-compose logs | grep "RECOVERY_RESPONSE"
```

### Issue: Quorum Not Available

**Symptoms:**
- "Minimum quorum not available" errors
- Recovery operations rejected
- System unable to make progress

**Diagnosis:**
```bash
# Count available nodes
docker-compose ps | grep "Up" | grep lambda-node | wc -l

# Check node health
for i in {1..5}; do
  echo "Node $i:"
  docker-compose exec lambda-node-$i echo "OK" 2>/dev/null || echo "FAILED"
done
```

**Solutions:**

1. **Start More Nodes:**
```bash
# Ensure at least 3 nodes are running
docker-compose up -d lambda-node-1 lambda-node-2 lambda-node-3
```

2. **Reset All Nodes:**
```bash
# Complete system restart
docker-compose down
docker-compose up -d
```

## Performance Problems

### Issue: High Latency

**Symptoms:**
- Consensus operations taking > 10 seconds
- Slow message processing
- High response times

**Diagnosis:**
```bash
# Check consensus timing
docker-compose logs | grep "consensus_duration" | awk '{print $NF}' | sort -n

# Monitor system resources
docker stats --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}"

# Check message queue lengths
curl http://localhost:9324/ | grep "ApproximateNumberOfMessages"
```

**Solutions:**

1. **Optimize JVM Settings:**
```dockerfile
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

2. **Increase Resources:**
```yaml
# In docker-compose.yml
services:
  lambda-node-1:
    cpus: '1.0'
    mem_limit: 1g
```

3. **Reduce Message Processing Time:**
```bash
# Check for serialization bottlenecks
docker-compose logs | grep -i "serialization" | grep -i "slow"
```

### Issue: High Memory Usage

**Symptoms:**
- Containers using > 1GB memory
- Out of memory errors
- System swapping

**Diagnosis:**
```bash
# Check memory usage patterns
docker stats --format "table {{.Container}}\t{{.MemUsage}}\t{{.MemPerc}}" --no-stream

# Look for memory leaks
docker-compose logs | grep -i "memory\|heap\|gc"
```

**Solutions:**

1. **Tune Garbage Collection:**
```dockerfile
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGC"
```

2. **Limit Container Memory:**
```yaml
services:
  lambda-node-1:
    mem_limit: 512m
    memswap_limit: 512m
```

3. **Monitor for Leaks:**
```bash
# Enable heap dumps
ENV JAVA_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"
```

## Logging and Monitoring

### Issue: Missing or Incomplete Logs

**Symptoms:**
- No logs appearing in console
- Missing structured log fields
- Log files not being created

**Diagnosis:**
```bash
# Check log configuration
docker-compose exec lambda-node-1 cat /opt/java/openjdk/conf/logging.properties

# Verify log levels
docker-compose exec lambda-node-1 env | grep LOG_LEVEL

# Check file permissions
docker-compose exec lambda-node-1 ls -la /var/log/
```

**Solutions:**

1. **Fix Log Configuration:**
```bash
# Set appropriate log level
export LOG_LEVEL=INFO
docker-compose up -d
```

2. **Check Logback Configuration:**
```xml
<!-- In src/main/resources/logback.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

### Issue: Log Analysis Problems

**Symptoms:**
- Can't parse structured logs
- Missing performance metrics
- Difficulty correlating events

**Diagnosis:**
```bash
# Check log format
docker-compose logs lambda-node-1 | head -5

# Verify JSON structure
docker-compose logs lambda-node-1 | jq '.' | head -10
```

**Solutions:**

1. **Use Log Analysis Tools:**
```bash
# Install jq for JSON parsing
sudo apt-get install jq

# Parse consensus operations
docker-compose logs | jq 'select(.operation == "CONSENSUS_OPERATION")'
```

2. **Custom Log Analysis:**
```bash
# Use the built-in log analyzer
java -cp target/lambda-consensus-federation-1.0.0.jar \
  com.example.consensus.logging.LogAnalyzerCLI \
  --analyze-consensus logs/
```

## Development Issues

### Issue: Build Failures

**Symptoms:**
- Maven build errors
- Compilation failures
- Test failures

**Diagnosis:**
```bash
# Check Java version
java -version
mvn -version

# Run with debug output
mvn clean package -X

# Check dependencies
mvn dependency:tree
```

**Solutions:**

1. **Fix Java Version:**
```bash
# Ensure Java 21 is installed and active
sudo update-alternatives --config java
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

2. **Clean Build:**
```bash
# Clean everything and rebuild
mvn clean
rm -rf target/
mvn package -DskipTests
```

3. **Dependency Issues:**
```bash
# Update dependencies
mvn versions:display-dependency-updates
mvn clean package -U  # Force update
```

### Issue: Test Failures

**Symptoms:**
- Unit tests failing
- Integration tests timing out
- Testcontainers issues

**Diagnosis:**
```bash
# Run specific test with debug
mvn test -Dtest=ConsensusLambdaHandlerTest -X

# Check test resources
ls -la src/test/resources/

# Verify Docker for tests
docker info
```

**Solutions:**

1. **Fix Test Environment:**
```bash
# Ensure Docker is available for Testcontainers
sudo usermod -aG docker $USER

# Set test properties
export TESTCONTAINERS_RYUK_DISABLED=true
```

2. **Increase Test Timeouts:**
```java
// In test files
@Timeout(value = 60, unit = TimeUnit.SECONDS)
```

## Advanced Debugging

### Debug Mode Setup

1. **Enable Debug Logging:**
```bash
export LOG_LEVEL=DEBUG
docker-compose down
docker-compose up -d
```

2. **Remote Debugging:**
```dockerfile
# Add to Dockerfile
ENV JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

3. **Profiling:**
```bash
# Add JVM profiling options
ENV JAVA_OPTS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=profile.jfr"
```

### Network Debugging

```bash
# Capture network traffic
docker-compose exec lambda-node-1 tcpdump -i eth0 -w /tmp/capture.pcap

# Monitor SQS traffic
docker-compose exec lambda-node-1 netstat -an | grep 9324

# Check DNS resolution
docker-compose exec lambda-node-1 nslookup elasticmq
```

### State Debugging

```bash
# Dump node state
docker-compose exec lambda-node-1 jstack 1

# Check thread states
docker-compose exec lambda-node-1 jcmd 1 Thread.print

# Monitor GC
docker-compose logs lambda-node-1 | grep -i "gc"
```

### Message Flow Tracing

```bash
# Trace message flow with correlation IDs
docker-compose logs -f | grep -E "(proposalId|messageId)" | sort

# Monitor queue depths
watch -n 5 'curl -s http://localhost:9324/ | grep -o "ApproximateNumberOfMessages[^<]*" | head -5'
```

## Getting Help

### Log Collection

When reporting issues, collect these logs:

```bash
#!/bin/bash
mkdir -p debug-logs
docker-compose logs > debug-logs/all-containers.log
docker-compose ps > debug-logs/container-status.txt
docker stats --no-stream > debug-logs/resource-usage.txt
./scripts/consensus-cli.sh status > debug-logs/node-status.txt 2>&1
docker network inspect lambda-consensus-federation_consensus-network > debug-logs/network-info.json
```

### System Information

```bash
# Collect system info
uname -a > debug-logs/system-info.txt
docker version >> debug-logs/system-info.txt
docker-compose version >> debug-logs/system-info.txt
free -h >> debug-logs/system-info.txt
df -h >> debug-logs/system-info.txt
```

### Support Channels

- **GitHub Issues**: For bug reports and feature requests
- **Discussions**: For questions and community support
- **Wiki**: For additional documentation and examples

---

**Remember**: Most issues can be resolved by restarting the system with `docker-compose down && docker-compose up -d`. When in doubt, check the logs first!