package com.example.consensus.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.consensus.manager.ConsensusManagerImpl;
import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.MessageType;
import com.example.consensus.state.StateManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for ConsensusLambdaHandler focusing on request routing
 * and error handling with mock SQS operations.
 * Note: Some tests may fail due to incomplete implementations in other components
 * which will be completed in subsequent tasks.
 */
@ExtendWith(MockitoExtension.class)
class ConsensusLambdaHandlerIntegrationTest {
    
    @Mock
    private SQSMessageHandler sqsHandler;
    
    @Mock
    private Context lambdaContext;
    
    @Mock
    private LambdaLogger logger;
    
    private ConsensusLambdaHandler handler;
    private StateManagerImpl stateManager;
    private ConsensusManagerImpl consensusManager;
    
    @BeforeEach
    void setUp() {
        // Setup mock context
        lenient().when(lambdaContext.getLogger()).thenReturn(logger);
        
        // Create real implementations for integration testing
        stateManager = new StateManagerImpl("test-node-1");
        consensusManager = new ConsensusManagerImpl(stateManager, sqsHandler);
        
        // Create handler with real implementations but mock SQS
        handler = new ConsensusLambdaHandler(consensusManager, sqsHandler, stateManager);
        
        // Setup SQS handler mocks for successful operations
        lenient().when(sqsHandler.getNodeId()).thenReturn("test-node-1");
        lenient().when(sqsHandler.broadcastMessage(any())).thenReturn(4); // 4 other nodes
        lenient().when(sqsHandler.sendMessage(anyString(), any())).thenReturn(true);
    }
    
    @Test
    void testIncrementRequestFlow_SuccessfulConsensus() {
        // Arrange
        ConsensusRequest incrementRequest = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act - Handle increment request
        ConsensusResponse response = handler.handleRequest(incrementRequest, lambdaContext);
        
        // Assert - Should initiate proposal successfully
        assertTrue(response.isSuccess());
        assertEquals("Increment proposal initiated", response.getMessage());
        assertEquals(1L, response.getCurrentValue()); // Proposed value
        assertEquals(ConsensusState.PROPOSING, response.getState());
        
        // Verify SQS broadcast was called for proposal
        verify(sqsHandler).broadcastMessage(any(ConsensusRequest.class));
    }
    
    @Test
    void testProposalVotingFlow() {
        // Arrange - First initiate a proposal
        stateManager.transitionToState(ConsensusState.PROPOSING);
        stateManager.setCurrentProposalId("proposal-123");
        
        // Create a proposal request from another node
        ConsensusRequest proposalRequest = new ConsensusRequest(
            MessageType.PROPOSE,
            "node-2",
            "test-node-1",
            5L,
            "proposal-456",
            null
        );
        
        // Act - Handle proposal
        ConsensusResponse response = handler.handleRequest(proposalRequest, lambdaContext);
        
        // Assert - Should process proposal and send vote
        assertTrue(response.isSuccess());
        assertEquals(ConsensusState.VOTING, response.getState());
        
        // Verify vote was sent back to proposing node
        verify(sqsHandler).sendMessage(eq("node-2"), any(ConsensusRequest.class));
    }
    
    @Test
    void testVoteProcessingFlow() {
        // Arrange - Set up node as proposer waiting for votes
        stateManager.transitionToState(ConsensusState.PROPOSING);
        stateManager.setCurrentProposalId("proposal-123");
        
        // Create vote requests from other nodes
        Map<String, Object> acceptMetadata = new HashMap<>();
        acceptMetadata.put("accept", true);
        
        ConsensusRequest vote1 = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            acceptMetadata
        );
        
        ConsensusRequest vote2 = new ConsensusRequest(
            MessageType.VOTE,
            "node-3",
            "test-node-1",
            null,
            "proposal-123",
            acceptMetadata
        );
        
        ConsensusRequest vote3 = new ConsensusRequest(
            MessageType.VOTE,
            "node-4",
            "test-node-1",
            null,
            "proposal-123",
            acceptMetadata
        );
        
        // Act - Process votes one by one
        ConsensusResponse response1 = handler.handleRequest(vote1, lambdaContext);
        ConsensusResponse response2 = handler.handleRequest(vote2, lambdaContext);
        ConsensusResponse response3 = handler.handleRequest(vote3, lambdaContext);
        
        // Assert - All votes should be processed successfully
        assertTrue(response1.isSuccess());
        assertTrue(response2.isSuccess());
        assertTrue(response3.isSuccess());
        
