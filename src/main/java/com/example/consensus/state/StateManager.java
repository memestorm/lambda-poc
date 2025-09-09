package com.example.consensus.state;

import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.NodeState;
import com.example.consensus.model.Vote;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Interface for managing Lambda node state and consensus tracking.
 * Handles state transitions, vote tracking, and node membership management.
 */
public interface StateManager {
    
    /**
     * Gets the current count value maintained by this node.
     * 
     * @return The current count value
     */
    Long getCurrentCount();
    
    /**
     * Updates the current count value after successful consensus.
     * 
     * @param newCount The new count value to set
     * @return true if update was successful, false otherwise
     */
    boolean updateCount(Long newCount);
    
    /**
     * Gets the current consensus state of this node.
     * 
     * @return Current consensus state (IDLE, PROPOSING, VOTING, etc.)
     */
    ConsensusState getConsensusState();
    
    /**
     * Transitions to a new consensus state.
     * 
     * @param newState The new consensus state to transition to
     * @return true if transition was valid and successful, false otherwise
     */
    boolean transitionToState(ConsensusState newState);
    
    /**
     * Gets the unique identifier for this Lambda node.
     * 
     * @return The node ID
     */
    String getNodeId();
    
    /**
     * Gets the set of all known nodes in the federation.
     * 
     * @return Set of known node IDs
     */
    Set<String> getKnownNodes();
    
    /**
     * Adds a new node to the known nodes set.
     * 
     * @param nodeId The ID of the node to add
     * @return true if node was added, false if already existed
     */
    boolean addKnownNode(String nodeId);
    
    /**
     * Removes a node from the known nodes set.
     * 
     * @param nodeId The ID of the node to remove
     * @return true if node was removed, false if didn't exist
     */
    boolean removeKnownNode(String nodeId);
    
    /**
     * Gets the current active proposal ID, if any.
     * 
     * @return The current proposal ID, or null if no active proposal
     */
    String getCurrentProposalId();
    
    /**
     * Sets the current active proposal ID.
     * 
     * @param proposalId The proposal ID to set as current
     */
    void setCurrentProposalId(String proposalId);
    
    /**
     * Records a vote received for the current proposal.
     * 
     * @param nodeId The ID of the node that cast the vote
     * @param vote The vote details (accept/reject with metadata)
     * @return true if vote was recorded, false otherwise
     */
    boolean recordVote(String nodeId, Vote vote);
    
    /**
     * Gets all votes received for the current proposal.
     * 
     * @return Map of node IDs to their votes
     */
    Map<String, Vote> getReceivedVotes();
    
    /**
     * Clears all recorded votes (typically after consensus completion).
     */
    void clearVotes();
    
    /**
     * Checks if this node has a quorum of votes for the current proposal.
     * 
     * @return true if quorum is achieved, false otherwise
     */
    boolean hasQuorum();
    
    /**
     * Checks if the majority of received votes are acceptances.
     * 
     * @return true if majority accepts, false otherwise
     */
    boolean hasMajorityAcceptance();
    
    /**
     * Updates the last heartbeat timestamp for this node.
     */
    void updateHeartbeat();
    
    /**
     * Gets the last heartbeat timestamp.
     * 
     * @return The last heartbeat timestamp
     */
    Instant getLastHeartbeat();
    
    /**
     * Checks if this node is currently in recovery mode.
     * 
     * @return true if recovering, false otherwise
     */
    boolean isRecovering();
    
    /**
     * Sets the recovery state of this node.
     * 
     * @param recovering true to enter recovery mode, false to exit
     */
    void setRecovering(boolean recovering);
    
    /**
     * Gets a complete snapshot of the current node state.
     * 
     * @return NodeState object containing all current state information
     */
    NodeState getNodeState();
    
    /**
     * Resets the node state to initial values (typically used during recovery).
     * 
     * @param preserveNodeId Whether to preserve the node ID during reset
     */
    void resetState(boolean preserveNodeId);
    
    /**
     * Validates that a state transition is allowed from current state.
     * 
     * @param targetState The state to transition to
     * @return true if transition is valid, false otherwise
     */
    boolean isValidTransition(ConsensusState targetState);
}