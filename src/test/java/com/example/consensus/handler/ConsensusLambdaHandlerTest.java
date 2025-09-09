package com.example.consensus.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.consensus.manager.ConsensusManager;
import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.MessageType;
import com.example.consensus.state.StateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Integration tests for ConsensusLambdaHandler with mock SQS operations.
 * Tests request routing, error handling, and response formatting.
 */
@ExtendWith(MockitoExtension.class)
class ConsensusLambdaHandlerTest {
    
    @Mock
    private ConsensusManager consensusManager;
    
    @Mock
    private SQSMessageHandler sqsHandler;
    
    @Mock
    private StateManager stateManager;
    
    @Mock
    private Context lambdaContext;
    
    @Mock
    private LambdaLogger logger;
    
    private ConsensusLambdaHandler handler;
    
    @BeforeEach
    void setUp() {
        // Setup mock context
        lenient().when(lambdaContext.getLogger()).thenReturn(logger);
        
        // Setup default state manager responses
        lenient().when(stateManager.getNodeId()).thenReturn("test-node-1");
        lenient().when(stateManager.getCurrentCount()).thenReturn(5L);
        lenient().when(stateManager.getConsensusState()).thenReturn(ConsensusState.IDLE);
        
        // Create handler with mocked dependencies
        handler = new ConsensusLambdaHandler(consensusManager, sqsHandler, stateManager);
    }
    
    @Test
    void testHandleIncrementRequest_Success() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(consensusManager.initiateProposal(6L)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals(6L, response.getCurrentValue());
        assertEquals("test-node-1", response.getNodeId());
        assertEquals("Increment proposal initiated", response.getMessage());
        assertEquals(ConsensusState.IDLE, response.getState());
        
        verify(consensusManager).initiateProposal(6L);
        verify(logger, atLeastOnce()).log(anyString());
    }
    
