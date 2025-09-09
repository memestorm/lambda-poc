package com.example.consensus.manager;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;

/**
 * Core interface for managing consensus operations in the Lambda federation.
 * Handles proposal initiation, voting, and commit phases of the consensus protocol.
 */
public interface ConsensusManager {
    
    /**
     * Processes an incoming consensus request and returns appropriate response.
     * 
     * @param request The consensus request to process
     * @return ConsensusResponse indicating the result of the operation
     */
    ConsensusResponse processRequest(ConsensusRequest request);
    
    /**
     * Initiates a new consensus proposal for incrementing the count.
     * 
     * @param proposedValue The new count value being proposed
     * @return true if proposal was successfully initiated, false otherwise
     */
    boolean initiateProposal(Long proposedValue);
    
    /**
     * Processes a vote received from another node during consensus.
     * 
     * @param proposalId The ID of the proposal being voted on
     * @param nodeId The ID of the node casting the vote
     * @param accept Whether the vote is an acceptance or rejection
     * @return true if vote was processed successfully, false otherwise
     */
    boolean processVote(String proposalId, String nodeId, boolean accept);
    
    /**
     * Commits a consensus decision after majority agreement is reached.
     * 
     * @param proposalId The ID of the proposal being committed
     * @param committedValue The value being committed
     * @return true if commit was successful, false otherwise
     */
    boolean commitDecision(String proposalId, Long committedValue);
    
    /**
     * Handles recovery requests from restarting nodes.
     * 
     * @param requestingNodeId The ID of the node requesting recovery
     * @return The current count value for recovery purposes
     */
    Long handleRecoveryRequest(String requestingNodeId);
    
    /**
     * Initiates recovery process for this node after restart.
     * 
     * @return true if recovery was successful, false otherwise
     */
    boolean initiateRecovery();
    
    /**
     * Gets the current consensus state of this node.
     * 
     * @return Current consensus state
     */
    String getCurrentState();
    
    /**
     * Checks if this node is currently participating in an active consensus.
     * 
     * @return true if actively participating in consensus, false otherwise
     */
    boolean isActiveInConsensus();
}