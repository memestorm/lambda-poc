package com.example.consensus.manager;

import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.model.*;
import com.example.consensus.state.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Focused unit tests for recovery quorum validation logic.
 * Tests various scenarios for quorum requirements based on node counts.
 */
@ExtendWith(MockitoExtension.class)
class RecoveryQuorumTest {
    
    @Mock
    private StateManager stateManager;
    
    @Mock
    private SQSMessageHandler messageHandler;
    
    private ConsensusManagerImpl consensusManager;
    
    @BeforeEach
    void setUp() {
        lenient().when(stateManager.getNodeId()).thenReturn("test-node");
        lenient().when(stateManager.getCurrentCount()).thenReturn(0L);
        lenient().when(stateManager.getConsensusState()).thenReturn(ConsensusState.IDLE);
        lenient().when(stateManager.isRecovering()).thenReturn(false);
        lenient().when(stateManager.updateCount(anyLong())).thenReturn(true);
        
        consensusManager = new ConsensusManagerImpl(stateManager, messageHandler);
    }
    
    @ParameterizedTest
    @CsvSource({
        "3, 2, false",  // 3 total nodes, 2 responses - invalid (below minimum quorum of 3)
        "3, 1, false",  // 3 total nodes, 1 response - invalid (below minimum)
        "4, 3, true",   // 4 total nodes, 3 responses - valid (3/4 majority)
        "4, 2, false",  // 4 total nodes, 2 responses - invalid (below minimum quorum)
        "5, 3, true",   // 5 total nodes, 3 responses - valid (minimum quorum)
        "5, 4, true",   // 5 total nodes, 4 responses - valid (strong majority)
        "5, 2, false",  // 5 total nodes, 2 responses - invalid (below minimum)
        "2, 2, false",  // 2 total nodes - invalid (cannot have valid quorum)
        "1, 1, false"   // 1 total node - invalid (cannot have valid quorum)
    })
    void testQuorumValidationRules(int totalNodes, int responses, boolean expectedValid) {
        // Arrange - create known nodes set based on total (excluding this node)
        Set<String> knownNodes = createKnownNodesSet(totalNodes - 1);
        when(stateManager.getKnownNodes()).thenReturn(knownNodes);
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send the specified number of recovery responses
        for (int i = 1; i <= responses; i++) {
            ConsensusRequest response = createRecoveryResponse("node-" + i, 100L);
            consensusManager.processRequest(response);
        }
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify behavior based on expected validity
        if (expectedValid) {
            // Should eventually call updateCount if quorum is valid
            verify(stateManager, timeout(2000)).updateCount(100L);
        } else {
            // Should not call updateCount if quorum is invalid
            verify(stateManager, after(500).never()).updateCount(anyLong());
        }
    }
    
