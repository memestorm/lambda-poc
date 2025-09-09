package com.example.consensus.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for JSON serialization and deserialization of consensus messages.
 * Handles conversion between Java objects and JSON strings for SQS message handling.
 */
public class JsonMessageSerializer {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonMessageSerializer.class);
    private static final ObjectMapper objectMapper;
    
    static {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * Serializes a ConsensusRequest object to JSON string.
     * 
     * @param request the ConsensusRequest to serialize
     * @return JSON string representation
     * @throws MessageSerializationException if serialization fails
     */
    public static String serialize(ConsensusRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            logger.debug("Serialized ConsensusRequest: {}", json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize ConsensusRequest: {}", request, e);
            throw new MessageSerializationException("Failed to serialize ConsensusRequest", e);
        }
    }
    
    /**
     * Serializes a ConsensusResponse object to JSON string.
     * 
     * @param response the ConsensusResponse to serialize
     * @return JSON string representation
     * @throws MessageSerializationException if serialization fails
     */
    public static String serialize(ConsensusResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            logger.debug("Serialized ConsensusResponse: {}", json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize ConsensusResponse: {}", response, e);
            throw new MessageSerializationException("Failed to serialize ConsensusResponse", e);
        }
    }
    
    /**
     * Serializes a Vote object to JSON string.
     * 
     * @param vote the Vote to serialize
     * @return JSON string representation
     * @throws MessageSerializationException if serialization fails
     */
    public static String serialize(Vote vote) {
        try {
            String json = objectMapper.writeValueAsString(vote);
            logger.debug("Serialized Vote: {}", json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize Vote: {}", vote, e);
            throw new MessageSerializationException("Failed to serialize Vote", e);
        }
    }
    
    /**
     * Deserializes a JSON string to ConsensusRequest object.
     * 
     * @param json the JSON string to deserialize
     * @return ConsensusRequest object
     * @throws MessageSerializationException if deserialization fails
     */
    public static ConsensusRequest deserializeRequest(String json) {
        try {
            if (json == null) {
                throw new MessageSerializationException("JSON string cannot be null");
            }
            ConsensusRequest request = objectMapper.readValue(json, ConsensusRequest.class);
            logger.debug("Deserialized ConsensusRequest from: {}", json);
            return request;
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize ConsensusRequest from JSON: {}", json, e);
            throw new MessageSerializationException("Failed to deserialize ConsensusRequest", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for ConsensusRequest deserialization: {}", json, e);
            throw new MessageSerializationException("Invalid input for ConsensusRequest deserialization", e);
        }
    }
    
    /**
     * Deserializes a JSON string to ConsensusResponse object.
     * 
     * @param json the JSON string to deserialize
     * @return ConsensusResponse object
     * @throws MessageSerializationException if deserialization fails
     */
    public static ConsensusResponse deserializeResponse(String json) {
        try {
            if (json == null) {
                throw new MessageSerializationException("JSON string cannot be null");
            }
            ConsensusResponse response = objectMapper.readValue(json, ConsensusResponse.class);
            logger.debug("Deserialized ConsensusResponse from: {}", json);
            return response;
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize ConsensusResponse from JSON: {}", json, e);
            throw new MessageSerializationException("Failed to deserialize ConsensusResponse", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for ConsensusResponse deserialization: {}", json, e);
            throw new MessageSerializationException("Invalid input for ConsensusResponse deserialization", e);
        }
    }
    
    /**
     * Deserializes a JSON string to Vote object.
     * 
     * @param json the JSON string to deserialize
     * @return Vote object
     * @throws MessageSerializationException if deserialization fails
     */
    public static Vote deserializeVote(String json) {
        try {
            if (json == null) {
                throw new MessageSerializationException("JSON string cannot be null");
            }
            Vote vote = objectMapper.readValue(json, Vote.class);
            logger.debug("Deserialized Vote from: {}", json);
            return vote;
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize Vote from JSON: {}", json, e);
            throw new MessageSerializationException("Failed to deserialize Vote", e);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid input for Vote deserialization: {}", json, e);
            throw new MessageSerializationException("Invalid input for Vote deserialization", e);
        }
    }
    
    /**
     * Generic method to serialize any object to JSON string.
     * 
     * @param object the object to serialize
     * @return JSON string representation
     * @throws MessageSerializationException if serialization fails
     */
    public static String serializeObject(Object object) {
        try {
            String json = objectMapper.writeValueAsString(object);
            logger.debug("Serialized object of type {}: {}", object.getClass().getSimpleName(), json);
            return json;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize object: {}", object, e);
            throw new MessageSerializationException("Failed to serialize object", e);
        }
    }
    
    /**
     * Validates that a JSON string can be parsed as a valid ConsensusRequest.
     * 
     * @param json the JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidRequestJson(String json) {
        try {
            objectMapper.readValue(json, ConsensusRequest.class);
            return true;
        } catch (JsonProcessingException e) {
            logger.debug("Invalid ConsensusRequest JSON: {}", json);
            return false;
        }
    }
    
    /**
     * Validates that a JSON string can be parsed as a valid ConsensusResponse.
     * 
     * @param json the JSON string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidResponseJson(String json) {
        try {
            objectMapper.readValue(json, ConsensusResponse.class);
            return true;
        } catch (JsonProcessingException e) {
            logger.debug("Invalid ConsensusResponse JSON: {}", json);
            return false;
        }
    }
    
    /**
     * Gets the configured ObjectMapper instance for advanced usage.
     * 
     * @return the ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}