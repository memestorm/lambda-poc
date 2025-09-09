package com.example.consensus.integration;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
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
 * Comprehensive system validation tests focusing on convergence properties.
 * Ensures all nodes reach the same count value under various scenarios.
 * Requirements: 3.2, 3.3, 6.2, 6.3
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemConvergenceValidationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemConvergenceValidationTest.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private static final String[] NODE_IDS = {"lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"};
    private static final int CONVERGENCE_VALIDATION_ROUNDS = 5;
    private static final int CONVERGENCE_TIMEOUT_SECONDS = 90;
    
    @Container
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
    private List<ConvergenceMetric> convergenceMetrics;
    
    @BeforeEach
    void setUp() {
        nodeEndpoints = new HashMap<>();
        convergenceMetrics = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            String nodeId = "lambda-" + i;
            String host = environment.getServiceHost(nodeId, 8080);
            Integer port = environment.getServicePort(nodeId, 8080);
            nodeEndpoints.put(nodeId, "http://" + host + ":" + port);
        }
        
        logger.info("Convergence validation setup completed with endpoints: {}", nodeEndpoints);
        waitForSystemReady();
        resetSystemToKnownState();
    }
    
    @Test
    @Order(1)
    @DisplayName("Basic convergence validation - single operations")
    void testBasicConvergenceValidation() throws Exception {
        logger.info("Testing basic convergence validation with single operations");
        
        for (int round = 1; round <= CONVERGENCE_VALIDATION_ROUNDS; round++) {
            logger.info("Convergence validation round {}/{}", round, CONVERGENCE_VALIDATION_ROUNDS);
            
            Instant roundStart = Instant.now();
            
            // Send increment request to a different node each round
            String targetNode = "lambda-" + (round % NODE_IDS.length + 1);
            
            ConsensusRequest request = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "convergence-validation-" + round,
                    targetNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("validationRound", round, "targetNode", targetNode)
            );
            
            ConsensusResponse response = sendRequestToNode(targetNode, request);
            assertTrue(response.isSuccess(), "Convergence validation round " + round + " should succeed");
            
            // Wait for consensus and validate convergence
            ConvergenceResult convergenceResult = waitForConvergence(round, CONVERGENCE_TIMEOUT_SECONDS);
            
            Instant roundEnd = Instant.now();
            long roundDuration = Duration.between(roundStart, roundEnd).toMillis();
            
            // Validate convergence properties
            assertTrue(convergenceResult.converged, "Round " + round + " should achieve convergence");
            assertEquals((long) round, convergenceResult.convergedValue, 
                    "Converged value should equal round number");
            assertEquals(NODE_IDS.length, convergenceResult.participatingNodes, 
                    "All nodes should participate in convergence");
            
            convergenceMetrics.add(new ConvergenceMetric(
                    "basic_convergence_round_" + round,
                    roundDuration,
                    convergenceResult.convergenceTime,
                    convergenceResult.convergedValue,
                    convergenceResult.participatingNodes,
                    convergenceResult.consistencyChecks
            ));
            
            logger.info("Round {} converged to value {} in {}ms with {} nodes", 
                    round, convergenceResult.convergedValue, convergenceResult.convergenceTime, 
                    convergenceResult.participatingNodes);
            
            // Brief pause between rounds
            Thread.sleep(3000);
        }
        
        // Final validation - all metrics should show successful convergence
        convergenceMetrics.forEach(metric -> {
            assertTrue(metric.convergedValue > 0, "Each round should have positive converged value");
            assertTrue(metric.convergenceTimeMs > 0, "Each round should have measurable convergence time");
            assertEquals(NODE_IDS.length, metric.participatingNodes, "All nodes should participate");
        });
        
        logger.info("Basic convergence validation completed successfully");
    }
    
    @Test
    @Order(2)
    @DisplayName("Concurrent operations convergence validation")
    void testConcurrentOperationsConvergence() throws Exception {
        logger.info("Testing convergence validation with concurrent operations");
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        for (int batch = 1; batch <= 3; batch++) {
            logger.info("Concurrent convergence batch {}/3", batch);
            
            Instant batchStart = Instant.now();
            List<Future<ConsensusResponse>> futures = new ArrayList<>();
            
            // Send 3 concurrent requests to different nodes
            String[] targetNodes = {"lambda-1", "lambda-3", "lambda-5"};
            
            for (int i = 0; i < targetNodes.length; i++) {
                final String nodeId = targetNodes[i];
                final int requestId = batch * 10 + i + 1;
                final int finalBatch = batch; // Make batch effectively final
                
                Future<ConsensusResponse> future = executor.submit(() -> {
                    try {
                        ConsensusRequest request = new ConsensusRequest(
                                MessageType.INCREMENT_REQUEST,
                                "concurrent-convergence-" + requestId,
                                nodeId,
                                null,
                                UUID.randomUUID().toString(),
                                Map.of("concurrentBatch", finalBatch, "requestId", requestId)
                        );
                        return sendRequestToNode(nodeId, request);
                    } catch (Exception e) {
                        logger.error("Concurrent convergence request {} failed", requestId, e);
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
            }
            
            // Collect responses
            List<ConsensusResponse> responses = new ArrayList<>();
            for (Future<ConsensusResponse> future : futures) {
                try {
                    responses.add(future.get(60, TimeUnit.SECONDS));
                } catch (Exception e) {
                    logger.warn("Concurrent convergence request failed: {}", e.getMessage());
                }
            }
            
            // At least one should succeed
            long successCount = responses.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
            assertTrue(successCount >= 1, "At least one concurrent request should succeed in batch " + batch);
            
            // Wait for convergence after concurrent operations
            ConvergenceResult convergenceResult = waitForConvergence(
                    getCurrentExpectedCount() + (int) successCount, 
                    CONVERGENCE_TIMEOUT_SECONDS
            );
            
            Instant batchEnd = Instant.now();
            long batchDuration = Duration.between(batchStart, batchEnd).toMillis();
            
            assertTrue(convergenceResult.converged, "Concurrent batch " + batch + " should achieve convergence");
            
            convergenceMetrics.add(new ConvergenceMetric(
                    "concurrent_convergence_batch_" + batch,
                    batchDuration,
                    convergenceResult.convergenceTime,
                    convergenceResult.convergedValue,
                    convergenceResult.participatingNodes,
                    convergenceResult.consistencyChecks
            ));
            
            logger.info("Concurrent batch {} converged to value {} in {}ms", 
                    batch, convergenceResult.convergedValue, convergenceResult.convergenceTime);
            
            // Pause between batches
            Thread.sleep(5000);
        }
        
        executor.shutdown();
        logger.info("Concurrent operations convergence validation completed successfully");
    }
    
    @Test
    @Order(3)
    @DisplayName("Recovery scenario convergence validation")
    void testRecoveryScenarioConvergence() throws Exception {
        logger.info("Testing convergence validation during recovery scenarios");
        
        // Get initial state
        Map<String, Long> initialCounts = getAllNodeCounts();
        Long initialCount = getConsistentValue(initialCounts);
        assertNotNull(initialCount, "System should start with consistent state");
        
        // Simulate node recovery scenarios
        for (String recoveringNode : Arrays.asList("lambda-2", "lambda-4")) {
            logger.info("Testing recovery convergence for node: {}", recoveringNode);
            
            Instant recoveryStart = Instant.now();
            
            // Send increment request to a different node while simulating recovery
            String activeNode = getActiveNodeExcluding(recoveringNode);
            
            ConsensusRequest incrementRequest = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "recovery-convergence-test",
                    activeNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("recoveringNode", recoveringNode, "activeNode", activeNode)
            );
            
            ConsensusResponse response = sendRequestToNode(activeNode, incrementRequest);
            assertTrue(response.isSuccess(), "Increment should succeed during recovery simulation");
            
            // Wait for consensus among active nodes
            Thread.sleep(20000);
            
            // Simulate recovery completion and validate convergence
            Thread.sleep(10000); // Recovery time
            
            ConvergenceResult convergenceResult = waitForConvergence(
                    getCurrentExpectedCount() + 1, 
                    CONVERGENCE_TIMEOUT_SECONDS
            );
            
            Instant recoveryEnd = Instant.now();
            long recoveryDuration = Duration.between(recoveryStart, recoveryEnd).toMillis();
            
            assertTrue(convergenceResult.converged, "Recovery scenario should achieve convergence");
            assertEquals(NODE_IDS.length, convergenceResult.participatingNodes, 
                    "All nodes including recovered node should participate");
            
            // Validate that the "recovered" node has the correct value
            Long recoveredNodeCount = getNodeCount(recoveringNode);
            assertEquals(convergenceResult.convergedValue, recoveredNodeCount, 
                    "Recovered node should have converged value");
            
            convergenceMetrics.add(new ConvergenceMetric(
                    "recovery_convergence_" + recoveringNode,
                    recoveryDuration,
                    convergenceResult.convergenceTime,
                    convergenceResult.convergedValue,
                    convergenceResult.participatingNodes,
                    convergenceResult.consistencyChecks
            ));
            
            logger.info("Recovery convergence for {} completed: value={}, time={}ms", 
                    recoveringNode, convergenceResult.convergedValue, convergenceResult.convergenceTime);
            
            Thread.sleep(5000); // Pause between recovery tests
        }
        
        logger.info("Recovery scenario convergence validation completed successfully");
    }
    
    @Test
    @Order(4)
    @DisplayName("Long-term stability convergence validation")
    void testLongTermStabilityConvergence() throws Exception {
        logger.info("Testing long-term stability and convergence maintenance");
        
        // Perform multiple operations over time and validate sustained convergence
        int operationsCount = 10;
        List<Long> stabilitySnapshots = new ArrayList<>();
        
        for (int operation = 1; operation <= operationsCount; operation++) {
            logger.info("Stability test operation {}/{}", operation, operationsCount);
            
            // Rotate through different initiator nodes
            String initiatorNode = "lambda-" + (operation % NODE_IDS.length + 1);
            
            ConsensusRequest request = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "stability-test-" + operation,
                    initiatorNode,
                    null,
                    UUID.randomUUID().toString(),
                    Map.of("stabilityTest", operation, "totalOperations", operationsCount)
            );
            
            ConsensusResponse response = sendRequestToNode(initiatorNode, request);
            assertTrue(response.isSuccess(), "Stability test operation " + operation + " should succeed");
            
            // Wait for convergence
            ConvergenceResult convergenceResult = waitForConvergence(
                    getCurrentExpectedCount() + 1, 
                    CONVERGENCE_TIMEOUT_SECONDS
            );
            
            assertTrue(convergenceResult.converged, "Operation " + operation + " should achieve convergence");
            stabilitySnapshots.add(convergenceResult.convergedValue);
            
            // Validate stability over multiple checks
            for (int stabilityCheck = 1; stabilityCheck <= 3; stabilityCheck++) {
                Thread.sleep(2000);
                
                Map<String, Long> counts = getAllNodeCounts();
                Long stableValue = getConsistentValue(counts);
                assertNotNull(stableValue, "System should maintain stability in check " + stabilityCheck);
                assertEquals(convergenceResult.convergedValue, stableValue, 
                        "Value should remain stable across checks");
            }
            
            logger.info("Stability operation {} completed and validated: value={}", 
                    operation, convergenceResult.convergedValue);
            
            // Brief pause between operations
            Thread.sleep(3000);
        }
        
        // Validate overall stability trend
        for (int i = 1; i < stabilitySnapshots.size(); i++) {
            assertEquals(stabilitySnapshots.get(i - 1) + 1, stabilitySnapshots.get(i).longValue(),
                    "Each operation should increment count by exactly 1");
        }
        
        // Final long-term stability check
        logger.info("Performing final long-term stability validation...");
        Thread.sleep(15000); // Extended wait
        
        Map<String, Long> finalCounts = getAllNodeCounts();
        Long finalStableValue = getConsistentValue(finalCounts);
        assertNotNull(finalStableValue, "System should maintain long-term stability");
        assertEquals(stabilitySnapshots.get(stabilitySnapshots.size() - 1), finalStableValue,
                "Final value should match last operation result");
        
        logger.info("Long-term stability convergence validation completed successfully");
        logger.info("Stability progression: {}", stabilitySnapshots);
    }
    
    @AfterEach
    void tearDown() {
        // Log convergence metrics summary
        if (!convergenceMetrics.isEmpty()) {
            logger.info("Convergence metrics summary:");
            convergenceMetrics.forEach(metric -> 
                logger.info("  {}: converged to {} in {}ms with {} nodes ({} checks)", 
                        metric.testName, metric.convergedValue, metric.convergenceTimeMs, 
                        metric.participatingNodes, metric.consistencyChecks));
            
            // Calculate average convergence time
            double avgConvergenceTime = convergenceMetrics.stream()
                    .mapToLong(m -> m.convergenceTimeMs)
                    .average()
                    .orElse(0.0);
            
            logger.info("Average convergence time: {:.2f}ms", avgConvergenceTime);
        }
    }
    
    // Helper methods
    
    private ConvergenceResult waitForConvergence(long expectedValue, int timeoutSeconds) throws Exception {
        logger.debug("Waiting for convergence to value {} with timeout {}s", expectedValue, timeoutSeconds);
        
        Instant convergenceStart = Instant.now();
        int consistencyChecks = 0;
        
        for (int attempt = 0; attempt < timeoutSeconds * 2; attempt++) { // Check every 500ms
            Thread.sleep(500);
            consistencyChecks++;
            
            Map<String, Long> counts = getAllNodeCounts();
            Long consistentValue = getConsistentValue(counts);
            
            if (consistentValue != null && consistentValue.equals(expectedValue)) {
                Instant convergenceEnd = Instant.now();
                long convergenceTime = Duration.between(convergenceStart, convergenceEnd).toMillis();
                
                logger.debug("Convergence achieved: value={}, time={}ms, nodes={}, checks={}", 
                        consistentValue, convergenceTime, counts.size(), consistencyChecks);
                
                return new ConvergenceResult(
                        true,
                        consistentValue,
                        convergenceTime,
                        counts.size(),
                        consistencyChecks
                );
            }
            
            if (attempt % 20 == 0) { // Log every 10 seconds
                logger.debug("Convergence attempt {}: current counts={}, expected={}", 
                        attempt, counts, expectedValue);
            }
        }
        
        // Convergence failed
        Map<String, Long> finalCounts = getAllNodeCounts();
        logger.warn("Convergence failed after {}s: final counts={}, expected={}", 
                timeoutSeconds, finalCounts, expectedValue);
        
        return new ConvergenceResult(false, null, 0, finalCounts.size(), consistencyChecks);
    }
    
    private void waitForSystemReady() {
        logger.info("Waiting for convergence validation system to be ready...");
        
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                Map<String, Long> counts = getAllNodeCounts();
                if (counts.size() == NODE_IDS.length) {
                    Long consistentValue = getConsistentValue(counts);
                    if (consistentValue != null) {
                        logger.info("Convergence validation system is ready with consistent value: {}", consistentValue);
                        return;
                    }
                }
                
                Thread.sleep(3000);
            } catch (Exception e) {
                logger.debug("System not ready yet: {}", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for system", ie);
                }
            }
        }
        
        throw new RuntimeException("Convergence validation system did not become ready within timeout");
    }
    
    private void resetSystemToKnownState() {
        logger.info("Resetting system to known state for convergence validation");
        
        try {
            // Send recovery requests to all nodes to ensure consistent starting state
            for (String nodeId : NODE_IDS) {
                try {
                    ConsensusRequest resetRequest = new ConsensusRequest(
                            MessageType.RECOVERY_REQUEST,
                            "convergence-reset",
                            nodeId,
                            null,
                            UUID.randomUUID().toString(),
                            Map.of("reset", true)
                    );
                    sendRequestToNode(nodeId, resetRequest);
                } catch (Exception e) {
                    logger.warn("Could not reset node {}: {}", nodeId, e.getMessage());
                }
            }
            
            Thread.sleep(10000); // Allow reset to complete
            
            // Validate consistent starting state
            Map<String, Long> initialCounts = getAllNodeCounts();
            Long initialValue = getConsistentValue(initialCounts);
            
            if (initialValue != null) {
                logger.info("System reset to consistent state: {}", initialValue);
            } else {
                logger.warn("System reset but not fully consistent: {}", initialCounts);
            }
            
        } catch (Exception e) {
            logger.warn("System reset encountered issues: {}", e.getMessage());
        }
    }
    
    private int getCurrentExpectedCount() {
        try {
            Map<String, Long> counts = getAllNodeCounts();
            Long consistentValue = getConsistentValue(counts);
            return consistentValue != null ? consistentValue.intValue() : 0;
        } catch (Exception e) {
            logger.warn("Could not get current expected count: {}", e.getMessage());
            return 0;
        }
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
                "convergence-validation-client",
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
                logger.debug("Could not get count from {}: {}", nodeId, e.getMessage());
            }
        }
        
        return counts;
    }
    
    private String getActiveNodeExcluding(String excludedNode) {
        return Arrays.stream(NODE_IDS)
                .filter(nodeId -> !nodeId.equals(excludedNode))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No active nodes available"));
    }
    
    private Long getConsistentValue(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return null;
        }
        
        Set<Long> uniqueValues = new HashSet<>(counts.values());
        
        if (uniqueValues.size() == 1) {
            return uniqueValues.iterator().next();
        }
        
        return null; // Not consistent
    }
    
    // Result classes
    
    private static class ConvergenceResult {
        final boolean converged;
        final Long convergedValue;
        final long convergenceTime;
        final int participatingNodes;
        final int consistencyChecks;
        
        ConvergenceResult(boolean converged, Long convergedValue, long convergenceTime, 
                         int participatingNodes, int consistencyChecks) {
            this.converged = converged;
            this.convergedValue = convergedValue;
            this.convergenceTime = convergenceTime;
            this.participatingNodes = participatingNodes;
            this.consistencyChecks = consistencyChecks;
        }
    }
    
    private static class ConvergenceMetric {
        final String testName;
        final long totalDurationMs;
        final long convergenceTimeMs;
        final Long convergedValue;
        final int participatingNodes;
        final int consistencyChecks;
        
        ConvergenceMetric(String testName, long totalDurationMs, long convergenceTimeMs, 
                         Long convergedValue, int participatingNodes, int consistencyChecks) {
            this.testName = testName;
            this.totalDurationMs = totalDurationMs;
            this.convergenceTimeMs = convergenceTimeMs;
            this.convergedValue = convergedValue;
            this.participatingNodes = participatingNodes;
            this.consistencyChecks = consistencyChecks;
        }
    }
}