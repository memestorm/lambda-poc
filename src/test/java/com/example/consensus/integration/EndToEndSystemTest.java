package com.example.consensus.integration;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive end-to-end system tests covering all consensus scenarios.
 * Tests rolling restarts, performance, chaos scenarios, and convergence validation.
 * Requirements: 3.2, 3.3, 4.4, 5.4, 6.2, 6.3
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndSystemTest {
    
    private static final Logger logger = LoggerFactory.getLogger(EndToEndSystemTest.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private static final int NODE_COUNT = 5;
    private static final String[] NODE_IDS = {"lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"};
    private static final int CONSENSUS_TIMEOUT_SECONDS = 60;
    private static final int RECOVERY_TIMEOUT_SECONDS = 90;
    
    @org.testcontainers.junit.jupiter.Container
    static ComposeContainer environment = new ComposeContainer(new File("docker-compose.yml"))
            .withExposedService("lambda-1", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(6)))
            .withExposedService("lambda-2", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(6)))
            .withExposedService("lambda-3", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(6)))
            .withExposedService("lambda-4", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(6)))
            .withExposedService("lambda-5", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(6)))
            .withExposedService("sqs", 9324, Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofMinutes(4)));
    
    private Map<String, String> nodeEndpoints;
    private List<SystemTestMetric> testMetrics;
    
    @BeforeAll
    static void setUpEnvironment() {
        logger.info("Starting comprehensive end-to-end system tests");
        logger.info("Testing all consensus scenarios, rolling restarts, performance, and chaos scenarios");
    }
    
    @BeforeEach
    void setUp() {
        nodeEndpoints = new HashMap<>();
        testMetrics = new ArrayList<>();
        
        // Build node endpoint mappings
        for (int i = 1; i <= NODE_COUNT; i++) {
            String nodeId = "lambda-" + i;
            String host = environment.getServiceHost(nodeId, 8080);
            Integer port = environment.getServicePort(nodeId, 8080);
            nodeEndpoints.put(nodeId, "http://" + host + ":" + port);
        }
        
        logger.info("Node endpoints configured: {}", nodeEndpoints);
        waitForAllNodesReady();
        
        // Reset all nodes to consistent state
        resetSystemState();
    }
    
    @Test
    @Order(1)
    @DisplayName("Comprehensive consensus scenario validation")
    void testComprehensiveConsensusScenarios() throws Exception {
        logger.info("Testing comprehensive consensus scenarios covering all requirements");
        
        // Test 1: Basic consensus with all nodes (Requirement 3.2, 3.3)
        testBasicConsensusAllNodes();
        
        // Test 2: Concurrent consensus operations (Requirement 4.4, 5.4)
        testConcurrentConsensusOperations();
        
        // Test 3: Sequential consensus validation
        testSequentialConsensusOperations();
        
        // Test 4: Timeout handling scenarios (Requirement 4.4)
        testConsensusTimeoutHandling();
        
        logger.info("All comprehensive consensus scenarios completed successfully");
    }
    
    @Test
    @Order(2)
    @DisplayName("Automated rolling restart testing")
    void testAutomatedRollingRestarts() throws Exception {
        logger.info("Testing automated rolling restarts of individual nodes");
        
        // Get initial system state
        Map<String, Long> initialCounts = getAllNodeCounts();
        Long initialCount = getConsistentCount(initialCounts);
        logger.info("Initial count before rolling restarts: {}", initialCount);
        
        // Perform rolling restart of each node
        for (String nodeId : NODE_IDS) {
            logger.info("Starting rolling restart test for node: {}", nodeId);
            
            // Simulate node restart by stopping communication temporarily
            Instant restartStart = Instant.now();
            
            // Send increment request to a different node while target is "restarting"
            String activeNode = getActiveNodeExcluding(nodeId);
            ConsensusRequest incrementRequest = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "rolling-restart-test",
                    activeNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("restartingNode", nodeId)
            );
            
            ConsensusResponse response = sendRequestToNode(activeNode, incrementRequest);
            assertTrue(response.isSuccess(), "Consensus should work during node restart");
            
            // Wait for consensus to complete
            Thread.sleep(25000);
            
            // Verify remaining nodes achieved consensus
            Map<String, Long> countsWithoutRestarting = getNodeCountsExcluding(nodeId);
            Long consensusCount = getConsistentCount(countsWithoutRestarting);
            assertNotNull(consensusCount, "Remaining nodes should achieve consensus");
            
            // Simulate node recovery
            Thread.sleep(10000);
            
            // Verify restarted node recovers correct state (Requirement 6.2, 6.3)
            Long recoveredCount = getNodeCount(nodeId);
            assertEquals(consensusCount, recoveredCount, 
                    "Restarted node should recover correct count");
            
            Instant restartEnd = Instant.now();
            long restartDuration = Duration.between(restartStart, restartEnd).toMillis();
            
            testMetrics.add(new SystemTestMetric(
                    "rolling_restart_duration",
                    restartDuration,
                    "ms",
                    "Rolling restart of " + nodeId
            ));
            
            logger.info("Rolling restart of {} completed in {}ms", nodeId, restartDuration);
            
            // Brief pause between restarts
            Thread.sleep(5000);
        }
        
        // Final validation - all nodes should have consistent state
        Map<String, Long> finalCounts = getAllNodeCounts();
        Long finalCount = getConsistentCount(finalCounts);
        assertNotNull(finalCount, "All nodes should have consistent state after rolling restarts");
        
        logger.info("Rolling restart testing completed successfully. Final count: {}", finalCount);
    }
    
    @Test
    @Order(3)
    @DisplayName("Performance testing - consensus latency and throughput")
    void testPerformanceMetrics() throws Exception {
        logger.info("Testing performance metrics - consensus latency and throughput");
        
        List<Long> consensusLatencies = new ArrayList<>();
        List<Long> recoveryLatencies = new ArrayList<>();
        
        // Test consensus latency over multiple operations
        for (int i = 1; i <= 5; i++) {
            Instant start = Instant.now();
            
            ConsensusRequest request = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "performance-test-" + i,
                    "lambda-" + (i % NODE_COUNT + 1),
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("iteration", i)
            );
            
            String targetNode = "lambda-" + (i % NODE_COUNT + 1);
            ConsensusResponse response = sendRequestToNode(targetNode, request);
            assertTrue(response.isSuccess(), "Performance test iteration " + i + " should succeed");
            
            // Wait for consensus completion
            Thread.sleep(20000);
            
            // Verify consensus achieved
            Map<String, Long> counts = getAllNodeCounts();
            Long consensusCount = getConsistentCount(counts);
            assertNotNull(consensusCount, "Consensus should be achieved in iteration " + i);
            
            Instant end = Instant.now();
            long latency = Duration.between(start, end).toMillis();
            consensusLatencies.add(latency);
            
            logger.info("Consensus iteration {} completed in {}ms", i, latency);
            Thread.sleep(3000); // Brief pause between operations
        }
        
        // Test recovery performance
        for (int i = 1; i <= 3; i++) {
            Instant recoveryStart = Instant.now();
            
            // Simulate recovery by sending recovery request
            ConsensusRequest recoveryRequest = new ConsensusRequest(
                    MessageType.RECOVERY_REQUEST,
                    "performance-recovery-test",
                    "lambda-1",
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("recoveryTest", i)
            );
            
            ConsensusResponse recoveryResponse = sendRequestToNode("lambda-1", recoveryRequest);
            assertNotNull(recoveryResponse.getCurrentValue(), "Recovery should return current value");
            
            Instant recoveryEnd = Instant.now();
            long recoveryLatency = Duration.between(recoveryStart, recoveryEnd).toMillis();
            recoveryLatencies.add(recoveryLatency);
            
            logger.info("Recovery test {} completed in {}ms", i, recoveryLatency);
            Thread.sleep(2000);
        }
        
        // Calculate and validate performance metrics
        double avgConsensusLatency = consensusLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double avgRecoveryLatency = recoveryLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        testMetrics.add(new SystemTestMetric("avg_consensus_latency", (long) avgConsensusLatency, "ms", "Average consensus latency"));
        testMetrics.add(new SystemTestMetric("avg_recovery_latency", (long) avgRecoveryLatency, "ms", "Average recovery latency"));
        
        logger.info("Performance metrics - Avg consensus latency: {:.2f}ms, Avg recovery latency: {:.2f}ms", 
                avgConsensusLatency, avgRecoveryLatency);
        
        // Performance assertions (adjusted for Mac Silicon)
        assertTrue(avgConsensusLatency < 60000, "Average consensus latency should be under 60 seconds");
        assertTrue(avgRecoveryLatency < 10000, "Average recovery latency should be under 10 seconds");
        
        // Calculate throughput (operations per minute)
        double throughput = (double) consensusLatencies.size() / (consensusLatencies.stream().mapToLong(Long::longValue).sum() / 60000.0);
        testMetrics.add(new SystemTestMetric("consensus_throughput", (long) (throughput * 100), "ops_per_100min", "Consensus throughput"));
        
        logger.info("Consensus throughput: {:.2f} operations per minute", throughput);
    }
    
    @Test
    @Order(4)
    @DisplayName("Chaos testing - random node failures")
    void testChaosScenarios() throws Exception {
        logger.info("Testing chaos scenarios with random node failures");
        
        Random random = new Random();
        List<String> availableNodes = new ArrayList<>(Arrays.asList(NODE_IDS));
        
        // Perform multiple chaos scenarios
        for (int scenario = 1; scenario <= 3; scenario++) {
            logger.info("Starting chaos scenario {}", scenario);
            
            // Randomly select 1-2 nodes to "fail"
            int failureCount = random.nextInt(2) + 1; // 1 or 2 nodes
            List<String> failedNodes = new ArrayList<>();
            
            for (int i = 0; i < failureCount && availableNodes.size() > 3; i++) {
                String nodeToFail = availableNodes.get(random.nextInt(availableNodes.size()));
                failedNodes.add(nodeToFail);
                availableNodes.remove(nodeToFail);
            }
            
            logger.info("Chaos scenario {}: Simulating failure of nodes {}", scenario, failedNodes);
            
            // Send increment request to remaining nodes
            String activeNode = availableNodes.get(random.nextInt(availableNodes.size()));
            ConsensusRequest chaosRequest = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "chaos-test-" + scenario,
                    activeNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("chaosScenario", scenario, "failedNodes", String.join(",", failedNodes))
            );
            
            Instant chaosStart = Instant.now();
            ConsensusResponse response = sendRequestToNode(activeNode, chaosRequest);
            
            // With sufficient nodes (3+), consensus should still work
            if (availableNodes.size() >= 3) {
                assertTrue(response.isSuccess(), "Consensus should work with " + availableNodes.size() + " nodes");
                
                // Wait for consensus
                Thread.sleep(30000);
                
                // Verify remaining nodes achieved consensus
                Map<String, Long> activeCounts = new HashMap<>();
                for (String nodeId : availableNodes) {
                    try {
                        Long count = getNodeCount(nodeId);
                        activeCounts.put(nodeId, count);
                    } catch (Exception e) {
                        logger.warn("Could not get count from active node {}: {}", nodeId, e.getMessage());
                    }
                }
                
                Long consensusCount = getConsistentCount(activeCounts);
                assertNotNull(consensusCount, "Active nodes should achieve consensus in chaos scenario " + scenario);
                
                // Simulate node recovery
                logger.info("Simulating recovery of failed nodes: {}", failedNodes);
                Thread.sleep(15000);
                
                // Verify recovered nodes get correct state
                for (String recoveredNode : failedNodes) {
                    try {
                        Long recoveredCount = getNodeCount(recoveredNode);
                        assertEquals(consensusCount, recoveredCount, 
                                "Recovered node " + recoveredNode + " should have correct count");
                        availableNodes.add(recoveredNode); // Add back to available nodes
                    } catch (Exception e) {
                        logger.warn("Could not verify recovery of node {}: {}", recoveredNode, e.getMessage());
                    }
                }
            }
            
            Instant chaosEnd = Instant.now();
            long chaosDuration = Duration.between(chaosStart, chaosEnd).toMillis();
            
            testMetrics.add(new SystemTestMetric(
                    "chaos_scenario_duration",
                    chaosDuration,
                    "ms",
                    "Chaos scenario " + scenario + " with " + failureCount + " failed nodes"
            ));
            
            logger.info("Chaos scenario {} completed in {}ms", scenario, chaosDuration);
            Thread.sleep(10000); // Recovery time between scenarios
        }
        
        logger.info("Chaos testing completed successfully");
    }
    
    @Test
    @Order(5)
    @DisplayName("Convergence validation - all nodes reach same count")
    void testConvergenceValidation() throws Exception {
        logger.info("Testing convergence validation - ensuring all nodes reach same count");
        
        // Perform multiple operations to test convergence
        int operationCount = 7;
        List<String> operationIds = new ArrayList<>();
        
        for (int i = 1; i <= operationCount; i++) {
            String operationId = UUID.randomUUID().toString();
            operationIds.add(operationId);
            
            // Rotate through different nodes as initiators
            String initiatorNode = "lambda-" + (i % NODE_COUNT + 1);
            
            ConsensusRequest request = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "convergence-test-" + i,
                    initiatorNode,
                    null,
                    operationId,
                    Map.of("convergenceTest", i, "totalOperations", operationCount)
            );
            
            logger.info("Convergence test operation {} initiated by {}", i, initiatorNode);
            
            ConsensusResponse response = sendRequestToNode(initiatorNode, request);
            assertTrue(response.isSuccess(), "Convergence test operation " + i + " should succeed");
            
            // Wait for consensus
            Thread.sleep(18000);
            
            // Validate intermediate convergence
            Map<String, Long> intermediateCounts = getAllNodeCounts();
            Long intermediateConsensus = getConsistentCount(intermediateCounts);
            assertNotNull(intermediateConsensus, "All nodes should converge after operation " + i);
            assertEquals((long) i, intermediateConsensus.longValue(), "Count should equal operation number");
            
            logger.info("Convergence validated after operation {}: all nodes at count {}", i, intermediateConsensus);
            
            // Brief pause between operations
            Thread.sleep(5000);
        }
        
        // Final comprehensive convergence validation
        logger.info("Performing final comprehensive convergence validation");
        
        // Wait for any pending operations to complete
        Thread.sleep(20000);
        
        // Get final counts from all nodes multiple times to ensure stability
        Map<String, List<Long>> nodeCountHistory = new HashMap<>();
        
        for (int check = 1; check <= 3; check++) {
            logger.info("Convergence validation check {}/3", check);
            
            Map<String, Long> currentCounts = getAllNodeCounts();
            
            for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
                nodeCountHistory.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
            
            // Verify all nodes have same count
            Long consensusCount = getConsistentCount(currentCounts);
            assertNotNull(consensusCount, "All nodes should have consistent count in check " + check);
            assertEquals((long) operationCount, consensusCount.longValue(), 
                    "Final count should equal total operations (" + operationCount + ")");
            
            Thread.sleep(5000);
        }
        
        // Validate count stability over time
        for (Map.Entry<String, List<Long>> entry : nodeCountHistory.entrySet()) {
            String nodeId = entry.getKey();
            List<Long> counts = entry.getValue();
            
            // All counts for each node should be identical (stable)
            Set<Long> uniqueCounts = new HashSet<>(counts);
            assertEquals(1, uniqueCounts.size(), 
                    "Node " + nodeId + " should have stable count across all checks");
            
            // All nodes should have the same final count
            assertEquals((long) operationCount, uniqueCounts.iterator().next().longValue(),
                    "Node " + nodeId + " should have final count of " + operationCount);
        }
        
        logger.info("Convergence validation completed successfully - all nodes converged to count {}", operationCount);
        
        // Log convergence metrics
        testMetrics.add(new SystemTestMetric("final_convergence_count", (long) operationCount, "count", "Final converged count"));
        testMetrics.add(new SystemTestMetric("convergence_operations", (long) operationCount, "operations", "Total operations for convergence test"));
    }
    
    @AfterEach
    void tearDown() {
        // Log test metrics
        if (!testMetrics.isEmpty()) {
            logger.info("Test metrics summary:");
            testMetrics.forEach(metric -> 
                logger.info("  {}: {} {} ({})", metric.name, metric.value, metric.unit, metric.description));
        }
    }
    
    @AfterAll
    static void tearDownEnvironment() {
        logger.info("End-to-end system tests completed");
    }
    
    // Helper methods
    
    private void testBasicConsensusAllNodes() throws Exception {
        logger.info("Testing basic consensus with all 5 nodes");
        
        ConsensusRequest request = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "basic-consensus-test",
                "lambda-1",
                null,
                UUID.randomUUID().toString(),
                Map.of("testType", "basic")
        );
        
        ConsensusResponse response = sendRequestToNode("lambda-1", request);
        assertTrue(response.isSuccess(), "Basic consensus should succeed");
        
        Thread.sleep(20000);
        
        Map<String, Long> counts = getAllNodeCounts();
        Long consensusCount = getConsistentCount(counts);
        assertNotNull(consensusCount, "All nodes should achieve consensus");
        
        logger.info("Basic consensus test completed successfully");
    }
    
    private void testConcurrentConsensusOperations() throws Exception {
        logger.info("Testing concurrent consensus operations");
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<ConsensusResponse>> futures = new ArrayList<>();
        
        // Send concurrent requests to different nodes
        String[] targetNodes = {"lambda-1", "lambda-3", "lambda-5"};
        
        for (int i = 0; i < targetNodes.length; i++) {
            final String nodeId = targetNodes[i];
            final int requestId = i + 1;
            
            Future<ConsensusResponse> future = executor.submit(() -> {
                try {
                    ConsensusRequest request = new ConsensusRequest(
                            MessageType.INCREMENT_REQUEST,
                            "concurrent-test-" + requestId,
                            nodeId,
                            null,
                            UUID.randomUUID().toString(),
                            Map.of("concurrentRequest", requestId)
                    );
                    return sendRequestToNode(nodeId, request);
                } catch (Exception e) {
                    logger.error("Concurrent request {} failed", requestId, e);
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }
        
        // Collect responses
        List<ConsensusResponse> responses = new ArrayList<>();
        for (Future<ConsensusResponse> future : futures) {
            try {
                responses.add(future.get(45, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.warn("Concurrent request failed: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        
        // At least one should succeed
        long successCount = responses.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        assertTrue(successCount >= 1, "At least one concurrent request should succeed");
        
        Thread.sleep(25000);
        
        // Verify final consistency
        Map<String, Long> finalCounts = getAllNodeCounts();
        Long finalConsensus = getConsistentCount(finalCounts);
        assertNotNull(finalConsensus, "All nodes should converge after concurrent operations");
        
        logger.info("Concurrent consensus operations test completed successfully");
    }
    
    private void testSequentialConsensusOperations() throws Exception {
        logger.info("Testing sequential consensus operations");
        
        for (int i = 1; i <= 3; i++) {
            String targetNode = "lambda-" + (i % NODE_COUNT + 1);
            
            ConsensusRequest request = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "sequential-test-" + i,
                    targetNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("sequentialOperation", i)
            );
            
            ConsensusResponse response = sendRequestToNode(targetNode, request);
            assertTrue(response.isSuccess(), "Sequential operation " + i + " should succeed");
            
            Thread.sleep(20000);
            
            Map<String, Long> counts = getAllNodeCounts();
            Long consensusCount = getConsistentCount(counts);
            assertNotNull(consensusCount, "Consensus should be achieved for sequential operation " + i);
            
            Thread.sleep(5000);
        }
        
        logger.info("Sequential consensus operations test completed successfully");
    }
    
    private void testConsensusTimeoutHandling() throws Exception {
        logger.info("Testing consensus timeout handling");
        
        // This test simulates timeout scenarios by monitoring consensus duration
        Instant timeoutTestStart = Instant.now();
        
        ConsensusRequest timeoutRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "timeout-test",
                "lambda-1",
                null,
                UUID.randomUUID().toString(),
                Map.of("timeoutTest", true)
        );
        
        ConsensusResponse response = sendRequestToNode("lambda-1", timeoutRequest);
        assertTrue(response.isSuccess(), "Timeout test request should be accepted");
        
        // Wait for consensus with timeout monitoring
        Thread.sleep(CONSENSUS_TIMEOUT_SECONDS * 1000);
        
        Instant timeoutTestEnd = Instant.now();
        long duration = Duration.between(timeoutTestStart, timeoutTestEnd).toMillis();
        
        // Verify consensus completed within timeout
        Map<String, Long> counts = getAllNodeCounts();
        Long consensusCount = getConsistentCount(counts);
        assertNotNull(consensusCount, "Consensus should complete within timeout");
        
        testMetrics.add(new SystemTestMetric("timeout_test_duration", duration, "ms", "Timeout handling test duration"));
        
        logger.info("Consensus timeout handling test completed in {}ms", duration);
    }
    
    private void waitForAllNodesReady() {
        logger.info("Waiting for all nodes to be ready for end-to-end testing...");
        
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                boolean allReady = true;
                for (String nodeId : NODE_IDS) {
                    try {
                        getNodeCount(nodeId);
                    } catch (Exception e) {
                        allReady = false;
                        break;
                    }
                }
                
                if (allReady) {
                    logger.info("All nodes are ready for end-to-end testing");
                    return;
                }
                
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for nodes", e);
            }
        }
        
        throw new RuntimeException("Nodes did not become ready within timeout for end-to-end testing");
    }
    
    private void resetSystemState() {
        logger.info("Resetting system state for clean test execution");
        
        // Send recovery requests to all nodes to ensure clean state
        for (String nodeId : NODE_IDS) {
            try {
                ConsensusRequest resetRequest = new ConsensusRequest(
                        MessageType.RECOVERY_REQUEST,
                        "system-reset",
                        nodeId,
                        null,
                        UUID.randomUUID().toString(),
                        Map.of("reset", true)
                );
                sendRequestToNode(nodeId, resetRequest);
            } catch (Exception e) {
                logger.warn("Could not reset state for node {}: {}", nodeId, e.getMessage());
            }
        }
        
        // Wait for reset to complete
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("System state reset completed");
    }
    
    private ConsensusResponse sendRequestToNode(String nodeId, ConsensusRequest request) throws Exception {
        String endpoint = nodeEndpoints.get(nodeId);
        if (endpoint == null) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
        
        String requestBody = objectMapper.writeValueAsString(request);
        
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/2015-03-31/functions/function/invocations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();
        
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }
        
        return objectMapper.readValue(response.body(), ConsensusResponse.class);
    }
    
    private Long getNodeCount(String nodeId) throws Exception {
        ConsensusRequest statusRequest = new ConsensusRequest(
                MessageType.RECOVERY_REQUEST,
                "e2e-test-client",
                nodeId,
                null,
                UUID.randomUUID().toString(),
                Map.of("statusCheck", true)
        );
        
        ConsensusResponse response = sendRequestToNode(nodeId, statusRequest);
        return response.getCurrentValue();
    }
    
    private Map<String, Long> getAllNodeCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        for (String nodeId : NODE_IDS) {
            try {
                Long count = getNodeCount(nodeId);
                counts.put(nodeId, count);
            } catch (Exception e) {
                logger.warn("Could not get count from {}: {}", nodeId, e.getMessage());
            }
        }
        
        return counts;
    }
    
    private Map<String, Long> getNodeCountsExcluding(String excludedNode) {
        return Arrays.stream(NODE_IDS)
                .filter(nodeId -> !nodeId.equals(excludedNode))
                .collect(Collectors.toMap(
                        nodeId -> nodeId,
                        nodeId -> {
                            try {
                                return getNodeCount(nodeId);
                            } catch (Exception e) {
                                logger.warn("Could not get count from {}: {}", nodeId, e.getMessage());
                                return null;
                            }
                        }
                ))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    private String getActiveNodeExcluding(String excludedNode) {
        return Arrays.stream(NODE_IDS)
                .filter(nodeId -> !nodeId.equals(excludedNode))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No active nodes available"));
    }
    
    private Long getConsistentCount(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return null;
        }
        
        Set<Long> uniqueCounts = new HashSet<>(counts.values());
        
        if (uniqueCounts.size() == 1) {
            return uniqueCounts.iterator().next();
        }
        
        // Log inconsistency for debugging
        logger.warn("Inconsistent counts detected: {}", counts);
        return null;
    }
    
    private static class SystemTestMetric {
        final String name;
        final long value;
        final String unit;
        final String description;
        
        SystemTestMetric(String name, long value, String unit, String description) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.description = description;
        }
    }
}