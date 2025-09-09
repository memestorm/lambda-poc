package com.example.consensus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Represents the complete state of a Lambda node in the consensus federation.
 */
public class NodeState {
    
    private final String nodeId;
    private final Long currentCount;
    private final ConsensusState consensusState;
    private final Set<String> knownNodes;
    private final String currentProposalId;
    private final Map<String, Vote> receivedVotes;
    private final Instant lastHeartbeat;
    private final boolean isRecovering;
    
    @JsonCreator
    public NodeState(
            @JsonProperty("nodeId") String nodeId,
            @JsonProperty("currentCount") Long currentCount,
            @JsonProperty("consensusState") ConsensusState consensusState,
            @JsonProperty("knownNodes") Set<String> knownNodes,
            @JsonProperty("currentProposalId") String currentProposalId,
            @JsonProperty("receivedVotes") Map<String, Vote> receivedVotes,
            @JsonProperty("lastHeartbeat") Instant lastHeartbeat,
            @JsonProperty("isRecovering") boolean isRecovering) {
        this.nodeId = nodeId;
        this.currentCount = currentCount;
        this.consensusState = consensusState;
        this.knownNodes = knownNodes;
        this.currentProposalId = currentProposalId;
        this.receivedVotes = receivedVotes;
        this.lastHeartbeat = lastHeartbeat;
        this.isRecovering = isRecovering;
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public Long getCurrentCount() {
        return currentCount;
    }
    
    public ConsensusState getConsensusState() {
        return consensusState;
    }
    
    public Set<String> getKnownNodes() {
        return knownNodes;
    }
    
    public String getCurrentProposalId() {
        return currentProposalId;
    }
    
    public Map<String, Vote> getReceivedVotes() {
        return receivedVotes;
    }
    
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }
    
    public boolean isRecovering() {
        return isRecovering;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeState nodeState = (NodeState) o;
        return isRecovering == nodeState.isRecovering &&
               Objects.equals(nodeId, nodeState.nodeId) &&
               Objects.equals(currentCount, nodeState.currentCount) &&
               consensusState == nodeState.consensusState &&
               Objects.equals(knownNodes, nodeState.knownNodes) &&
               Objects.equals(currentProposalId, nodeState.currentProposalId) &&
               Objects.equals(receivedVotes, nodeState.receivedVotes) &&
               Objects.equals(lastHeartbeat, nodeState.lastHeartbeat);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeId, currentCount, consensusState, knownNodes, 
                          currentProposalId, receivedVotes, lastHeartbeat, isRecovering);
    }
    
    @Override
    public String toString() {
        return "NodeState{" +
               "nodeId='" + nodeId + '\'' +
               ", currentCount=" + currentCount +
               ", consensusState=" + consensusState +
               ", knownNodes=" + knownNodes +
               ", currentProposalId='" + currentProposalId + '\'' +
               ", receivedVotes=" + receivedVotes +
               ", lastHeartbeat=" + lastHeartbeat +
               ", isRecovering=" + isRecovering +
               '}';
    }
}