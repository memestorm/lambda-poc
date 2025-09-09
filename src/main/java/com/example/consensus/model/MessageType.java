package com.example.consensus.model;

/**
 * Enumeration of message types used in the consensus protocol.
 */
public enum MessageType {
    /**
     * External trigger to increment the count value.
     */
    INCREMENT_REQUEST,
    
    /**
     * Consensus proposal message sent to initiate voting.
     */
    PROPOSE,
    
    /**
     * Vote response message (accept or reject).
     */
    VOTE,
    
    /**
     * Commit decision message sent after consensus is reached.
     */
    COMMIT,
    
    /**
     * Request for current state during node recovery.
     */
    RECOVERY_REQUEST,
    
    /**
     * Response containing current state for recovery purposes.
     */
    RECOVERY_RESPONSE
}