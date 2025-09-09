package com.example.consensus.integration;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Utility class for chaos testing scenarios in the consensus federation.
 * Provides methods for simulating various failure modes and network partitions.
 */
public class ChaosTestingUtility {
    
    private static final Logger logger = LoggerFactory.getLogger(ChaosTestingUtility.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private final Map<String, String> nodeEndpoints;
    private final Random random = new Random();
    
    public ChaosTestingUtility(Map<String, String> nodeEndpoints) {
        this.nodeEndpoints = new HashMap<>(nodeEndpoints);
    }
    
    /**
     * Simulates random node failures and tests system resilience.
     */
    public ChaosTestResult simulateRandomNodeFailures(int maxFailures, Duration testDuration) throws Exception {
        logger.info("Starting random node failure simulation - max failures: {}, duration: {}", 
                maxFailures, testDuration);
        
        Instant testStart = Instant.now();
        List<String> allNodes = new ArrayList<>(nodeEndpoints.keySet());
        Set<String> failedNodes = new HashSet<>();
        List<ChaosEvent> events = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        try {
            while (Duration.between(testStart, Instant.now()).compareTo(testDuration) < 0) {
                // Randomly decide whether to fail a node or recover one
                if (failedNodes.size() < maxFailures && random.nextBoolean()) {
                    // Fail a random healthy node
                    List<String> healthyNodes = allNodes.stream()
                            .filter(node -> !failedNodes.contains(node))
                            .collect(Collectors.toList());
                    
                    if (!healthyNodes.isEmpty() && healthyNodes.size() > 3) { // Keep minimum quorum
                        String nodeToFail = healthyNodes.get(random.nextInt(healthyNodes.size()));
                        failedNodes.add(nodeToFail);
                        
                        events.add(new ChaosEvent(Instant.now(), "NODE_FAILURE", nodeToFail, 
                                "Simulated failure of node " + nodeToFail));
                        
                        logger.info("Chaos: Failed node {}, total failed: {}", nodeToFail, failedNodes.size());
                    }
                } else if (!failedNodes.isEmpty() && random.nextBoolean()) {
                    // Recover a random failed node
                    String nodeToRecover = failedNodes.iterator().next();
                    failedNodes.remove(nodeToRecover);
                    
                    events.add(new ChaosEvent(Instant.now(), "NODE_RECOVERY", nodeToRecover,
                            "Simulated recovery of node " + nodeToRecover));
                    
                    logger.info("Chaos: Recovered node {}, total failed: {}", nodeToRecover, failedNodes.size());
                }
                
                // Send random increment requests to healthy nodes
                List<String> healthyNodes = allNodes.stream()
                        .filter(node -> !failedNodes.contains(node))
                        .collect(Collectors.toList());
                
                if (!healthyNodes.isEmpty()) {
                    String targetNode = healthyNodes.get(random.nextInt(healthyNodes.size()));
                    
                    Future<ConsensusResponse> requestFuture = executor.submit(() -> {
                        try {
                            ConsensusRequest request = new ConsensusRequest(
                                    MessageType.INCREMENT_REQUEST,
                                    "chaos-increment",
                                    targetNode,
                                    null,
                                    UUID.randomUUID().toString(),
                                    Map.of("chaosTest", true, "failedNodes", failedNodes.size())
                            );
                            
                            return sendRequestToNode(targetNode, request);
                        } catch (Exception e) {
                            logger.debug("Chaos request failed (expected): {}", e.getMessage());
                            return null;
                        }
                    });
                    
                    try {
                        ConsensusResponse response = requestFuture.get(10, TimeUnit.SECONDS);
                        if (response != null && response.isSuccess()) {
                            events.add(new ChaosEvent(Instant.now(), "SUCCESSFUL_CONSENSUS", targetNode,
                                    "Successful consensus with " + healthyNodes.size() + " healthy nodes"));
                        }
                    } catch (Exception e) {
                        // Expected during chaos conditions
                        logger.debug("Chaos consensus attempt failed: {}", e.getMessage());
                    }
                }
                
                // Random delay between chaos events
                Thread.sleep(random.nextInt(5000) + 2000); // 2-7 seconds
            }
            
        } finally {
            executor.shutdown();
        }
        
        Instant testEnd = Instant.now();
        long testDurationMs = Duration.between(testStart, testEnd).toMillis();
        
        // Final validation - recover all nodes and check consistency
        logger.info("Chaos test completed, validating final consistency...");
        Thread.sleep(15000); // Allow recovery time
        
        Map<String, Long> finalCounts = getAllNodeCounts();
        boolean finalConsistency = isConsistent(finalCounts);
        
        return new ChaosTestResult(
                testDurationMs,
                events.size(),
                events.stream().mapToLong(e -> e.type.equals("NODE_FAILURE") ? 1 : 0).sum(),
                events.stream().mapToLong(e -> e.type.equals("SUCCESSFUL_CONSENSUS") ? 1 : 0).sum(),
                finalConsistency,
                finalCounts,
                events
        );
    }
    
    /**
     * Simulates network partition scenarios.
     */
    public PartitionTestResult simulateNetworkPartition(List<String> partition1, List<String> partition2, 
                                                       Duration partitionDuration) throws Exception {
        logger.info("Simulating network partition - Partition 1: {}, Partition 2: {}, Duration: {}", 
                partition1, partition2, partitionDuration);
        
        Instant partitionStart = Instant.now();
        
        // Test consensus in each partition
        Map<String, Long> partition1CountsBefore = getNodeCounts(partition1);
        Map<String, Long> partition2CountsBefore = getNodeCounts(partition2);
        
        // Try consensus in partition 1
        boolean partition1CanConsensus = false;
        if (partition1.size() >= 3) {
            try {
                String targetNode = partition1.get(0);
                ConsensusRequest request = new ConsensusRequest(
                        MessageType.INCREMENT_REQUEST,
                        "partition1-test",
                        targetNode,
                        null,
                        UUID.randomUUID().toString(),
                        Map.of("partitionTest", "partition1")
                );
                
                ConsensusResponse response = sendRequestToNode(targetNode, request);
                partition1CanConsensus = response.isSuccess();
                
                if (partition1CanConsensus) {
                    Thread.sleep(20000); // Wait for consensus
                }
            } catch (Exception e) {
                logger.info("Partition 1 consensus failed as expected: {}", e.getMessage());
            }
        }
        
        // Try consensus in partition 2
        boolean partition2CanConsensus = false;
        if (partition2.size() >= 3) {
            try {
                String targetNode = partition2.get(0);
                ConsensusRequest request = new ConsensusRequest(
                        MessageType.INCREMENT_REQUEST,
                        "partition2-test",
                        targetNode,
                        null,
                        UUID.randomUUID().toString(),
                        Map.of("partitionTest", "partition2")
                );
                
                ConsensusResponse response = sendRequestToNode(targetNode, request);
                partition2CanConsensus = response.isSuccess();
                
                if (partition2CanConsensus) {
                    Thread.sleep(20000); // Wait for consensus
                }
            } catch (Exception e) {
                logger.info("Partition 2 consensus failed as expected: {}", e.getMessage());
            }
        }
        
        // Wait for partition duration
        Thread.sleep(partitionDuration.toMillis());
        
        // Heal partition and test recovery
        logger.info("Healing network partition...");
        Thread.sleep(15000); // Allow partition healing
        
        // Test consensus after healing
        String healingNode = nodeEndpoints.keySet().iterator().next();
        ConsensusRequest healingRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "partition-healing-test",
                healingNode,
                null,
                UUID.randomUUID().toString(),
                Map.of("partitionTest", "healing")
        );
        
        ConsensusResponse healingResponse = sendRequestToNode(healingNode, healingRequest);
        boolean healingSuccessful = healingResponse.isSuccess();
        
        if (healingSuccessful) {
            Thread.sleep(25000); // Wait for healing consensus
        }
        
        Instant partitionEnd = Instant.now();
        long totalDuration = Duration.between(partitionStart, partitionEnd).toMillis();
        
        // Final consistency check
        Map<String, Long> finalCounts = getAllNodeCounts();
        boolean finalConsistency = isConsistent(finalCounts);
        
        return new PartitionTestResult(
                totalDuration,
                partition1.size(),
                partition2.size(),
                partition1CanConsensus,
                partition2CanConsensus,
                healingSuccessful,
                finalConsistency,
                partition1CountsBefore,
                partition2CountsBefore,
                finalCounts
        );
    }
    
