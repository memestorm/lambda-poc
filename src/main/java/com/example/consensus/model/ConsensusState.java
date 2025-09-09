package com.example.consensus.model;

/**
 * Enumeration of consensus states for Lambda nodes.
 */
public enum ConsensusState {
    /**
     * Node is not participating in any consensus operation.
     */
    IDLE,
    
    /**
     * Node has initiated a proposal and is waiting for votes.
     */
    PROPOSING,
    
    /**
     * Node is evaluating a received proposal and preparing to vote.
     */
    VOTING,
    
    /**
     * Node is applying a committed decision.
     */
    COMMITTING,
    
    /**
     * Node is synchronizing state after restart or failure.
     */
    RECOVERING
}