    @Test
    void testHandleIncrementRequest_Failure() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(consensusManager.initiateProposal(6L)).thenReturn(false);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertEquals(5L, response.getCurrentValue());
        assertEquals("test-node-1", response.getNodeId());
        assertEquals("Failed to initiate increment proposal", response.getMessage());
        assertEquals(ConsensusState.IDLE, response.getState());
    }
    
    @Test
    void testHandleProposal_Success() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.PROPOSE,
            "node-2",
            "test-node-1",
            7L,
            "proposal-123",
            null
        );
        
        ConsensusResponse expectedResponse = new ConsensusResponse(
            true, 7L, "test-node-1", "Proposal processed", ConsensusState.VOTING
        );
        
        when(consensusManager.processRequest(request)).thenReturn(expectedResponse);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertEquals(expectedResponse, response);
        verify(consensusManager).processRequest(request);
    }
    
    @Test
    void testHandleVote_Accept() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accept", true);
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            metadata
        );
        
        when(consensusManager.processVote("proposal-123", "node-2", true)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals("Vote processed", response.getMessage());
        verify(consensusManager).processVote("proposal-123", "node-2", true);
    }
    
    @Test
    void testHandleVote_Reject() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accept", false);
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            metadata
        );
        
        when(consensusManager.processVote("proposal-123", "node-2", false)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals("Vote processed", response.getMessage());
        verify(consensusManager).processVote("proposal-123", "node-2", false);
    }
    
    @Test
    void testHandleVote_NoMetadata_DefaultsToReject() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            null
        );
        
        when(consensusManager.processVote("proposal-123", "node-2", false)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        verify(consensusManager).processVote("proposal-123", "node-2", false);
    }
    
    @Test
    void testHandleCommit_Success() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.COMMIT,
            "node-2",
            "test-node-1",
            8L,
            "proposal-123",
            null
        );
        
        when(consensusManager.commitDecision("proposal-123", 8L)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals(8L, response.getCurrentValue());
        assertEquals("Decision committed", response.getMessage());
        verify(consensusManager).commitDecision("proposal-123", 8L);
    }
    
    @Test
    void testHandleRecoveryRequest_Success() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.RECOVERY_REQUEST,
            "node-3",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(consensusManager.handleRecoveryRequest("node-3")).thenReturn(10L);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        assertEquals(10L, response.getCurrentValue());
        assertEquals("Recovery data provided", response.getMessage());
        verify(consensusManager).handleRecoveryRequest("node-3");
    }
    
    @Test
    void testHandleRecoveryResponse_Success() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.RECOVERY_RESPONSE,
            "node-2",
            "test-node-1",
            12L,
            null,
            null
        );
        
        ConsensusResponse expectedResponse = new ConsensusResponse(
            true, 12L, "test-node-1", "Recovery response processed", ConsensusState.RECOVERING
        );
        
        when(consensusManager.processRequest(request)).thenReturn(expectedResponse);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertEquals(expectedResponse, response);
        verify(consensusManager).processRequest(request);
    }
    
    @Test
    void testHandleRequest_NullRequest() {
        // Act
        ConsensusResponse response = handler.handleRequest(null, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid request: request is null", response.getMessage());
        assertEquals("test-node-1", response.getNodeId());
    }
    
    @Test
    void testHandleRequest_NullMessageType() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            null,
            "node-2",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertEquals("Invalid request: missing type", response.getMessage());
    }
    
    @Test
    void testHandleRequest_ExceptionInRouting() {
        // Arrange - simulate an exception in routing
        ConsensusRequest request = new ConsensusRequest(
            MessageType.PROPOSE,
            "node-2",
            "test-node-1",
            7L,
            "proposal-123",
            null
        );
        
        when(consensusManager.processRequest(request))
            .thenThrow(new RuntimeException("Simulated routing error"));
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Error processing proposal"));
        assertTrue(response.getMessage().contains("RuntimeException"));
    }
    
    @Test
    void testHandleRequest_ExceptionInProcessing() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(consensusManager.initiateProposal(anyLong()))
            .thenThrow(new RuntimeException("Consensus manager error"));
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Error processing increment"));
        assertTrue(response.getMessage().contains("RuntimeException"));
        verify(logger).log(contains("Error handling increment request"));
    }
    
    @Test
    void testHandleRequest_StateManagerException() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(stateManager.getCurrentCount())
            .thenThrow(new RuntimeException("State manager error"));
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Error processing increment"));
        assertTrue(response.getMessage().contains("RuntimeException"));
    }
    
    @Test
    void testCreateErrorResponse_WithNullStateManager() {
        // Arrange - create handler with null state manager
        ConsensusLambdaHandler handlerWithNullState = new ConsensusLambdaHandler(
            consensusManager, sqsHandler, null
        );
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        // Act
        ConsensusResponse response = handlerWithNullState.handleRequest(request, lambdaContext);
        
        // Assert
        assertFalse(response.isSuccess());
        assertNull(response.getCurrentValue());
        assertEquals("unknown", response.getNodeId());
        assertEquals(ConsensusState.IDLE, response.getState());
    }
    
    @Test
    void testVoteDecisionExtraction_StringValue() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accept", "true");
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            metadata
        );
        
        when(consensusManager.processVote("proposal-123", "node-2", true)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        verify(consensusManager).processVote("proposal-123", "node-2", true);
    }
    
    @Test
    void testVoteDecisionExtraction_InvalidValue() {
        // Arrange
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("accept", 123); // Invalid type
        
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE,
            "node-2",
            "test-node-1",
            null,
            "proposal-123",
            metadata
        );
        
        when(consensusManager.processVote("proposal-123", "node-2", false)).thenReturn(true);
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertTrue(response.isSuccess());
        // Should default to false (reject) for invalid value
        verify(consensusManager).processVote("proposal-123", "node-2", false);
    }
    
    @Test
    void testLoggingBehavior() {
        // Arrange
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node-1",
            null,
            null,
            null
        );
        
        when(consensusManager.initiateProposal(6L)).thenReturn(true);
        
        // Act
        handler.handleRequest(request, lambdaContext);
        
        // Assert - verify logging calls
        verify(logger).log(contains("Processing request: type=INCREMENT_REQUEST"));
        verify(logger).log(contains("Handling increment request"));
        verify(logger).log(contains("Proposing increment from 5 to 6"));
        verify(logger).log(contains("Request processed: success=true"));
    }
    
    @Test
    void testGetterMethods() {
        // Test that getter methods return the injected dependencies
        assertEquals(consensusManager, handler.getConsensusManager());
        assertEquals(sqsHandler, handler.getSqsHandler());
        assertEquals(stateManager, handler.getStateManager());
    }
}