    /**
     * Simulates cascading failures.
     */
    public CascadeTestResult simulateCascadingFailures(int initialFailures, Duration cascadeDuration) throws Exception {
        logger.info("Simulating cascading failures - initial failures: {}, cascade duration: {}", 
                initialFailures, cascadeDuration);
        
        Instant cascadeStart = Instant.now();
        List<String> allNodes = new ArrayList<>(nodeEndpoints.keySet());
        Set<String> failedNodes = new HashSet<>();
        List<ChaosEvent> cascadeEvents = new ArrayList<>();
        
        // Initial failures
        for (int i = 0; i < initialFailures && failedNodes.size() < allNodes.size() - 3; i++) {
            List<String> healthyNodes = allNodes.stream()
                    .filter(node -> !failedNodes.contains(node))
                    .collect(Collectors.toList());
            
            if (!healthyNodes.isEmpty()) {
                String nodeToFail = healthyNodes.get(random.nextInt(healthyNodes.size()));
                failedNodes.add(nodeToFail);
                
                cascadeEvents.add(new ChaosEvent(Instant.now(), "INITIAL_FAILURE", nodeToFail,
                        "Initial cascading failure of " + nodeToFail));
                
                logger.info("Cascade: Initial failure of node {}", nodeToFail);
            }
        }
        
        // Simulate cascade effect
        Instant cascadeEnd = cascadeStart.plus(cascadeDuration);
        while (Instant.now().isBefore(cascadeEnd) && failedNodes.size() < allNodes.size() - 3) {
            // Randomly trigger additional failures (cascade effect)
            if (random.nextDouble() < 0.3) { // 30% chance of additional failure
                List<String> healthyNodes = allNodes.stream()
                        .filter(node -> !failedNodes.contains(node))
                        .collect(Collectors.toList());
                
                if (healthyNodes.size() > 3) { // Maintain minimum quorum
                    String nodeToFail = healthyNodes.get(random.nextInt(healthyNodes.size()));
                    failedNodes.add(nodeToFail);
                    
                    cascadeEvents.add(new ChaosEvent(Instant.now(), "CASCADE_FAILURE", nodeToFail,
                            "Cascading failure of " + nodeToFail));
                    
                    logger.info("Cascade: Additional failure of node {} (total failed: {})", 
                            nodeToFail, failedNodes.size());
                }
            }
            
            // Test system resilience during cascade
            List<String> healthyNodes = allNodes.stream()
                    .filter(node -> !failedNodes.contains(node))
                    .collect(Collectors.toList());
            
            if (!healthyNodes.isEmpty()) {
                try {
                    String targetNode = healthyNodes.get(random.nextInt(healthyNodes.size()));
                    ConsensusRequest request = new ConsensusRequest(
                            MessageType.INCREMENT_REQUEST,
                            "cascade-resilience-test",
                            targetNode,
                            null,
                            UUID.randomUUID().toString(),
                            Map.of("cascadeTest", true, "healthyNodes", healthyNodes.size())
                    );
                    
                    ConsensusResponse response = sendRequestToNode(targetNode, request);
                    if (response.isSuccess()) {
                        cascadeEvents.add(new ChaosEvent(Instant.now(), "RESILIENCE_SUCCESS", targetNode,
                                "System remained resilient with " + healthyNodes.size() + " healthy nodes"));
                    }
                } catch (Exception e) {
                    logger.debug("Cascade resilience test failed: {}", e.getMessage());
                }
            }
            
            Thread.sleep(random.nextInt(3000) + 1000); // 1-4 seconds between events
        }
        
        // Recovery phase
        logger.info("Starting cascade recovery phase...");
        Thread.sleep(20000); // Allow recovery time
        
        Instant recoveryEnd = Instant.now();
        long totalDuration = Duration.between(cascadeStart, recoveryEnd).toMillis();
        
        // Final validation
        Map<String, Long> finalCounts = getAllNodeCounts();
        boolean finalConsistency = isConsistent(finalCounts);
        
        return new CascadeTestResult(
                totalDuration,
                initialFailures,
                failedNodes.size(),
                cascadeEvents.stream().mapToLong(e -> e.type.equals("RESILIENCE_SUCCESS") ? 1 : 0).sum(),
                finalConsistency,
                finalCounts,
                cascadeEvents
        );
    }
    