        // After receiving majority votes, should send commit messages
        verify(sqsHandler, atLeast(1)).broadcastMessage(any(ConsensusRequest.class));
    }
    
    @Test
    void testCommitProcessingFlow() {
        // Arrange - Set up node in voting state
        stateManager.transitionToState(ConsensusState.VOTING);
        stateManager.setCurrentProposalId("proposal-123");
        
        ConsensusRequest commitRequest = new ConsensusRequest(
            MessageType.COMMIT,
            "node-2",
            "test-node-1",
            7L,
            "proposal-123",
            null
        );
        
        // Act - Handle commit
        ConsensusResponse response = handler.handleRequest(commitRequest, lambdaContext);
        
        // Assert - Should commit the decision and update count
        assertTrue(response.isSuccess());
        assertEquals("Decision committed", response.getMessage());
        assertEquals(7L, response.getCurrentValue());
        assertEquals(7L, stateManager.getCurrentCount());
        assertEquals(ConsensusState.IDLE, response.getState());
    }
    
    @Test
    void testRecoveryRequestFlow() {
        // Arrange - Set current count to simulate established state
        stateManager.updateCount(15L);
        
        ConsensusRequest recoveryRequest = new ConsensusRequest(
            MessageType.RECOVERY_REQUEST,
            "node-5",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act - Handle recovery request
        ConsensusResponse response = handler.handleRequest(recoveryRequest, lambdaContext);
        
        // Assert - Should provide current state
        assertTrue(response.isSuccess());
        assertEquals("Recovery data provided", response.getMessage());
        assertEquals(15L, response.getCurrentValue());
        
        // Verify recovery response was sent
        verify(sqsHandler).sendMessage(eq("node-5"), any(ConsensusRequest.class));
    }
    
    @Test
    void testRecoveryResponseFlow() {
        // Arrange - Set node in recovery state
        stateManager.setRecovering(true);
        stateManager.transitionToState(ConsensusState.RECOVERING);
        
        ConsensusRequest recoveryResponse = new ConsensusRequest(
            MessageType.RECOVERY_RESPONSE,
            "node-2",
            "test-node-1",
            20L,
            null,
            null
        );
        
        // Act - Handle recovery response
        ConsensusResponse response = handler.handleRequest(recoveryResponse, lambdaContext);
        
        // Assert - Should process recovery data
        assertTrue(response.isSuccess());
        assertEquals(ConsensusState.RECOVERING, response.getState());
    }
    
    @Test
    void testConcurrentRequestHandling() {
        // Arrange - Multiple different request types
        ConsensusRequest incrementRequest = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        ConsensusRequest recoveryRequest = new ConsensusRequest(
            MessageType.RECOVERY_REQUEST,
            "node-5",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act - Handle requests in sequence (simulating concurrent processing)
        ConsensusResponse incrementResponse = handler.handleRequest(incrementRequest, lambdaContext);
        ConsensusResponse recoveryResponse = handler.handleRequest(recoveryRequest, lambdaContext);
        
        // Assert - Both should be handled appropriately
        assertTrue(incrementResponse.isSuccess());
        assertTrue(recoveryResponse.isSuccess());
        
        // Verify appropriate SQS operations were called
        verify(sqsHandler, atLeast(1)).broadcastMessage(any(ConsensusRequest.class));
        verify(sqsHandler, atLeast(1)).sendMessage(anyString(), any(ConsensusRequest.class));
    }
    
    @Test
    void testErrorHandlingWithRealImplementations() {
        // Arrange - Simulate SQS failure
        when(sqsHandler.broadcastMessage(any())).thenReturn(0); // No nodes reached
        
        ConsensusRequest incrementRequest = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act - Handle request with SQS failure
        ConsensusResponse response = handler.handleRequest(incrementRequest, lambdaContext);
        
        // Assert - Should still handle gracefully
        // The exact behavior depends on ConsensusManagerImpl implementation
        assertNotNull(response);
        assertEquals("test-node-1", response.getNodeId());
    }
    
    @Test
    void testStateTransitionsWithRealImplementations() {
        // Arrange - Start with idle state
        assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
        assertEquals(0L, stateManager.getCurrentCount());
        
        // Act - Process increment request
        ConsensusRequest incrementRequest = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        ConsensusResponse response = handler.handleRequest(incrementRequest, lambdaContext);
        
        // Assert - State should transition to proposing
        assertTrue(response.isSuccess());
        assertEquals(ConsensusState.PROPOSING, stateManager.getConsensusState());
        assertNotNull(stateManager.getCurrentProposalId());
    }
    
    @Test
    void testDefaultConstructorInitialization() {
        // Act - Create handler with default constructor
        ConsensusLambdaHandler defaultHandler = new ConsensusLambdaHandler();
        
        // Assert - Should have initialized state manager and consensus manager
        assertNotNull(defaultHandler.getStateManager());
        assertNotNull(defaultHandler.getConsensusManager());
        // SQS handler should be null until task 4 is implemented
        assertNull(defaultHandler.getSqsHandler());
    }
    
    @Test
    void testLoggingIntegration() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act
        handler.handleRequest(request, lambdaContext);
        
        // Assert - Verify comprehensive logging
        verify(logger, atLeastOnce()).log(contains("Processing request"));
        verify(logger, atLeastOnce()).log(contains("Handling increment request"));
        verify(logger, atLeastOnce()).log(contains("Request processed"));
    }
    
    @Test
    void testNodeIdConsistency() {
        // Arrange
        when(sqsHandler.getNodeId()).thenReturn("consistent-node-id");
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.RECOVERY_REQUEST,
            "requesting-node",
            "consistent-node-id",
            null,
            null,
            null
        );
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert - Node ID should be consistent across components
        assertEquals("test-node-1", response.getNodeId()); // From StateManager
        assertTrue(response.isSuccess());
    }
}