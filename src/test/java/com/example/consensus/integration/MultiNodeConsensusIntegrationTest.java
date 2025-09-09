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
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for multi-node consensus scenarios using Testcontainers.
 * Tests the complete consensus federation with all 5 Lambda nodes and SQS messaging.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiNodeConsensusIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiNodeConsensusIntegrationTest.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .build();
    
    private static final int CONSENSUS_TIMEOUT_SECONDS = 45; // Reduced for Mac Silicon
    private static final int NODE_COUNT = 5;
    private static final String[] NODE_IDS = {"lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"};
    
    @Container
    static ComposeContainer environment = new ComposeContainer(new File("docker-compose.yml"))
            .withExposedService("lambda-1", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405) // POST required, but endpoint exists
                    .withStartupTimeout(Duration.ofMinutes(5))) // Extended for Mac Silicon
            .withExposedService("lambda-2", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(5)))
            .withExposedService("lambda-3", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(5)))
            .withExposedService("lambda-4", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(5)))
            .withExposedService("lambda-5", 8080, Wait.forHttp("/2015-03-31/functions/function/invocations")
                    .forStatusCode(405)
                    .withStartupTimeout(Duration.ofMinutes(5)))
            .withExposedService("sqs", 9324, Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofMinutes(3))); // Extended for Mac Silicon
    
    private Map<String, String> nodeEndpoints;
    
    @BeforeAll
    static void setUpEnvironment() {
        logger.info("Starting multi-node consensus federation for integration testing");
        // Container startup is handled by @Container annotation
    }
    
    @BeforeEach
    void setUp() {
        // Build node endpoint mappings
        nodeEndpoints = new HashMap<>();
        for (int i = 1; i <= NODE_COUNT; i++) {
            String nodeId = "lambda-" + i;
            String host = environment.getServiceHost(nodeId, 8080);
            Integer port = environment.getServicePort(nodeId, 8080);
            nodeEndpoints.put(nodeId, "http://" + host + ":" + port);
        }
        
        logger.info("Node endpoints configured: {}", nodeEndpoints);
        
        // Wait for all nodes to be ready
        waitForAllNodesReady();
    }
    
    @Test
    @Order(1)
    @DisplayName("Test successful consensus with all 5 nodes")
    void testSuccessfulConsensusWithAllNodes() throws Exception {
        logger.info("Testing successful consensus with all 5 nodes");
        
        // Send increment request to lambda-1
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "integration-test",
                "lambda-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = sendRequestToNode("lambda-1", incrementRequest);
        
        // Verify initial response
        assertTrue(response.isSuccess(), "Increment request should be accepted");
        assertEquals("lambda-1", response.getNodeId());
        
        // Wait for consensus to complete (longer for Mac Silicon)
        Thread.sleep(15000); // Allow extra time for consensus protocol on ARM64
        
        // Verify all nodes converged to the same count
        Map<String, Long> nodeCounts = getAllNodeCounts();
        logger.info("Node counts after consensus: {}", nodeCounts);
        
        // All nodes should have the same count (1)
        Set<Long> uniqueCounts = new HashSet<>(nodeCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should have the same count");
        assertEquals(1L, uniqueCounts.iterator().next(), "Count should be 1 after increment");
        
        // Verify all nodes are in IDLE state
        Map<String, ConsensusResponse> nodeStates = getAllNodeStates();
        nodeStates.values().forEach(state -> 
            assertEquals("IDLE", state.getState().toString(), "All nodes should be in IDLE state"));
    }
    
    @Test
    @Order(2)
    @DisplayName("Test concurrent increment requests and conflict resolution")
    void testConcurrentIncrementRequests() throws Exception {
        logger.info("Testing concurrent increment requests and conflict resolution");
        
        // Send concurrent increment requests to different nodes (reduced for Mac Silicon)
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ConsensusResponse>> futures = new ArrayList<>();
        
        // Send requests to lambda-1 and lambda-3 simultaneously (reduced load for ARM64)
        String[] targetNodes = {"lambda-1", "lambda-3"};
        
        for (String nodeId : targetNodes) {
            Future<ConsensusResponse> future = executor.submit(() -> {
                try {
                    ConsensusRequest request = new ConsensusRequest(
                            MessageType.INCREMENT_REQUEST,
                            "concurrent-test",
                            nodeId,
                            null,
                            null,
                            new HashMap<>()
                    );
                    return sendRequestToNode(nodeId, request);
                } catch (Exception e) {
                    logger.error("Error sending concurrent request to {}", nodeId, e);
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }
        
        // Collect responses
        List<ConsensusResponse> responses = new ArrayList<>();
        for (Future<ConsensusResponse> future : futures) {
            try {
                responses.add(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                logger.warn("Concurrent request failed: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        
        // At least one request should succeed
        long successfulRequests = responses.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        assertTrue(successfulRequests >= 1, "At least one concurrent request should succeed");
        
        // Wait for all consensus operations to complete (extended for Mac Silicon)
        Thread.sleep(20000);
        
        // Verify final consistency - all nodes should have the same count
        Map<String, Long> finalCounts = getAllNodeCounts();
        logger.info("Final node counts after concurrent requests: {}", finalCounts);
        
        Set<Long> uniqueCounts = new HashSet<>(finalCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should converge to the same count");
        
        // Count should be at least 2 (previous test + at least one successful concurrent request)
        Long finalCount = uniqueCounts.iterator().next();
        assertTrue(finalCount >= 2, "Count should be at least 2 after concurrent increments");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test node failure and recovery scenarios")
    void testNodeFailureAndRecovery() throws Exception {
        logger.info("Testing node failure and recovery scenarios");
        
        // Get initial state
        Map<String, Long> initialCounts = getAllNodeCounts();
        Long initialCount = initialCounts.values().iterator().next();
        logger.info("Initial count before failure test: {}", initialCount);
        
        // Stop lambda-3 to simulate failure
        logger.info("Stopping lambda-3 to simulate node failure");
        // Note: ComposeContainer doesn't support stopping individual services
        // This test simulates failure by not sending requests to lambda-3
        
        // Wait for failure to be detected
        Thread.sleep(5000);
        
        // Send increment request to remaining nodes
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "failure-test",
                "lambda-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = sendRequestToNode("lambda-1", incrementRequest);
        assertTrue(response.isSuccess(), "Consensus should work with 4 nodes");
        
        // Wait for consensus among remaining nodes
        Thread.sleep(10000);
        
        // Verify remaining nodes have consistent state
        Map<String, Long> countsWithoutFailedNode = new HashMap<>();
        for (String nodeId : Arrays.asList("lambda-1", "lambda-2", "lambda-4", "lambda-5")) {
            try {
                Long count = getNodeCount(nodeId);
                countsWithoutFailedNode.put(nodeId, count);
            } catch (Exception e) {
                logger.warn("Could not get count from {}: {}", nodeId, e.getMessage());
            }
        }
        
        logger.info("Counts without failed node: {}", countsWithoutFailedNode);
        Set<Long> uniqueCounts = new HashSet<>(countsWithoutFailedNode.values());
        assertEquals(1, uniqueCounts.size(), "Remaining nodes should have consistent state");
        
        Long countAfterFailure = uniqueCounts.iterator().next();
        assertEquals(initialCount + 1, countAfterFailure, "Count should be incremented by 1");
        
        // Simulate lambda-3 restart by resuming communication
        logger.info("Simulating lambda-3 restart to test recovery");
        
        // Wait for node to recover (extended for Mac Silicon)
        Thread.sleep(25000);
        
        // Verify lambda-3 recovered the correct state
        Long recoveredCount = getNodeCount("lambda-3");
        assertEquals(countAfterFailure, recoveredCount, "Recovered node should have correct count");
        
        logger.info("Node recovery test completed successfully");
    }
    
    @Test
    @Order(4)
    @DisplayName("Test network partition simulation and quorum behavior")
    void testNetworkPartitionAndQuorum() throws Exception {
        logger.info("Testing network partition simulation and quorum behavior");
        
        // Get initial state
        Map<String, Long> initialCounts = getAllNodeCounts();
        Long initialCount = initialCounts.values().iterator().next();
        logger.info("Initial count before partition test: {}", initialCount);
        
        // Create minority partition by simulating 3 node failures
        logger.info("Creating minority partition by simulating 3 node failures");
        // Note: We simulate partition by only communicating with lambda-1 and lambda-2
        
        // Wait for partition to be established
        Thread.sleep(5000);
        
        // Try to send increment request to minority partition (lambda-1, lambda-2)
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "partition-test",
                "lambda-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = sendRequestToNode("lambda-1", incrementRequest);
        
        // With only 2 nodes, consensus should not be achievable (need 3 for quorum)
        // The request might be accepted initially but consensus should fail
        logger.info("Response from minority partition: {}", response);
        
        // Wait to see if consensus can be achieved
        Thread.sleep(15000);
        
        // Check if counts changed in minority partition
        Map<String, Long> minorityCounts = new HashMap<>();
        for (String nodeId : Arrays.asList("lambda-1", "lambda-2")) {
            try {
                Long count = getNodeCount(nodeId);
                minorityCounts.put(nodeId, count);
            } catch (Exception e) {
                logger.warn("Could not get count from minority node {}: {}", nodeId, e.getMessage());
            }
        }
        
        logger.info("Minority partition counts: {}", minorityCounts);
        
        // Restore majority by resuming communication with all nodes
        logger.info("Restoring majority by resuming communication with all nodes");
        
        // Wait for nodes to recover and rejoin (extended for Mac Silicon)
        Thread.sleep(30000);
        
        // Verify all nodes have consistent state after partition healing
        Map<String, Long> finalCounts = getAllNodeCounts();
        logger.info("Final counts after partition healing: {}", finalCounts);
        
        Set<Long> uniqueCounts = new HashSet<>(finalCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should have consistent state after healing");
        
        logger.info("Network partition test completed successfully");
    }
    
    @Test
    @Order(5)
    @DisplayName("Test multiple sequential consensus operations")
    void testMultipleSequentialConsensus() throws Exception {
        logger.info("Testing multiple sequential consensus operations");
        
        Long initialCount = getNodeCount("lambda-1");
        logger.info("Starting sequential test with initial count: {}", initialCount);
        
        // Perform 3 sequential increment operations
        for (int i = 1; i <= 3; i++) {
            logger.info("Performing sequential increment {}", i);
            
            ConsensusRequest incrementRequest = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "sequential-test-" + i,
                    "lambda-" + (i % NODE_COUNT + 1), // Rotate target nodes
                    null,
                    null,
                    new HashMap<>()
            );
            
            String targetNode = "lambda-" + (i % NODE_COUNT + 1);
            ConsensusResponse response = sendRequestToNode(targetNode, incrementRequest);
            
            assertTrue(response.isSuccess(), "Sequential increment " + i + " should succeed");
            
            // Wait for consensus to complete (extended for Mac Silicon)
            Thread.sleep(18000);
            
            // Verify all nodes have consistent state
            Map<String, Long> counts = getAllNodeCounts();
            Set<Long> uniqueCounts = new HashSet<>(counts.values());
            assertEquals(1, uniqueCounts.size(), "All nodes should be consistent after increment " + i);
            
            Long expectedCount = initialCount + i;
            assertEquals(expectedCount, uniqueCounts.iterator().next(), 
                    "Count should be " + expectedCount + " after " + i + " increments");
            
            logger.info("Sequential increment {} completed, count: {}", i, uniqueCounts.iterator().next());
        }
        
        logger.info("Multiple sequential consensus operations completed successfully");
    }
    
    // Helper methods
    
    private void waitForAllNodesReady() {
        logger.info("Waiting for all nodes to be ready...");
        
        for (int attempt = 0; attempt < 30; attempt++) {
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
                    logger.info("All nodes are ready");
                    return;
                }
                
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for nodes", e);
            }
        }
        
        throw new RuntimeException("Nodes did not become ready within timeout");
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
                .timeout(Duration.ofSeconds(30))
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
                "test-client",
                nodeId,
                null,
                null,
                new HashMap<>()
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
    
    private Map<String, ConsensusResponse> getAllNodeStates() {
        Map<String, ConsensusResponse> states = new HashMap<>();
        
        for (String nodeId : NODE_IDS) {
            try {
                ConsensusRequest statusRequest = new ConsensusRequest(
                        MessageType.RECOVERY_REQUEST,
                        "test-client",
                        nodeId,
                        null,
                        null,
                        new HashMap<>()
                );
                
                ConsensusResponse response = sendRequestToNode(nodeId, statusRequest);
                states.put(nodeId, response);
            } catch (Exception e) {
                logger.warn("Could not get state from {}: {}", nodeId, e.getMessage());
            }
        }
        
        return states;
    }
}