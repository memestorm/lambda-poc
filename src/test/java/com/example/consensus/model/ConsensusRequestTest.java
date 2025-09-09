package com.example.consensus.model;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConsensusRequest model class.
 */
class ConsensusRequestTest {
    
    @Test
    void testConsensusRequestCreation() {
        // Given
        MessageType type = MessageType.INCREMENT_REQUEST;
        String sourceNodeId = "node-1";
        String targetNodeId = "node-2";
        Long proposedValue = 42L;
        String proposalId = "proposal-123";
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", "2023-01-01T00:00:00Z");
        
        // When
        ConsensusRequest request = new ConsensusRequest(
            type, sourceNodeId, targetNodeId, proposedValue, proposalId, metadata
        );
        
        // Then
        assertEquals(type, request.getType());
        assertEquals(sourceNodeId, request.getSourceNodeId());
        assertEquals(targetNodeId, request.getTargetNodeId());
        assertEquals(proposedValue, request.getProposedValue());
        assertEquals(proposalId, request.getProposalId());
        assertEquals(metadata, request.getMetadata());
    }
    
    @Test
    void testConsensusRequestEquality() {
        // Given
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("test", "value");
        
        ConsensusRequest request1 = new ConsensusRequest(
            MessageType.PROPOSE, "node-1", "node-2", 10L, "prop-1", metadata
        );
        
        ConsensusRequest request2 = new ConsensusRequest(
            MessageType.PROPOSE, "node-1", "node-2", 10L, "prop-1", metadata
        );
        
        // Then
        assertEquals(request1, request2);
        assertEquals(request1.hashCode(), request2.hashCode());
    }
    
    @Test
    void testConsensusRequestToString() {
        // Given
        ConsensusRequest request = new ConsensusRequest(
            MessageType.VOTE, "node-1", null, null, "prop-1", null
        );
        
        // When
        String result = request.toString();
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("VOTE"));
        assertTrue(result.contains("node-1"));
        assertTrue(result.contains("prop-1"));
    }
}