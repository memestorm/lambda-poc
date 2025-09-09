package com.example.consensus.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonMessageSerializer.
 */
class JsonMessageSerializerTest {
    
    @Nested
    @DisplayName("ConsensusRequest Serialization Tests")
    class ConsensusRequestSerializationTests {
        
        @Test
        @DisplayName("Should serialize and deserialize ConsensusRequest with all fields")
        void shouldSerializeAndDeserializeCompleteConsensusRequest() {
            // Given
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", "2023-01-01T00:00:00Z");
            metadata.put("priority", 1);
            
            ConsensusRequest original = new ConsensusRequest(
                MessageType.PROPOSE,
                "node-1",
                "node-2", 
                42L,
                "proposal-123",
                metadata
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            ConsensusRequest deserialized = JsonMessageSerializer.deserializeRequest(json);
            
            // Then
            assertNotNull(json);
            assertFalse(json.isEmpty());
            assertEquals(original, deserialized);
            assertEquals(original.getType(), deserialized.getType());
            assertEquals(original.getSourceNodeId(), deserialized.getSourceNodeId());
            assertEquals(original.getTargetNodeId(), deserialized.getTargetNodeId());
            assertEquals(original.getProposedValue(), deserialized.getProposedValue());
            assertEquals(original.getProposalId(), deserialized.getProposalId());
            assertEquals(original.getMetadata(), deserialized.getMetadata());
        }
        
        @Test
        @DisplayName("Should serialize and deserialize ConsensusRequest with null fields")
        void shouldSerializeAndDeserializeConsensusRequestWithNullFields() {
            // Given
            ConsensusRequest original = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "node-1",
                null,
                null,
                null,
                null
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            ConsensusRequest deserialized = JsonMessageSerializer.deserializeRequest(json);
            
            // Then
            assertEquals(original, deserialized);
            assertNull(deserialized.getTargetNodeId());
            assertNull(deserialized.getProposedValue());
            assertNull(deserialized.getProposalId());
            assertNull(deserialized.getMetadata());
        }
        
        @Test
        @DisplayName("Should handle all MessageType values")
        void shouldHandleAllMessageTypes() {
            for (MessageType messageType : MessageType.values()) {
                // Given
                ConsensusRequest original = new ConsensusRequest(
                    messageType,
                    "node-1",
                    "node-2",
                    1L,
                    "test-proposal",
                    null
                );
                
                // When
                String json = JsonMessageSerializer.serialize(original);
                ConsensusRequest deserialized = JsonMessageSerializer.deserializeRequest(json);
                
                // Then
                assertEquals(messageType, deserialized.getType());
            }
        }
        
        @Test
        @DisplayName("Should throw exception for invalid JSON")
        void shouldThrowExceptionForInvalidRequestJson() {
            // Given
            String invalidJson = "{invalid json}";
            
            // When & Then
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeRequest(invalidJson));
        }
    }
    
    @Nested
    @DisplayName("ConsensusResponse Serialization Tests")
    class ConsensusResponseSerializationTests {
        
        @Test
        @DisplayName("Should serialize and deserialize ConsensusResponse with all fields")
        void shouldSerializeAndDeserializeCompleteConsensusResponse() {
            // Given
            ConsensusResponse original = new ConsensusResponse(
                true,
                100L,
                "node-3",
                "Operation successful",
                ConsensusState.COMMITTING
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            ConsensusResponse deserialized = JsonMessageSerializer.deserializeResponse(json);
            
            // Then
            assertNotNull(json);
            assertFalse(json.isEmpty());
            assertEquals(original, deserialized);
            assertEquals(original.isSuccess(), deserialized.isSuccess());
            assertEquals(original.getCurrentValue(), deserialized.getCurrentValue());
            assertEquals(original.getNodeId(), deserialized.getNodeId());
            assertEquals(original.getMessage(), deserialized.getMessage());
            assertEquals(original.getState(), deserialized.getState());
        }
        
        @Test
        @DisplayName("Should serialize and deserialize ConsensusResponse with null fields")
        void shouldSerializeAndDeserializeConsensusResponseWithNullFields() {
            // Given
            ConsensusResponse original = new ConsensusResponse(
                false,
                null,
                null,
                null,
                null
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            ConsensusResponse deserialized = JsonMessageSerializer.deserializeResponse(json);
            
            // Then
            assertEquals(original, deserialized);
            assertNull(deserialized.getCurrentValue());
            assertNull(deserialized.getNodeId());
            assertNull(deserialized.getMessage());
            assertNull(deserialized.getState());
        }
        
        @Test
        @DisplayName("Should handle all ConsensusState values")
        void shouldHandleAllConsensusStates() {
            for (ConsensusState state : ConsensusState.values()) {
                // Given
                ConsensusResponse original = new ConsensusResponse(
                    true,
                    50L,
                    "node-test",
                    "Test message",
                    state
                );
                
                // When
                String json = JsonMessageSerializer.serialize(original);
                ConsensusResponse deserialized = JsonMessageSerializer.deserializeResponse(json);
                
                // Then
                assertEquals(state, deserialized.getState());
            }
        }
        
        @Test
        @DisplayName("Should throw exception for invalid JSON")
        void shouldThrowExceptionForInvalidResponseJson() {
            // Given
            String invalidJson = "{\"invalid\": \"structure\"}";
            
            // When & Then
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeResponse(invalidJson));
        }
    }
    
    @Nested
    @DisplayName("Vote Serialization Tests")
    class VoteSerializationTests {
        