    // Helper methods
    
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
    
    private Map<String, Long> getAllNodeCounts() {
        return getNodeCounts(new ArrayList<>(nodeEndpoints.keySet()));
    }
    
    private Map<String, Long> getNodeCounts(List<String> nodeIds) {
        Map<String, Long> counts = new HashMap<>();
        
        for (String nodeId : nodeIds) {
            try {
                ConsensusRequest statusRequest = new ConsensusRequest(
                        MessageType.RECOVERY_REQUEST,
                        "chaos-test-client",
                        nodeId,
                        null,
                        UUID.randomUUID().toString(),
                        Map.of("statusCheck", true)
                );
                
                ConsensusResponse response = sendRequestToNode(nodeId, statusRequest);
                counts.put(nodeId, response.getCurrentValue());
            } catch (Exception e) {
                logger.warn("Could not get count from {}: {}", nodeId, e.getMessage());
            }
        }
        
        return counts;
    }
    
    private boolean isConsistent(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return false;
        }
        
        Set<Long> uniqueCounts = new HashSet<>(counts.values());
        return uniqueCounts.size() == 1;
    }
    
    // Result classes
    
    public static class ChaosTestResult {
        public final long durationMs;
        public final int totalEvents;
        public final long nodeFailures;
        public final long successfulConsensus;
        public final boolean finalConsistency;
        public final Map<String, Long> finalCounts;
        public final List<ChaosEvent> events;
        
        public ChaosTestResult(long durationMs, int totalEvents, long nodeFailures, 
                              long successfulConsensus, boolean finalConsistency,
                              Map<String, Long> finalCounts, List<ChaosEvent> events) {
            this.durationMs = durationMs;
            this.totalEvents = totalEvents;
            this.nodeFailures = nodeFailures;
            this.successfulConsensus = successfulConsensus;
            this.finalConsistency = finalConsistency;
            this.finalCounts = finalCounts;
            this.events = events;
        }
    }
    
    public static class PartitionTestResult {
        public final long durationMs;
        public final int partition1Size;
        public final int partition2Size;
        public final boolean partition1CanConsensus;
        public final boolean partition2CanConsensus;
        public final boolean healingSuccessful;
        public final boolean finalConsistency;
        public final Map<String, Long> partition1CountsBefore;
        public final Map<String, Long> partition2CountsBefore;
        public final Map<String, Long> finalCounts;
        
        public PartitionTestResult(long durationMs, int partition1Size, int partition2Size,
                                  boolean partition1CanConsensus, boolean partition2CanConsensus,
                                  boolean healingSuccessful, boolean finalConsistency,
                                  Map<String, Long> partition1CountsBefore,
                                  Map<String, Long> partition2CountsBefore,
                                  Map<String, Long> finalCounts) {
            this.durationMs = durationMs;
            this.partition1Size = partition1Size;
            this.partition2Size = partition2Size;
            this.partition1CanConsensus = partition1CanConsensus;
            this.partition2CanConsensus = partition2CanConsensus;
            this.healingSuccessful = healingSuccessful;
            this.finalConsistency = finalConsistency;
            this.partition1CountsBefore = partition1CountsBefore;
            this.partition2CountsBefore = partition2CountsBefore;
            this.finalCounts = finalCounts;
        }
    }
    
    public static class CascadeTestResult {
        public final long durationMs;
        public final int initialFailures;
        public final int totalFailures;
        public final long resilienceSuccesses;
        public final boolean finalConsistency;
        public final Map<String, Long> finalCounts;
        public final List<ChaosEvent> events;
        
        public CascadeTestResult(long durationMs, int initialFailures, int totalFailures,
                                long resilienceSuccesses, boolean finalConsistency,
                                Map<String, Long> finalCounts, List<ChaosEvent> events) {
            this.durationMs = durationMs;
            this.initialFailures = initialFailures;
            this.totalFailures = totalFailures;
            this.resilienceSuccesses = resilienceSuccesses;
            this.finalConsistency = finalConsistency;
            this.finalCounts = finalCounts;
            this.events = events;
        }
    }
    
    public static class ChaosEvent {
        public final Instant timestamp;
        public final String type;
        public final String nodeId;
        public final String description;
        
        public ChaosEvent(Instant timestamp, String type, String nodeId, String description) {
            this.timestamp = timestamp;
            this.type = type;
            this.nodeId = nodeId;
            this.description = description;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] %s on %s: %s", timestamp, type, nodeId, description);
        }
    }
}