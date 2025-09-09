package com.example.consensus.messaging;

import com.example.consensus.model.ConsensusRequest;
import java.util.List;
import java.util.Optional;

/**
 * Interface for handling SQS message operations in the Lambda consensus federation.
 * Manages message sending, receiving, and processing for inter-Lambda communication.
 */
public interface SQSMessageHandler {
    
    /**
     * Sends a message to a specific Lambda node's queue.
     * 
     * @param targetNodeId The ID of the target Lambda node
     * @param message The consensus request message to send
     * @return true if message was sent successfully, false otherwise
     */
    boolean sendMessage(String targetNodeId, ConsensusRequest message);
    
    /**
     * Broadcasts a message to all known Lambda nodes in the federation.
     * 
     * @param message The consensus request message to broadcast
     * @return Number of nodes that successfully received the message
     */
    int broadcastMessage(ConsensusRequest message);
    
    /**
     * Polls for incoming messages from this node's SQS queue.
     * 
     * @param maxMessages Maximum number of messages to retrieve
     * @param waitTimeSeconds Long polling wait time in seconds
     * @return List of received consensus requests
     */
    List<ConsensusRequest> pollMessages(int maxMessages, int waitTimeSeconds);
    
    /**
     * Receives a single message from this node's SQS queue.
     * 
     * @param waitTimeSeconds Long polling wait time in seconds
     * @return Optional containing the received message, or empty if no message
     */
    Optional<ConsensusRequest> receiveMessage(int waitTimeSeconds);
    
    /**
     * Deletes a processed message from the SQS queue.
     * 
     * @param receiptHandle The receipt handle of the message to delete
     * @return true if message was deleted successfully, false otherwise
     */
    boolean deleteMessage(String receiptHandle);
    
    /**
     * Initializes SQS queues for this Lambda node and federation.
     * Creates necessary queues if they don't exist.
     * 
     * @return true if initialization was successful, false otherwise
     */
    boolean initializeQueues();
    
    /**
     * Gets the queue URL for a specific Lambda node.
     * 
     * @param nodeId The ID of the Lambda node
     * @return The SQS queue URL for the specified node
     */
    String getQueueUrl(String nodeId);
    
    /**
     * Checks if the SQS service is available and responsive.
     * 
     * @return true if SQS is available, false otherwise
     */
    boolean isServiceAvailable();
    
    /**
     * Sets the retry configuration for failed message operations.
     * 
     * @param maxRetries Maximum number of retry attempts
     * @param baseDelayMs Base delay in milliseconds for exponential backoff
     */
    void setRetryConfiguration(int maxRetries, long baseDelayMs);
    
    /**
     * Gets the current node ID for this Lambda instance.
     * 
     * @return The node ID of this Lambda instance
     */
    String getNodeId();
}