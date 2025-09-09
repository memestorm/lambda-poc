package com.example.consensus.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.MessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Basic integration tests for ConsensusLambdaHandler focusing on
 * handler instantiation and basic request processing.
 */
@ExtendWith(MockitoExtension.class)
class ConsensusLambdaHandlerBasicTest {
    
    @Mock
    private Context lambdaContext;
    
    @Mock
    private LambdaLogger logger;
    
    @BeforeEach
    void setUp() {
        lenient().when(lambdaContext.getLogger()).thenReturn(logger);
    }
    
    @Test
    void testDefaultConstructorCreatesHandler() {
        // Act - Create handler with default constructor
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        
        // Assert - Handler should be created successfully
        assertNotNull(handler);
        assertNotNull(handler.getStateManager());
        assertNotNull(handler.getConsensusManager());
        // SQS handler will be null until task 4 is implemented
        assertNull(handler.getSqsHandler());
    }
    
    @Test
    void testHandlerProcessesValidRequest() {
        // Arrange
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node",
            null,
            null,
            null
        );
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert - Should get a response (success or failure)
        assertNotNull(response);
        assertNotNull(response.getNodeId());
        assertNotNull(response.getMessage());
        assertNotNull(response.getState());
        
        // Verify logging occurred
        verify(logger, atLeastOnce()).log(anyString());
    }
    
    @Test
    void testHandlerProcessesAllMessageTypes() {
        // Arrange
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        
        // Test each message type
        MessageType[] messageTypes = {
            MessageType.INCREMENT_REQUEST,
            MessageType.PROPOSE,
            MessageType.VOTE,
            MessageType.COMMIT,
            MessageType.RECOVERY_REQUEST,
            MessageType.RECOVERY_RESPONSE
        };
        
        for (MessageType messageType : messageTypes) {
            // Arrange
            ConsensusRequest request = new ConsensusRequest(
                messageType,
                "source-node",
                "target-node",
                10L,
                "proposal-123",
                null
            );
            
            // Act
            ConsensusResponse response = handler.handleRequest(request, lambdaContext);
            
            // Assert - Should get a response for each message type
            assertNotNull(response, "Response should not be null for message type: " + messageType);
            assertNotNull(response.getNodeId(), "Node ID should not be null for message type: " + messageType);
            assertNotNull(response.getMessage(), "Message should not be null for message type: " + messageType);
        }
    }
    
    @Test
    void testHandlerHandlesNullRequest() {
        // Arrange
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        
        // Act
        ConsensusResponse response = handler.handleRequest(null, lambdaContext);
        
        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Invalid request: request is null", response.getMessage());
    }
    
    @Test
    void testHandlerHandlesNullMessageType() {
        // Arrange
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        ConsensusRequest request = new ConsensusRequest(
            null,
            "source-node",
            "target-node",
            null,
            null,
            null
        );
        
        // Act
        ConsensusResponse response = handler.handleRequest(request, lambdaContext);
        
        // Assert
        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("Invalid request: missing type", response.getMessage());
    }
    
    @Test
    void testHandlerLogsRequestProcessing() {
        // Arrange
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        ConsensusRequest request = new ConsensusRequest(
            MessageType.INCREMENT_REQUEST,
            "external-trigger",
            "test-node",
            null,
            null,
            null
        );
        
        // Act
        handler.handleRequest(request, lambdaContext);
        
        // Assert - Verify comprehensive logging
        verify(logger).log(contains("Processing request: type=INCREMENT_REQUEST"));
        verify(logger).log(contains("Request processed"));
    }
    
    @Test
    void testHandlerWithEnvironmentNodeId() {
        // This test would require setting environment variables
        // For now, just verify the handler works with default node ID generation
        
        // Arrange & Act
        ConsensusLambdaHandler handler = new ConsensusLambdaHandler();
        
        // Assert
        assertNotNull(handler.getStateManager().getNodeId());
        assertTrue(handler.getStateManager().getNodeId().startsWith("lambda-node-"));
    }
}