        @Test
        @DisplayName("Should serialize and deserialize Vote with all fields")
        void shouldSerializeAndDeserializeCompleteVote() {
            // Given
            Instant timestamp = Instant.parse("2023-01-01T12:00:00Z");
            Vote original = new Vote(
                "voter-node",
                "proposal-456",
                true,
                timestamp,
                "Proposal looks good"
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            Vote deserialized = JsonMessageSerializer.deserializeVote(json);
            
            // Then
            assertNotNull(json);
            assertFalse(json.isEmpty());
            assertEquals(original, deserialized);
            assertEquals(original.getNodeId(), deserialized.getNodeId());
            assertEquals(original.getProposalId(), deserialized.getProposalId());
            assertEquals(original.isAccept(), deserialized.isAccept());
            assertEquals(original.getTimestamp(), deserialized.getTimestamp());
            assertEquals(original.getReason(), deserialized.getReason());
        }
        
        @Test
        @DisplayName("Should serialize and deserialize Vote with null fields")
        void shouldSerializeAndDeserializeVoteWithNullFields() {
            // Given
            Vote original = new Vote(
                "voter-node",
                "proposal-789",
                false,
                null,
                null
            );
            
            // When
            String json = JsonMessageSerializer.serialize(original);
            Vote deserialized = JsonMessageSerializer.deserializeVote(json);
            
            // Then
            assertEquals(original, deserialized);
            assertNull(deserialized.getTimestamp());
            assertNull(deserialized.getReason());
        }
        
        @Test
        @DisplayName("Should handle accept and reject votes")
        void shouldHandleAcceptAndRejectVotes() {
            // Test accept vote
            Vote acceptVote = new Vote("node-1", "prop-1", true, Instant.now(), "Accept");
            String acceptJson = JsonMessageSerializer.serialize(acceptVote);
            Vote deserializedAccept = JsonMessageSerializer.deserializeVote(acceptJson);
            assertTrue(deserializedAccept.isAccept());
            
            // Test reject vote
            Vote rejectVote = new Vote("node-2", "prop-1", false, Instant.now(), "Reject");
            String rejectJson = JsonMessageSerializer.serialize(rejectVote);
            Vote deserializedReject = JsonMessageSerializer.deserializeVote(rejectJson);
            assertFalse(deserializedReject.isAccept());
        }
        
        @Test
        @DisplayName("Should throw exception for invalid JSON")
        void shouldThrowExceptionForInvalidVoteJson() {
            // Given
            String invalidJson = "not json at all";
            
            // When & Then
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeVote(invalidJson));
        }
    }
    
    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {
        
        @Test
        @DisplayName("Should validate correct ConsensusRequest JSON")
        void shouldValidateCorrectConsensusRequestJson() {
            // Given
            ConsensusRequest request = new ConsensusRequest(
                MessageType.VOTE, "node-1", "node-2", 10L, "prop-1", null
            );
            String json = JsonMessageSerializer.serialize(request);
            
            // When & Then
            assertTrue(JsonMessageSerializer.isValidRequestJson(json));
        }
        
        @Test
        @DisplayName("Should invalidate incorrect ConsensusRequest JSON")
        void shouldInvalidateIncorrectConsensusRequestJson() {
            // Given
            String invalidJson = "{\"wrongField\": \"wrongValue\"}";
            
            // When & Then
            assertFalse(JsonMessageSerializer.isValidRequestJson(invalidJson));
        }
        
        @Test
        @DisplayName("Should validate correct ConsensusResponse JSON")
        void shouldValidateCorrectConsensusResponseJson() {
            // Given
            ConsensusResponse response = new ConsensusResponse(
                true, 25L, "node-3", "Success", ConsensusState.IDLE
            );
            String json = JsonMessageSerializer.serialize(response);
            
            // When & Then
            assertTrue(JsonMessageSerializer.isValidResponseJson(json));
        }
        
        @Test
        @DisplayName("Should invalidate incorrect ConsensusResponse JSON")
        void shouldInvalidateIncorrectConsensusResponseJson() {
            // Given
            String invalidJson = "[]";
            
            // When & Then
            assertFalse(JsonMessageSerializer.isValidResponseJson(invalidJson));
        }
    }
    
    @Nested
    @DisplayName("Generic Serialization Tests")
    class GenericSerializationTests {
        
        @Test
        @DisplayName("Should serialize generic object")
        void shouldSerializeGenericObject() {
            // Given
            Map<String, Object> testObject = new HashMap<>();
            testObject.put("key1", "value1");
            testObject.put("key2", 42);
            
            // When
            String json = JsonMessageSerializer.serializeObject(testObject);
            
            // Then
            assertNotNull(json);
            assertFalse(json.isEmpty());
            assertTrue(json.contains("key1"));
            assertTrue(json.contains("value1"));
        }
        
        @Test
        @DisplayName("Should provide access to ObjectMapper")
        void shouldProvideAccessToObjectMapper() {
            // When
            var objectMapper = JsonMessageSerializer.getObjectMapper();
            
            // Then
            assertNotNull(objectMapper);
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle null input gracefully")
        void shouldHandleNullInputGracefully() {
            // When & Then - Jackson serializes null objects as "null" string, so we test deserialization instead
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeRequest(null));
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeResponse(null));
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeVote(null));
        }
        
        @Test
        @DisplayName("Should handle empty JSON string")
        void shouldHandleEmptyJsonString() {
            // When & Then
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeRequest(""));
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeResponse(""));
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeVote(""));
        }
        
        @Test
        @DisplayName("Should handle malformed JSON")
        void shouldHandleMalformedJson() {
            // Given
            String malformedJson = "{\"type\":\"PROPOSE\",\"sourceNodeId\":\"node-1\""; // Missing closing brace
            
            // When & Then
            assertThrows(MessageSerializationException.class, 
                () -> JsonMessageSerializer.deserializeRequest(malformedJson));
        }
    }
}