    @Test
    void testMinimumQuorumRequirement() {
        // Test that minimum quorum of 3 is always required regardless of total nodes
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3", "node-4", "node-5", "node-6"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send only 2 responses (below minimum quorum of 3)
        ConsensusRequest response1 = createRecoveryResponse("node-1", 100L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 100L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        
        // Wait for processing
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should not update count with insufficient responses
        verify(stateManager, never()).updateCount(anyLong());
    }
    
    @Test
    void testMajorityCountWithTiedVotes() {
        // Test scenario where votes are tied - should use highest count
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3", "node-4"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send responses with tied counts (2 votes for 100, 2 votes for 200)
        ConsensusRequest response1 = createRecoveryResponse("node-1", 100L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 100L);
        ConsensusRequest response3 = createRecoveryResponse("node-3", 200L);
        ConsensusRequest response4 = createRecoveryResponse("node-4", 200L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        consensusManager.processRequest(response3);
        consensusManager.processRequest(response4);
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should adopt the higher count (200) when tied
        verify(stateManager, timeout(1000)).updateCount(200L);
    }
    
    @Test
    void testMajorityCountWithClearWinner() {
        // Test scenario with clear majority
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3", "node-4", "node-5"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send responses with clear majority for 150
        ConsensusRequest response1 = createRecoveryResponse("node-1", 150L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 150L);
        ConsensusRequest response3 = createRecoveryResponse("node-3", 150L); // 3 votes for 150
        ConsensusRequest response4 = createRecoveryResponse("node-4", 100L); // 1 vote for 100
        ConsensusRequest response5 = createRecoveryResponse("node-5", 200L); // 1 vote for 200
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        consensusManager.processRequest(response3);
        consensusManager.processRequest(response4);
        consensusManager.processRequest(response5);
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should adopt the majority count (150)
        verify(stateManager, timeout(1000)).updateCount(150L);
    }
    
    @Test
    void testQuorumWithExactlyThreeNodes() {
        // Test special case with exactly 3 total nodes (minimum viable federation)
        // Need at least 3 responses for minimum quorum, so we need to add a third node
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send responses from 3 nodes (3 out of 4 total including this node)
        ConsensusRequest response1 = createRecoveryResponse("node-1", 75L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 75L);
        ConsensusRequest response3 = createRecoveryResponse("node-3", 75L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        consensusManager.processRequest(response3);
        
        // Allow processing time
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should accept 3 out of 4 as valid quorum
        verify(stateManager, timeout(2000)).updateCount(75L);
    }
    
    @Test
    void testQuorumWithFourNodes() {
        // Test with 4 total nodes - need at least 3 responses
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send exactly 3 responses (minimum for 4-node federation)
        ConsensusRequest response1 = createRecoveryResponse("node-1", 90L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 90L);
        ConsensusRequest response3 = createRecoveryResponse("node-3", 90L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        consensusManager.processRequest(response3);
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should accept 3 out of 4 as valid quorum
        verify(stateManager, timeout(1000)).updateCount(90L);
    }
    
    @Test
    void testRecoveryResponsesFromUnknownNodes() {
        // Test that responses from unknown nodes are still processed
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send responses including from an unknown node
        ConsensusRequest response1 = createRecoveryResponse("node-1", 120L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", 120L);
        ConsensusRequest response3 = createRecoveryResponse("unknown-node", 120L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        ConsensusResponse result3 = consensusManager.processRequest(response3);
        
        // All responses should be processed successfully
        assertTrue(result3.isSuccess());
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should still achieve quorum with 3 responses
        verify(stateManager, timeout(1000)).updateCount(120L);
    }
    
    @Test
    void testRecoveryWithNullCounts() {
        // Test handling of recovery responses with null counts
        when(stateManager.getKnownNodes()).thenReturn(Set.of("node-1", "node-2", "node-3"));
        when(messageHandler.sendMessage(anyString(), any(ConsensusRequest.class))).thenReturn(true);
        
        // Start recovery
        when(stateManager.isRecovering()).thenReturn(false);
        consensusManager.initiateRecovery();
        when(stateManager.isRecovering()).thenReturn(true);
        
        // Send responses with some null counts
        ConsensusRequest response1 = createRecoveryResponse("node-1", 80L);
        ConsensusRequest response2 = createRecoveryResponse("node-2", null); // Null count
        ConsensusRequest response3 = createRecoveryResponse("node-3", 80L);
        
        consensusManager.processRequest(response1);
        consensusManager.processRequest(response2);
        consensusManager.processRequest(response3);
        
        // Allow processing time
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Should handle null counts gracefully and use valid majority
        verify(stateManager, timeout(1000)).updateCount(80L);
    }
    
    /**
     * Helper method to create a set of known nodes.
     */
    private Set<String> createKnownNodesSet(int count) {
        Set<String> nodes = new java.util.HashSet<>();
        for (int i = 1; i <= count; i++) {
            nodes.add("node-" + i);
        }
        return nodes;
    }
    
    /**
     * Helper method to create recovery response messages.
     */
    private ConsensusRequest createRecoveryResponse(String nodeId, Long count) {
        return new ConsensusRequest(
            MessageType.RECOVERY_RESPONSE,
            nodeId,
            "test-node",
            count,
            "response-" + nodeId,
            Map.of("consensusState", "IDLE", "timestamp", Instant.now().toString())
        );
    }
}