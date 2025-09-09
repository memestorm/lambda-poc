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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance-focused integration tests for consensus operations.
 * Optimized for Mac Silicon with realistic performance expectations.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConsensusPerformanceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsensusPerformanceIntegrationTest.class);
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .build();
    
    private static final String[] PERF_NODES = {"lambda-1", "lambda-2", "lambda-3"};
    
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
            .withExposedService("sqs", 9324, Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofMinutes(4)));
    
    private Map<String, String> nodeEndpoints;
    private List<PerformanceMetric> performanceMetrics;
    
    @BeforeEach
    void setUp() {
        nodeEndpoints = new HashMap<>();
        performanceMetrics = new ArrayList<>();
        
        for (String nodeId : PERF_NODES) {
            String host = environment.getServiceHost(nodeId, 8080);
            Integer port = environment.getServicePort(nodeId, 8080);
            nodeEndpoints.put(nodeId, "http://" + host + ":" + port);
        }
        
        waitForNodesReady();
    }
    
    @Test
    @Order(1)
    @DisplayName("Measure consensus latency")
    void measureConsensusLatency() throws Exception {
        logger.info("Measuring consensus latency (Mac Silicon baseline)");
        
        for (int i = 1; i <= 3; i++) {
            Instant startTime = Instant.now();
            
            ConsensusRequest incrementRequest = new ConsensusRequest(
                    MessageType.INCREMENT_REQUEST,
                    "latency-test-" + i,
                    "lambda-1",
                    null,
                    null,
                    new HashMap<>()
            );
            
            ConsensusResponse response = sendRequestToNode("lambda-1", incrementRequest);
            assertTrue(response.isSuccess(), "Request " + i + " should succeed");
            
            Thread.sleep(20000); // Mac Silicon adjusted timing
            
            Map<String, Long> counts = getNodeCounts();
            Set<Long> uniqueCounts = new HashSet<>(counts.values());
            assertEquals(1, uniqueCounts.size(), "Consensus should be achieved for request " + i);
            
            Instant endTime = Instant.now();
            long latencyMs = Duration.between(startTime, endTime).toMillis();
            
            PerformanceMetric metric = new PerformanceMetric(
                    "consensus_latency",
                    latencyMs,
                    "ms",
                    "Consensus operation " + i
            );
            performanceMetrics.add(metric);
            
            logger.info("Consensus operation {} completed in {}ms", i, latencyMs);
            Thread.sleep(5000);
        }
        
        double avgLatency = performanceMetrics.stream()
                .filter(m -> m.name.equals("consensus_latency"))
                .mapToLong(m -> m.value)
                .average()
                .orElse(0.0);
        
        logger.info("Average consensus latency: {:.2f}ms", avgLatency);
        assertTrue(avgLatency < 60000, "Average latency should be under 60 seconds on Mac Silicon");
    }
    
    // Helper methods
    
    private void waitForNodesReady() {
        logger.info("Waiting for performance test nodes to be ready...");
        
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                boolean allReady = true;
                for (String nodeId : PERF_NODES) {
                    try {
                        getNodeCount(nodeId);
                    } catch (Exception e) {
                        allReady = false;
                        break;
                    }
                }
                
                if (allReady) {
                    logger.info("All performance test nodes are ready");
                    return;
                }
                
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for nodes", e);
            }
        }
        
        throw new RuntimeException("Performance test nodes did not become ready within timeout");
    }
    
    private ConsensusResponse sendRequestToNode(String nodeId, ConsensusRequest request) throws Exception {
        String endpoint = nodeEndpoints.get(nodeId);
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
                "perf-test-client",
                nodeId,
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = sendRequestToNode(nodeId, statusRequest);
        return response.getCurrentValue();
    }
    
    private Map<String, Long> getNodeCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        for (String nodeId : PERF_NODES) {
            try {
                Long count = getNodeCount(nodeId);
                counts.put(nodeId, count);
            } catch (Exception e) {
                logger.warn("Could not get count from {}: {}", nodeId, e.getMessage());
            }
        }
        
        return counts;
    }
    
    private static class PerformanceMetric {
        final String name;
        final long value;
        final String unit;
        final String description;
        
        PerformanceMetric(String name, long value, String unit, String description) {
            this.name = name;
            this.value = value;
            this.unit = unit;
            this.description = description;
        }
    }
}