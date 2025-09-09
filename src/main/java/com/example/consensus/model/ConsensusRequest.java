package com.example.consensus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a consensus request message exchanged between Lambda nodes.
 */
public class ConsensusRequest {
    
    private final MessageType type;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final Long proposedValue;
    private final String proposalId;
    private final Map<String, Object> metadata;
    
    @JsonCreator
    public ConsensusRequest(
            @JsonProperty("type") MessageType type,
            @JsonProperty("sourceNodeId") String sourceNodeId,
            @JsonProperty("targetNodeId") String targetNodeId,
            @JsonProperty("proposedValue") Long proposedValue,
            @JsonProperty("proposalId") String proposalId,
            @JsonProperty("metadata") Map<String, Object> metadata) {
        this.type = type;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.proposedValue = proposedValue;
        this.proposalId = proposalId;
        this.metadata = metadata;
    }
    
    public MessageType getType() {
        return type;
    }
    
    public String getSourceNodeId() {
        return sourceNodeId;
    }
    
    public String getTargetNodeId() {
        return targetNodeId;
    }
    
    public Long getProposedValue() {
        return proposedValue;
    }
    
    public String getProposalId() {
        return proposalId;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsensusRequest that = (ConsensusRequest) o;
        return type == that.type &&
               Objects.equals(sourceNodeId, that.sourceNodeId) &&
               Objects.equals(targetNodeId, that.targetNodeId) &&
               Objects.equals(proposedValue, that.proposedValue) &&
               Objects.equals(proposalId, that.proposalId) &&
               Objects.equals(metadata, that.metadata);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type, sourceNodeId, targetNodeId, proposedValue, proposalId, metadata);
    }
    
    @Override
    public String toString() {
        return "ConsensusRequest{" +
               "type=" + type +
               ", sourceNodeId='" + sourceNodeId + '\'' +
               ", targetNodeId='" + targetNodeId + '\'' +
               ", proposedValue=" + proposedValue +
               ", proposalId='" + proposalId + '\'' +
               ", metadata=" + metadata +
               '}';
    }
}