package com.example.consensus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a vote cast by a node during the consensus process.
 */
public class Vote {
    
    private final String nodeId;
    private final String proposalId;
    private final boolean accept;
    private final Instant timestamp;
    private final String reason;
    
    @JsonCreator
    public Vote(
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("proposalId") String proposalId,
            @JsonProperty("accept") boolean accept,
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("reason") String reason) {
        this.nodeId = nodeId;
        this.proposalId = proposalId;
        this.accept = accept;
        this.timestamp = timestamp;
        this.reason = reason;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public String getProposalId() {
        return proposalId;
    }
    
    public boolean isAccept() {
        return accept;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return accept == vote.accept &&
               Objects.equals(nodeId, vote.nodeId) &&
               Objects.equals(proposalId, vote.proposalId) &&
               Objects.equals(timestamp, vote.timestamp) &&
               Objects.equals(reason, vote.reason);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeId, proposalId, accept, timestamp, reason);
    }
    
    @Override
    public String toString() {
        return "Vote{" +
               "nodeId='" + nodeId + '\'' +
               ", proposalId='" + proposalId + '\'' +
               ", accept=" + accept +
               ", timestamp=" + timestamp +
               ", reason='" + reason + '\'' +
               '}';
    }
}