package com.example.consensus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a response to a consensus request.
 */
public class ConsensusResponse {
    
    private final boolean success;
    private final Long currentValue;
    private final String nodeId;
    private final String message;
    private final ConsensusState state;
    
    @JsonCreator
    public ConsensusResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("currentValue") Long currentValue,
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("message") String message,
            @JsonProperty("state") ConsensusState state) {
        this.success = success;
        this.currentValue = currentValue;
        this.nodeId = nodeId;
        this.message = message;
        this.state = state;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public Long getCurrentValue() {
        return currentValue;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public String getMessage() {
        return message;
    }
    
    public ConsensusState getState() {
        return state;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsensusResponse that = (ConsensusResponse) o;
        return success == that.success &&
               Objects.equals(currentValue, that.currentValue) &&
               Objects.equals(nodeId, that.nodeId) &&
               Objects.equals(message, that.message) &&
               state == that.state;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(success, currentValue, nodeId, message, state);
    }
    
    @Override
    public String toString() {
        return "ConsensusResponse{" +
               "success=" + success +
               ", currentValue=" + currentValue +
               ", nodeId='" + nodeId + '\'' +
               ", message='" + message + '\'' +
               ", state=" + state +
               '}';
    }
}