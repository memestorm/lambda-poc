package com.example.consensus.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StructuredLogger functionality.
 */
class StructuredLoggerTest {
    
    private StructuredLogger structuredLogger;
    private static final String TEST_NODE_ID = "test-node-1";
    
    @BeforeEach
    void setUp() {
        structuredLogger = new StructuredLogger(StructuredLoggerTest.class, TEST_NODE_ID);
        MDC.clear(); // Clear any existing MDC context
    }
    
    @Test
    void testLogConsensusOperation() {
        // Test that consensus operation logging doesn't throw exceptions
        assertDoesNotThrow(() -> {
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.INCREMENT_REQUEST,
                "proposal-123",
                42L,
                "initiate",
                Map.of("currentCount", 41L, "proposedValue", 42L)
            );
        });
    }
    
    @Test
    void testLogConsensusOperationWithNullContext() {
        // Test that null context is handled gracefully
        assertDoesNotThrow(() -> {
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.PROPOSE,
                "proposal-456",
                100L,
                "received",
                null
            );
        });
    }
    
    @Test
    void testLogPerformanceMetrics() {
        // Test performance metrics logging
        assertDoesNotThrow(() -> {
            structuredLogger.logPerformanceMetrics(
                "consensus_operation",
                1500L,
                5,
                Map.of(
                    "success", true,
                    "totalOperations", 10L,
                    "successRate", 0.9
                )
            );
        });
    }
    
    @Test
    void testLogError() {
        Exception testException = new RuntimeException("Test error");
        
        assertDoesNotThrow(() -> {
            structuredLogger.logError(
                "handleRequest",
                "Test error message",
                testException,
                2,
                3,
                Map.of("proposalId", "proposal-789")
            );
        });
    }
    
    @Test
    void testLogErrorWithoutException() {
        assertDoesNotThrow(() -> {
            structuredLogger.logError(
                "validateInput",
                "Invalid input provided",
                null,
                0,
                0,
                Map.of("inputType", "ConsensusRequest")
            );
        });
    }
    
    @Test
    void testLogStateTransition() {
        assertDoesNotThrow(() -> {
            structuredLogger.logStateTransition(
                "IDLE",
                "PROPOSING",
                "Consensus proposal initiated",
                Map.of(
                    "currentCount", 5L,
                    "proposalId", "proposal-abc"
                )
            );
        });
    }
    
    @Test
    void testLogMessage() {
        assertDoesNotThrow(() -> {
            structuredLogger.logMessage(
                StructuredLogger.MessageDirection.SENT,
                "PROPOSE",
                TEST_NODE_ID,
                "target-node-2",
                "proposal-def",
                Map.of("proposedValue", 10L)
            );
        });
    }
    
    @Test
    void testLogRecoveryOperation() {
        assertDoesNotThrow(() -> {
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.INITIATED,
                1,
                3,
                0,
                3,
                Map.of("currentCount", 15L)
            );
        });
    }
    
    @Test
    void testLogNodeLifecycle() {
        assertDoesNotThrow(() -> {
            structuredLogger.logNodeLifecycle(
                StructuredLogger.NodeLifecycleEvent.STARTED,
                Map.of(
                    "nodeId", TEST_NODE_ID,
                    "initialCount", 0L
                )
            );
        });
    }
    
    @Test
    void testMDCContext() {
        String proposalId = "test-proposal";
        String operation = "TEST_OPERATION";
        
        // Set MDC context - this should not throw exceptions
        assertDoesNotThrow(() -> {
            structuredLogger.setMDCContext(proposalId, operation);
        });
        
        // Clear MDC context - this should not throw exceptions
        assertDoesNotThrow(() -> {
            structuredLogger.clearMDCContext();
        });
        
        // Note: SLF4J Simple Logger doesn't fully support MDC, so we just test
        // that the methods don't throw exceptions rather than testing the actual values
    }
    
    @Test
    void testGetLogger() {
        assertNotNull(structuredLogger.getLogger());
        assertEquals(StructuredLoggerTest.class.getName(), 
                    structuredLogger.getLogger().getName());
    }
    
    @Test
    void testEnumValues() {
        // Test that all enum values are accessible
        assertNotNull(StructuredLogger.ConsensusOperation.INCREMENT_REQUEST);
        assertNotNull(StructuredLogger.ConsensusOperation.PROPOSE);
        assertNotNull(StructuredLogger.ConsensusOperation.VOTE);
        assertNotNull(StructuredLogger.ConsensusOperation.COMMIT);
        assertNotNull(StructuredLogger.ConsensusOperation.RECOVERY_REQUEST);
        assertNotNull(StructuredLogger.ConsensusOperation.RECOVERY_RESPONSE);
        
        assertNotNull(StructuredLogger.MessageDirection.SENT);
        assertNotNull(StructuredLogger.MessageDirection.RECEIVED);
        
        assertNotNull(StructuredLogger.RecoveryPhase.INITIATED);
        assertNotNull(StructuredLogger.RecoveryPhase.REQUESTS_SENT);
        assertNotNull(StructuredLogger.RecoveryPhase.WAITING_RESPONSES);
        assertNotNull(StructuredLogger.RecoveryPhase.PROCESSING_RESPONSES);
        assertNotNull(StructuredLogger.RecoveryPhase.COMPLETED);
        assertNotNull(StructuredLogger.RecoveryPhase.FAILED);
        assertNotNull(StructuredLogger.RecoveryPhase.RETRYING);
        
        assertNotNull(StructuredLogger.NodeLifecycleEvent.STARTING);
        assertNotNull(StructuredLogger.NodeLifecycleEvent.STARTED);
        assertNotNull(StructuredLogger.NodeLifecycleEvent.STOPPING);
        assertNotNull(StructuredLogger.NodeLifecycleEvent.STOPPED);
        assertNotNull(StructuredLogger.NodeLifecycleEvent.ERROR);
    }
}