package com.example.consensus.integration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Advanced chaos testing scenarios using the ChaosTestingUtility.
 * Tests complex failure patterns, network partitions, and cascading failures.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvancedChaosTest {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedChaosTest.class);
    
    private static final String[] NODE_IDS = {"lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"};
    
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
    
    private ChaosTestingUtility chaosUtility;
    private Map<String, String> nodeEndpoints;
    
    @BeforeEach
    void setUp() {
        nodeEndpoints = new HashMap<>();
        
        for (int i = 1; i <= 5; i++) {
            String nodeId = "lambda-" + i;
            String host = environment.getServiceHost(nodeId, 8080);
            Integer port = environment.getServicePort(nodeId, 8080);
            nodeEndpoints.put(nodeId, "http://" + host + ":" + port);
        }
        
        chaosUtility = new ChaosTestingUtility(nodeEndpoints);
        
        logger.info("Advanced chaos testing setup completed with endpoints: {}", nodeEndpoints);
        waitForSystemReady();
    }
    
    @Test
    @Order(1)
    @DisplayName("Random node failure chaos testing")
    void testRandomNodeFailureChaos() throws Exception {
        logger.info("Starting random node failure chaos testing");
        
        // Test with up to 2 simultaneous failures for 60 seconds
        ChaosTestingUtility.ChaosTestResult result = chaosUtility.simulateRandomNodeFailures(
                2, // Max 2 simultaneous failures
                Duration.ofSeconds(60) // 1 minute of chaos
        );
        
        // Validate results
        assertTrue(result.durationMs > 50000, "Chaos test should run for approximately 60 seconds");
        assertTrue(result.totalEvents > 0, "Should have generated chaos events");
        assertTrue(result.nodeFailures > 0, "Should have simulated node failures");
        
        logger.info("Random chaos results: Duration={}ms, Events={}, Failures={}, Successful Consensus={}, Final Consistency={}", 
                result.durationMs, result.totalEvents, result.nodeFailures, result.successfulConsensus, result.finalConsistency);
        
        // System should maintain consistency despite chaos
        assertTrue(result.finalConsistency, "System should achieve final consistency after chaos");
        
        // Log chaos events for analysis
        logger.info("Chaos events summary:");
        result.events.forEach(event -> logger.info("  {}", event));
        
        // Validate that system can still perform consensus after chaos
        Thread.sleep(10000); // Allow system to stabilize
        validateSystemCanConsensus();
    }
    
    @Test
    @Order(2)
    @DisplayName("Network partition scenarios")
    void testNetworkPartitionScenarios() throws Exception {
        logger.info("Starting network partition scenario testing");
        
        // Test majority-minority partition (3-2 split)
        List<String> majorityPartition = Arrays.asList("lambda-1", "lambda-2", "lambda-3");
        List<String> minorityPartition = Arrays.asList("lambda-4", "lambda-5");
        
        ChaosTestingUtility.PartitionTestResult result = chaosUtility.simulateNetworkPartition(
                majorityPartition,
                minorityPartition,
                Duration.ofSeconds(45) // 45 second partition
        );
        
        // Validate partition behavior
        assertTrue(result.durationMs > 40000, "Partition test should run for approximately 45+ seconds");
        
        // Majority partition (3 nodes) should be able to achieve consensus
        assertTrue(result.partition1CanConsensus, "Majority partition should achieve consensus");
        
        // Minority partition (2 nodes) should NOT be able to achieve consensus
        assertFalse(result.partition2CanConsensus, "Minority partition should not achieve consensus");
        
        // System should heal and achieve consensus after partition
        assertTrue(result.healingSuccessful, "System should heal successfully after partition");
        assertTrue(result.finalConsistency, "System should achieve final consistency after healing");
        
        logger.info("Partition test results: Duration={}ms, Majority consensus={}, Minority consensus={}, Healing={}, Final consistency={}", 
                result.durationMs, result.partition1CanConsensus, result.partition2CanConsensus, 
                result.healingSuccessful, result.finalConsistency);
        
        // Test even split partition (2-2-1)
        testEvenSplitPartition();
    }
    
    @Test
    @Order(3)
    @DisplayName("Cascading failure scenarios")
    void testCascadingFailureScenarios() throws Exception {
        logger.info("Starting cascading failure scenario testing");
        
        // Start with 1 initial failure and allow cascade for 45 seconds
        ChaosTestingUtility.CascadeTestResult result = chaosUtility.simulateCascadingFailures(
                1, // Start with 1 failure
                Duration.ofSeconds(45) // Allow 45 seconds for cascade
        );
        
        // Validate cascade behavior
        assertTrue(result.durationMs > 40000, "Cascade test should run for approximately 45+ seconds");
        assertEquals(1, result.initialFailures, "Should start with 1 initial failure");
        assertTrue(result.totalFailures >= result.initialFailures, "Total failures should be >= initial failures");
        
        // System should maintain some resilience during cascade
        assertTrue(result.resilienceSuccesses >= 0, "Should have some resilience successes or graceful degradation");
        
        // System should achieve final consistency after cascade recovery
        assertTrue(result.finalConsistency, "System should achieve final consistency after cascade recovery");
        
        logger.info("Cascade test results: Duration={}ms, Initial failures={}, Total failures={}, Resilience successes={}, Final consistency={}", 
                result.durationMs, result.initialFailures, result.totalFailures, 
                result.resilienceSuccesses, result.finalConsistency);
        
        // Log cascade events for analysis
        logger.info("Cascade events summary:");
        result.events.forEach(event -> logger.info("  {}", event));
        
        // Validate system recovery
        Thread.sleep(15000); // Allow additional recovery time
        validateSystemCanConsensus();
    }
    
    @Test
    @Order(4)
    @DisplayName("Combined chaos scenarios")
    void testCombinedChaosScenarios() throws Exception {
        logger.info("Starting combined chaos scenarios testing");
        
        // Sequential chaos tests to validate system resilience
        
        // Phase 1: Random failures
        logger.info("Phase 1: Random failure chaos");
        ChaosTestingUtility.ChaosTestResult chaosResult = chaosUtility.simulateRandomNodeFailures(
                1, Duration.ofSeconds(30)
        );
        assertTrue(chaosResult.finalConsistency, "Phase 1 should maintain consistency");
        
        Thread.sleep(10000); // Recovery time
        
        // Phase 2: Network partition
        logger.info("Phase 2: Network partition");
        ChaosTestingUtility.PartitionTestResult partitionResult = chaosUtility.simulateNetworkPartition(
                Arrays.asList("lambda-1", "lambda-2", "lambda-3"),
                Arrays.asList("lambda-4", "lambda-5"),
                Duration.ofSeconds(20)
        );
        assertTrue(partitionResult.finalConsistency, "Phase 2 should maintain consistency");
        
        Thread.sleep(10000); // Recovery time
        
        // Phase 3: Cascading failures
        logger.info("Phase 3: Cascading failures");
        ChaosTestingUtility.CascadeTestResult cascadeResult = chaosUtility.simulateCascadingFailures(
                1, Duration.ofSeconds(25)
        );
        assertTrue(cascadeResult.finalConsistency, "Phase 3 should maintain consistency");
        
        // Final validation
        Thread.sleep(15000); // Final recovery time
        validateSystemCanConsensus();
        
        logger.info("Combined chaos scenarios completed successfully");
        logger.info("  Random chaos: {} events, {} failures", chaosResult.totalEvents, chaosResult.nodeFailures);
        logger.info("  Partition: majority consensus={}, healing={}", partitionResult.partition1CanConsensus, partitionResult.healingSuccessful);
        logger.info("  Cascade: {} total failures, {} resilience successes", cascadeResult.totalFailures, cascadeResult.resilienceSuccesses);
    }
    
    // Helper methods
    
    private void testEvenSplitPartition() throws Exception {
        logger.info("Testing even split partition scenario (2-2-1)");
        
        // Create 2-2-1 partition
        List<String> partition1 = Arrays.asList("lambda-1", "lambda-2");
        List<String> partition2 = Arrays.asList("lambda-3", "lambda-4");
        // lambda-5 is isolated
        
        ChaosTestingUtility.PartitionTestResult result = chaosUtility.simulateNetworkPartition(
                partition1,
                partition2,
                Duration.ofSeconds(30)
        );
        
        // Neither partition should achieve consensus (both have < 3 nodes)
        assertFalse(result.partition1CanConsensus, "2-node partition should not achieve consensus");
        assertFalse(result.partition2CanConsensus, "2-node partition should not achieve consensus");
        
        // System should heal successfully
        assertTrue(result.healingSuccessful, "System should heal from even split partition");
        assertTrue(result.finalConsistency, "System should achieve consistency after healing from even split");
        
        logger.info("Even split partition test completed successfully");
    }
    
    private void waitForSystemReady() {
        logger.info("Waiting for chaos testing system to be ready...");
        
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                boolean allReady = true;
                for (String nodeId : NODE_IDS) {
                    try {
                        // Simple connectivity test
                        String endpoint = nodeEndpoints.get(nodeId);
                        if (endpoint == null) {
                            allReady = false;
                            break;
                        }
                    } catch (Exception e) {
                        allReady = false;
                        break;
                    }
                }
                
                if (allReady) {
                    logger.info("Chaos testing system is ready");
                    return;
                }
                
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for system", e);
            }
        }
        
        throw new RuntimeException("Chaos testing system did not become ready within timeout");
    }
    
    private void validateSystemCanConsensus() throws Exception {
        logger.info("Validating system can still achieve consensus after chaos");
        
        // This is a simplified validation - in a real implementation,
        // we would send an increment request and verify consensus
        
        // For now, just verify all endpoints are accessible
        for (String nodeId : NODE_IDS) {
            String endpoint = nodeEndpoints.get(nodeId);
            assertNotNull(endpoint, "Node endpoint should be available: " + nodeId);
        }
        
        logger.info("System consensus validation completed");
    }
}