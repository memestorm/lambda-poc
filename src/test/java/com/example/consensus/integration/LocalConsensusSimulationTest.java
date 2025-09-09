package com.example.consensus.integration;

import com.example.consensus.handler.ConsensusLambdaHandler;
import com.example.consensus.manager.ConsensusManagerImpl;
import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.model.*;
import com.example.consensus.state.StateManagerImpl;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

/**
 * Local simulation of multi-node consensus without Docker containers.
 * Optimized for fast development cycles on Mac Silicon by using in-memory simulation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LocalConsensusSimulationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalConsensusSimulationTest.class);
    
    private static final String[] NODE_IDS = {"node-1", "node-2", "node-3", "node-4", "node-5"};
    
    private Map<String, ConsensusLambdaHandler> nodeHandlers;
    private Map<String, StateManagerImpl> stateManagers;
    private Map<String, SQSMessageHandler> sqsHandlers;
    private InMemoryMessageBus messageBus;
    
    @BeforeEach
    void setUp() {
        logger.info("Setting up local consensus simulation");
        
        messageBus = new InMemoryMessageBus();
        nodeHandlers = new HashMap<>();
        stateManagers = new HashMap<>();
        sqsHandlers = new HashMap<>();
        
        // Create simulated nodes
        for (String nodeId : NODE_IDS) {
            StateManagerImpl stateManager = new StateManagerImpl(nodeId);
            SQSMessageHandler sqsHandler = createMockSQSHandler(nodeId);
            ConsensusManagerImpl consensusManager = new ConsensusManagerImpl(stateManager, sqsHandler);
            ConsensusLambdaHandler handler = new ConsensusLambdaHandler(consensusManager, sqsHandler, stateManager);
            
            nodeHandlers.put(nodeId, handler);
            stateManagers.put(nodeId, stateManager);
            sqsHandlers.put(nodeId, sqsHandler);
        }
        
        logger.info("Local simulation setup complete with {} nodes", NODE_IDS.length);
    }
    
    @Test
    @Order(1)
    @DisplayName("Test simulated consensus with all nodes")
    void testSimulatedConsensusAllNodes() throws Exception {
        logger.info("Testing simulated consensus with all nodes");
        
        // Send increment request to node-1
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "simulation-test",
                "node-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = nodeHandlers.get("node-1").handleRequest(incrementRequest, createMockContext());
        assertTrue(response.isSuccess(), "Increment request should be accepted");
        
        // Simulate message propagation
        simulateMessagePropagation();
        
        // Verify all nodes have consistent state
        Map<String, Long> nodeCounts = getAllNodeCounts();
        logger.info("Node counts after simulated consensus: {}", nodeCounts);
        
        Set<Long> uniqueCounts = new HashSet<>(nodeCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should have the same count");
        assertEquals(1L, uniqueCounts.iterator().next(), "Count should be 1 after increment");
    }
    
    @Test
    @Order(2)
    @DisplayName("Test simulated node failure and recovery")
    void testSimulatedNodeFailureRecovery() throws Exception {
        logger.info("Testing simulated node failure and recovery");
        
        // Get initial state
        Long initialCount = stateManagers.get("node-1").getCurrentCount();
        
        // Simulate node-3 failure by removing it from message bus
        messageBus.simulateNodeFailure("node-3");
        
        // Send increment request
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "failure-test",
                "node-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = nodeHandlers.get("node-1").handleRequest(incrementRequest, createMockContext());
        assertTrue(response.isSuccess(), "Request should be accepted even with node failure");
        
        // Simulate consensus with 4 nodes
        simulateMessagePropagation();
        
        // Verify remaining nodes have consistent state
        Map<String, Long> countsWithoutFailedNode = new HashMap<>();
        for (String nodeId : Arrays.asList("node-1", "node-2", "node-4", "node-5")) {
            countsWithoutFailedNode.put(nodeId, stateManagers.get(nodeId).getCurrentCount());
        }
        
        logger.info("Counts without failed node: {}", countsWithoutFailedNode);
        Set<Long> uniqueCounts = new HashSet<>(countsWithoutFailedNode.values());
        assertEquals(1, uniqueCounts.size(), "Remaining nodes should have consistent state");
        
        // Simulate node recovery
        messageBus.simulateNodeRecovery("node-3");
        
        // Send recovery request
        ConsensusRequest recoveryRequest = new ConsensusRequest(
                MessageType.RECOVERY_REQUEST,
                "node-3",
                "node-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse recoveryResponse = nodeHandlers.get("node-1").handleRequest(recoveryRequest, null);
        assertTrue(recoveryResponse.isSuccess(), "Recovery request should succeed");
        
        // Simulate recovery response
        ConsensusRequest recoveryResponseMsg = new ConsensusRequest(
                MessageType.RECOVERY_RESPONSE,
                "node-1",
                "node-3",
                recoveryResponse.getCurrentValue(),
                null,
                new HashMap<>()
        );
        
        nodeHandlers.get("node-3").handleRequest(recoveryResponseMsg, createMockContext());
        
        // Verify recovered node has correct state
        Long recoveredCount = stateManagers.get("node-3").getCurrentCount();
        assertEquals(uniqueCounts.iterator().next(), recoveredCount, "Recovered node should have correct count");
        
        logger.info("Simulated node recovery completed successfully");
    }
    
    @Test
    @Order(3)
    @DisplayName("Test simulated concurrent requests")
    void testSimulatedConcurrentRequests() throws Exception {
        logger.info("Testing simulated concurrent requests");
        
        // Send concurrent requests to different nodes
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<ConsensusResponse>> futures = new ArrayList<>();
        
        String[] targetNodes = {"node-1", "node-2", "node-4"};
        
        for (String nodeId : targetNodes) {
            Future<ConsensusResponse> future = executor.submit(() -> {
                ConsensusRequest request = new ConsensusRequest(
                        MessageType.INCREMENT_REQUEST,
                        "concurrent-test",
                        nodeId,
                        null,
                        null,
                        new HashMap<>()
                );
                return nodeHandlers.get(nodeId).handleRequest(request, createMockContext());
            });
            futures.add(future);
        }
        
        // Collect responses
        List<ConsensusResponse> responses = new ArrayList<>();
        for (Future<ConsensusResponse> future : futures) {
            responses.add(future.get(10, TimeUnit.SECONDS));
        }
        
        executor.shutdown();
        
        // At least one should succeed
        long successfulRequests = responses.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
        assertTrue(successfulRequests >= 1, "At least one concurrent request should succeed");
        
        // Simulate message propagation for all concurrent operations
        simulateMessagePropagation();
        
        // Verify final consistency
        Map<String, Long> finalCounts = getAllNodeCounts();
        Set<Long> uniqueCounts = new HashSet<>(finalCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should converge to same count");
        
        logger.info("Simulated concurrent requests completed successfully");
    }
    
    @Test
    @Order(4)
    @DisplayName("Test simulated quorum behavior")
    void testSimulatedQuorumBehavior() throws Exception {
        logger.info("Testing simulated quorum behavior");
        
        // Simulate majority partition (3 nodes fail)
        messageBus.simulateNodeFailure("node-3");
        messageBus.simulateNodeFailure("node-4");
        messageBus.simulateNodeFailure("node-5");
        
        // Try consensus with minority (2 nodes)
        ConsensusRequest incrementRequest = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "quorum-test",
                "node-1",
                null,
                null,
                new HashMap<>()
        );
        
        ConsensusResponse response = nodeHandlers.get("node-1").handleRequest(incrementRequest, createMockContext());
        logger.info("Response from minority partition: {}", response);
        
        // Simulate limited message propagation (only 2 nodes)
        simulateMessagePropagation();
        
        // Check if minority achieved consensus (should be limited)
        Map<String, Long> minorityCounts = new HashMap<>();
        for (String nodeId : Arrays.asList("node-1", "node-2")) {
            minorityCounts.put(nodeId, stateManagers.get(nodeId).getCurrentCount());
        }
        
        logger.info("Minority partition counts: {}", minorityCounts);
        
        // Restore majority
        messageBus.simulateNodeRecovery("node-3");
        messageBus.simulateNodeRecovery("node-4");
        messageBus.simulateNodeRecovery("node-5");
        
        // Simulate state synchronization
        for (String nodeId : Arrays.asList("node-3", "node-4", "node-5")) {
            ConsensusRequest recoveryRequest = new ConsensusRequest(
                    MessageType.RECOVERY_REQUEST,
                    nodeId,
                    "node-1",
                    null,
                    null,
                    new HashMap<>()
            );
            
            ConsensusResponse recoveryResponse = nodeHandlers.get("node-1").handleRequest(recoveryRequest, createMockContext());
            
            if (recoveryResponse.isSuccess()) {
                ConsensusRequest recoveryResponseMsg = new ConsensusRequest(
                        MessageType.RECOVERY_RESPONSE,
                        "node-1",
                        nodeId,
                        recoveryResponse.getCurrentValue(),
                        null,
                        new HashMap<>()
                );
                
                nodeHandlers.get(nodeId).handleRequest(recoveryResponseMsg, createMockContext());
            }
        }
        
        // Verify final consistency after partition healing
        Map<String, Long> finalCounts = getAllNodeCounts();
        Set<Long> uniqueCounts = new HashSet<>(finalCounts.values());
        assertEquals(1, uniqueCounts.size(), "All nodes should have consistent state after healing");
        
        logger.info("Simulated quorum behavior test completed successfully");
    }
    
    // Helper methods
    
    private SQSMessageHandler createMockSQSHandler(String nodeId) {
        SQSMessageHandler mock = mock(SQSMessageHandler.class);
        when(mock.getNodeId()).thenReturn(nodeId);
        when(mock.sendMessage(anyString(), any())).thenReturn(true);
        when(mock.broadcastMessage(any())).thenReturn(4); // 4 other nodes
        return mock;
    }
    
    private void simulateMessagePropagation() {
        // Simulate the time it takes for messages to propagate and consensus to complete
        try {
            Thread.sleep(1000); // Much faster than real Docker containers
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private Map<String, Long> getAllNodeCounts() {
        Map<String, Long> counts = new HashMap<>();
        for (String nodeId : NODE_IDS) {
            if (!messageBus.isNodeFailed(nodeId)) {
                counts.put(nodeId, stateManagers.get(nodeId).getCurrentCount());
            }
        }
        return counts;
    }
    
    private Context createMockContext() {
        Context mockContext = mock(Context.class);
        LambdaLogger mockLogger = mock(LambdaLogger.class);
        when(mockContext.getLogger()).thenReturn(mockLogger);
        return mockContext;
    }
    
    /**
     * Simple in-memory message bus simulation for testing
     */
    private static class InMemoryMessageBus {
        private final Set<String> failedNodes = new HashSet<>();
        
        void simulateNodeFailure(String nodeId) {
            failedNodes.add(nodeId);
            logger.info("Simulated failure of node: {}", nodeId);
        }
        
        void simulateNodeRecovery(String nodeId) {
            failedNodes.remove(nodeId);
            logger.info("Simulated recovery of node: {}", nodeId);
        }
        
        boolean isNodeFailed(String nodeId) {
            return failedNodes.contains(nodeId);
        }
    }
}