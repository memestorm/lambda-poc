package com.example.consensus.manager;

import com.example.consensus.logging.PerformanceTracker;
import com.example.consensus.logging.StructuredLogger;
import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.model.*;
import com.example.consensus.state.StateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Implementation of ConsensusManager that handles the consensus protocol
 * including recovery mechanisms for node failures and restarts.
 */
public class ConsensusManagerImpl implements ConsensusManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsensusManagerImpl.class);
    
    // Recovery configuration constants
    private static final int MINIMUM_QUORUM_SIZE = 3;
    private static final int RECOVERY_RETRY_INTERVAL_SECONDS = 30;
    private static final int RECOVERY_TIMEOUT_SECONDS = 10;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;
    
    private final StateManager stateManager;
    private final SQSMessageHandler messageHandler;
    private final ScheduledExecutorService scheduler;
    private final StructuredLogger structuredLogger;
    private final PerformanceTracker performanceTracker;
    
    // Recovery state tracking
    private final Map<String, RecoveryResponse> recoveryResponses = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Boolean> currentRecoveryFuture;
    private volatile int recoveryAttempts = 0;
    
    public ConsensusManagerImpl(StateManager stateManager, SQSMessageHandler messageHandler) {
        this.stateManager = stateManager;
        this.messageHandler = messageHandler;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.structuredLogger = new StructuredLogger(ConsensusManagerImpl.class, stateManager.getNodeId());
        this.performanceTracker = new PerformanceTracker(structuredLogger);
        
        logger.info("ConsensusManager initialized for node: {}", stateManager.getNodeId());
        
        // Log node lifecycle event
        structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STARTED, 
            Map.of(
                "nodeId", stateManager.getNodeId(),
                "consensusState", stateManager.getConsensusState().toString(),
                "currentCount", stateManager.getCurrentCount()
            ));
    }
    
    @Override
    public ConsensusResponse processRequest(ConsensusRequest request) {
        logger.debug("Processing request: {}", request);
        
        try {
            switch (request.getType()) {
                case RECOVERY_REQUEST:
                    return handleRecoveryRequestMessage(request);
                case RECOVERY_RESPONSE:
                    return handleRecoveryResponseMessage(request);
                case INCREMENT_REQUEST:
                    return handleIncrementRequest(request);
                case PROPOSE:
                    return handleProposal(request);
                case VOTE:
                    return handleVote(request);
                case COMMIT:
                    return handleCommit(request);
                default:
                    logger.warn("Unknown message type: {}", request.getType());
                    return createErrorResponse("Unknown message type: " + request.getType());
            }
        } catch (Exception e) {
            logger.error("Error processing request: {}", request, e);
            return createErrorResponse("Internal error: " + e.getMessage());
        }
    }
    
    @Override
    public boolean initiateProposal(Long proposedValue) {
        // Implementation for proposal initiation - placeholder for now
        logger.info("Initiating proposal for value: {}", proposedValue);
        return false; // Will be implemented in task 5
    }
    
    @Override
    public boolean processVote(String proposalId, String nodeId, boolean accept) {
        // Implementation for vote processing - placeholder for now
        logger.info("Processing vote from {}: {} for proposal {}", nodeId, accept, proposalId);
        return false; // Will be implemented in task 5
    }
    
    @Override
    public boolean commitDecision(String proposalId, Long committedValue) {
        // Implementation for commit processing - placeholder for now
        logger.info("Committing decision for proposal {}: {}", proposalId, committedValue);
        return false; // Will be implemented in task 5
    }
    
    @Override
    public Long handleRecoveryRequest(String requestingNodeId) {
        logger.info("Handling recovery request from node: {}", requestingNodeId);
        
        // Only respond if we're not in recovery ourselves
        if (stateManager.isRecovering()) {
            logger.warn("Cannot handle recovery request while in recovery mode");
            return null;
        }
        
        // Return current count to requesting node
        Long currentCount = stateManager.getCurrentCount();
        logger.debug("Responding to recovery request with count: {}", currentCount);
        
        return currentCount;
    }
    
    @Override
    public boolean initiateRecovery() {
        logger.info("Initiating recovery process for node: {}", stateManager.getNodeId());
        
        // Check if already recovering
        if (stateManager.isRecovering()) {
            logger.warn("Recovery already in progress");
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.FAILED,
                recoveryAttempts, MAX_RECOVERY_ATTEMPTS, 0, MINIMUM_QUORUM_SIZE,
                Map.of("reason", "Recovery already in progress")
            );
            return false;
        }
        
        // Log recovery initiation
        structuredLogger.logRecoveryOperation(
            StructuredLogger.RecoveryPhase.INITIATED,
            0, MAX_RECOVERY_ATTEMPTS, 0, MINIMUM_QUORUM_SIZE,
            Map.of(
                "currentCount", stateManager.getCurrentCount(),
                "consensusState", stateManager.getConsensusState().toString()
            )
        );
        
        // Set recovery state
        stateManager.setRecovering(true);
        stateManager.transitionToState(ConsensusState.RECOVERING);
        
        // Log state transition
        structuredLogger.logStateTransition(
            "IDLE", "RECOVERING", "Recovery initiated",
            Map.of("recoveryAttempts", 0, "maxAttempts", MAX_RECOVERY_ATTEMPTS)
        );
        
        // Reset recovery attempts
        recoveryAttempts = 0;
        
        // Start recovery process
        return performRecovery();
    }
    
    /**
     * Performs the actual recovery process with retry logic.
     */
    private boolean performRecovery() {
        PerformanceTracker.OperationTimer recoveryTimer = performanceTracker.startOperation(
            "recovery-" + recoveryAttempts, "RECOVERY");
        
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            logger.error("Maximum recovery attempts ({}) exceeded for node: {}", 
                        MAX_RECOVERY_ATTEMPTS, stateManager.getNodeId());
            
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.FAILED,
                recoveryAttempts, MAX_RECOVERY_ATTEMPTS, 0, MINIMUM_QUORUM_SIZE,
                Map.of("reason", "Maximum attempts exceeded")
            );
            
            structuredLogger.logError("performRecovery", "Maximum recovery attempts exceeded", 
                null, recoveryAttempts, MAX_RECOVERY_ATTEMPTS, Map.of());
            
            stateManager.setRecovering(false);
            stateManager.transitionToState(ConsensusState.IDLE);
            
            performanceTracker.recordOperation(recoveryTimer, false, 0, 
                Map.of("reason", "max_attempts_exceeded"));
            
            return false;
        }
        
        recoveryAttempts++;
        logger.info("Starting recovery attempt {} of {} for node: {}", 
                   recoveryAttempts, MAX_RECOVERY_ATTEMPTS, stateManager.getNodeId());
        
        structuredLogger.logRecoveryOperation(
            StructuredLogger.RecoveryPhase.REQUESTS_SENT,
            recoveryAttempts, MAX_RECOVERY_ATTEMPTS, 0, MINIMUM_QUORUM_SIZE,
            Map.of("attemptNumber", recoveryAttempts)
        );
        
        // Clear previous recovery responses
        recoveryResponses.clear();
        
        // Send recovery requests to all known nodes
        Set<String> knownNodes = stateManager.getKnownNodes();
        if (knownNodes.isEmpty()) {
            logger.warn("No known nodes for recovery - using default federation nodes");
            knownNodes = getDefaultFederationNodes();
        }
        
        int requestsSent = sendRecoveryRequests(knownNodes);
        recoveryTimer.addMessageCount(requestsSent);
        
        if (requestsSent == 0) {
            logger.error("Failed to send any recovery requests");
            structuredLogger.logError("performRecovery", "Failed to send recovery requests", 
                null, recoveryAttempts, MAX_RECOVERY_ATTEMPTS, 
                Map.of("knownNodesCount", knownNodes.size()));
            scheduleRecoveryRetry();
            performanceTracker.recordOperation(recoveryTimer, false, requestsSent, 
                Map.of("reason", "no_requests_sent"));
            return false;
        }
        
        // Wait for responses and process them
        CompletableFuture<Boolean> recoveryFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return waitForRecoveryResponses(requestsSent);
            } catch (Exception e) {
                logger.error("Error during recovery response processing", e);
                structuredLogger.logError("performRecovery", "Error during recovery response processing", 
                    e, recoveryAttempts, MAX_RECOVERY_ATTEMPTS, Map.of("requestsSent", requestsSent));
                return false;
            }
        }, scheduler);
        
        currentRecoveryFuture = recoveryFuture;
        
        // Handle recovery completion
        recoveryFuture.whenComplete((success, throwable) -> {
            if (throwable != null) {
                logger.error("Recovery failed with exception", throwable);
                structuredLogger.logError("performRecovery", "Recovery failed with exception", 
                    throwable, recoveryAttempts, MAX_RECOVERY_ATTEMPTS, Map.of());
                performanceTracker.recordOperation(recoveryTimer, false, recoveryTimer.getMessageCount(), 
                    Map.of("reason", "exception", "exceptionType", throwable.getClass().getSimpleName()));
                scheduleRecoveryRetry();
            } else if (success) {
                logger.info("Recovery completed successfully for node: {}", stateManager.getNodeId());
                
                structuredLogger.logRecoveryOperation(
                    StructuredLogger.RecoveryPhase.COMPLETED,
                    recoveryAttempts, MAX_RECOVERY_ATTEMPTS, recoveryResponses.size(), MINIMUM_QUORUM_SIZE,
                    Map.of(
                        "finalCount", stateManager.getCurrentCount(),
                        "responseCount", recoveryResponses.size()
                    )
                );
                
                structuredLogger.logStateTransition(
                    "RECOVERING", "IDLE", "Recovery completed successfully",
                    Map.of("finalCount", stateManager.getCurrentCount())
                );
                
                stateManager.setRecovering(false);
                stateManager.transitionToState(ConsensusState.IDLE);
                recoveryAttempts = 0;
                
                performanceTracker.recordOperation(recoveryTimer, true, recoveryTimer.getMessageCount(), 
                    Map.of("responseCount", recoveryResponses.size(), "finalCount", stateManager.getCurrentCount()));
            } else {
                logger.warn("Recovery attempt {} failed", recoveryAttempts);
                structuredLogger.logRecoveryOperation(
                    StructuredLogger.RecoveryPhase.FAILED,
                    recoveryAttempts, MAX_RECOVERY_ATTEMPTS, recoveryResponses.size(), MINIMUM_QUORUM_SIZE,
                    Map.of("reason", "Insufficient responses or invalid quorum")
                );
                performanceTracker.recordOperation(recoveryTimer, false, recoveryTimer.getMessageCount(), 
                    Map.of("reason", "insufficient_responses", "responseCount", recoveryResponses.size()));
                scheduleRecoveryRetry();
            }
        });
        
        return true;
    }
    
    /**
     * Sends recovery requests to all known nodes.
     */
    private int sendRecoveryRequests(Set<String> targetNodes) {
        String nodeId = stateManager.getNodeId();
        int successCount = 0;
        
        for (String targetNode : targetNodes) {
            if (targetNode.equals(nodeId)) {
                continue; // Don't send to ourselves
            }
            
            ConsensusRequest recoveryRequest = new ConsensusRequest(
                MessageType.RECOVERY_REQUEST,
                nodeId,
                targetNode,
                null, // No proposed value for recovery
                UUID.randomUUID().toString(), // Unique request ID
                Map.of("timestamp", Instant.now().toString())
            );
            
            try {
                if (messageHandler.sendMessage(targetNode, recoveryRequest)) {
                    successCount++;
                    logger.debug("Sent recovery request to node: {}", targetNode);
                } else {
                    logger.warn("Failed to send recovery request to node: {}", targetNode);
                }
            } catch (Exception e) {
                logger.error("Error sending recovery request to node: {}", targetNode, e);
            }
        }
        
        logger.info("Sent {} recovery requests out of {} target nodes", successCount, targetNodes.size());
        return successCount;
    }
    
    /**
     * Waits for recovery responses and processes them.
     */
    private boolean waitForRecoveryResponses(int expectedResponses) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = RECOVERY_TIMEOUT_SECONDS * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (recoveryResponses.size() >= MINIMUM_QUORUM_SIZE) {
                return processRecoveryResponses();
            }
            
            try {
                Thread.sleep(100); // Short sleep to avoid busy waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Recovery wait interrupted");
                return false;
            }
        }
        
        logger.warn("Recovery timeout - received {} responses, needed {}", 
                   recoveryResponses.size(), MINIMUM_QUORUM_SIZE);
        return false;
    }
    
    /**
     * Processes collected recovery responses and determines the correct count value.
     */
    private boolean processRecoveryResponses() {
        if (recoveryResponses.size() < MINIMUM_QUORUM_SIZE) {
            logger.warn("Insufficient recovery responses: {} < {}", 
                       recoveryResponses.size(), MINIMUM_QUORUM_SIZE);
            return false;
        }
        
        // Validate quorum based on available nodes
        Set<String> knownNodes = stateManager.getKnownNodes();
        int totalNodes = knownNodes.size() + 1; // +1 for this node
        
        if (!isValidQuorum(recoveryResponses.size(), totalNodes)) {
            logger.warn("Invalid quorum: {} responses from {} total nodes", 
                       recoveryResponses.size(), totalNodes);
            return false;
        }
        
        // Find majority count value
        Long majorityCount = findMajorityCount();
        if (majorityCount == null) {
            logger.error("Could not determine majority count from responses");
            return false;
        }
        
        // Adopt the majority count
        logger.info("Adopting majority count value: {} (from {} responses)", 
                   majorityCount, recoveryResponses.size());
        
        return stateManager.updateCount(majorityCount);
    }
    
    /**
     * Validates if the number of responses constitutes a valid quorum.
     */
    private boolean isValidQuorum(int responses, int totalNodes) {
        // Minimum quorum is 3 nodes
        if (responses < MINIMUM_QUORUM_SIZE) {
            return false;
        }
        
        // For different total node counts, apply different quorum rules
        if (totalNodes < 3) {
            return false; // Cannot have valid quorum with less than 3 total nodes
        } else if (totalNodes == 3) {
            return responses >= 2; // Need at least 2 out of 3
        } else if (totalNodes == 4) {
            return responses >= 3; // Need at least 3 out of 4
        } else { // 5 or more nodes
            return responses >= 3; // Need at least 3 responses
        }
    }
    
    /**
     * Finds the majority count value from recovery responses.
     */
    private Long findMajorityCount() {
        Map<Long, Integer> countFrequency = new HashMap<>();
        
        // Count frequency of each count value
        for (RecoveryResponse response : recoveryResponses.values()) {
            Long count = response.getCurrentCount();
            countFrequency.merge(count, 1, Integer::sum);
        }
        
        // Find the count with majority
        int totalResponses = recoveryResponses.size();
        int majorityThreshold = (totalResponses / 2) + 1;
        
        for (Map.Entry<Long, Integer> entry : countFrequency.entrySet()) {
            if (entry.getValue() >= majorityThreshold) {
                logger.debug("Found majority count: {} with {} votes out of {}", 
                           entry.getKey(), entry.getValue(), totalResponses);
                return entry.getKey();
            }
        }
        
        // If no clear majority, return the highest count (conservative approach)
        Long maxCount = countFrequency.keySet().stream()
            .max(Long::compareTo)
            .orElse(null);
            
        logger.warn("No clear majority found, using highest count: {}", maxCount);
        return maxCount;
    }
    
    /**
     * Schedules a retry of the recovery process.
     */
    private void scheduleRecoveryRetry() {
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            logger.error("Maximum recovery attempts exceeded, giving up");
            stateManager.setRecovering(false);
            stateManager.transitionToState(ConsensusState.IDLE);
            return;
        }
        
        logger.info("Scheduling recovery retry in {} seconds", RECOVERY_RETRY_INTERVAL_SECONDS);
        
        scheduler.schedule(() -> {
            if (stateManager.isRecovering()) {
                performRecovery();
            }
        }, RECOVERY_RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * Handles incoming recovery request messages.
     */
    private ConsensusResponse handleRecoveryRequestMessage(ConsensusRequest request) {
        String requestingNodeId = request.getSourceNodeId();
        logger.info("Received recovery request from node: {}", requestingNodeId);
        
        Long currentCount = handleRecoveryRequest(requestingNodeId);
        if (currentCount == null) {
            return createErrorResponse("Cannot provide recovery data while in recovery");
        }
        
        // Send recovery response back to requesting node
        ConsensusRequest response = new ConsensusRequest(
            MessageType.RECOVERY_RESPONSE,
            stateManager.getNodeId(),
            requestingNodeId,
            currentCount,
            request.getProposalId(), // Use same ID for correlation
            Map.of(
                "timestamp", Instant.now().toString(),
                "consensusState", stateManager.getConsensusState().toString()
            )
        );
        
        try {
            messageHandler.sendMessage(requestingNodeId, response);
            logger.debug("Sent recovery response to node: {} with count: {}", requestingNodeId, currentCount);
        } catch (Exception e) {
            logger.error("Failed to send recovery response to node: {}", requestingNodeId, e);
        }
        
        return createSuccessResponse("Recovery response sent", currentCount);
    }
    
    /**
     * Handles incoming recovery response messages.
     */
    private ConsensusResponse handleRecoveryResponseMessage(ConsensusRequest request) {
        String respondingNodeId = request.getSourceNodeId();
        Long reportedCount = request.getProposedValue();
        
        logger.debug("Received recovery response from node: {} with count: {}", 
                    respondingNodeId, reportedCount);
        
        // Only process if we're currently recovering
        if (!stateManager.isRecovering()) {
            logger.warn("Received recovery response while not in recovery mode");
            return createErrorResponse("Not in recovery mode");
        }
        
        // Store the recovery response
        RecoveryResponse recoveryResponse = new RecoveryResponse(
            respondingNodeId,
            reportedCount,
            request.getMetadata() != null ? 
                request.getMetadata().get("consensusState") : null,
            Instant.now()
        );
        
        recoveryResponses.put(respondingNodeId, recoveryResponse);
        
        logger.debug("Stored recovery response from node: {} (total responses: {})", 
                    respondingNodeId, recoveryResponses.size());
        
        return createSuccessResponse("Recovery response processed", reportedCount);
    }
    
    /**
     * Gets the default set of federation node IDs.
     */
    private Set<String> getDefaultFederationNodes() {
        return Set.of("lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5");
    }
    
    // Placeholder implementations for other consensus operations
    private ConsensusResponse handleIncrementRequest(ConsensusRequest request) {
        logger.info("Handling increment request - placeholder implementation");
        return createSuccessResponse("Increment request received", stateManager.getCurrentCount());
    }
    
    private ConsensusResponse handleProposal(ConsensusRequest request) {
        logger.info("Handling proposal - placeholder implementation");
        return createSuccessResponse("Proposal received", stateManager.getCurrentCount());
    }
    
    private ConsensusResponse handleVote(ConsensusRequest request) {
        logger.info("Handling vote - placeholder implementation");
        return createSuccessResponse("Vote received", stateManager.getCurrentCount());
    }
    
    private ConsensusResponse handleCommit(ConsensusRequest request) {
        logger.info("Handling commit - placeholder implementation");
        return createSuccessResponse("Commit received", stateManager.getCurrentCount());
    }
    
    @Override
    public String getCurrentState() {
        return String.format("Node: %s, Count: %d, State: %s, Recovering: %s",
            stateManager.getNodeId(),
            stateManager.getCurrentCount(),
            stateManager.getConsensusState(),
            stateManager.isRecovering());
    }
    
    @Override
    public boolean isActiveInConsensus() {
        ConsensusState state = stateManager.getConsensusState();
        return state == ConsensusState.PROPOSING || 
               state == ConsensusState.VOTING || 
               state == ConsensusState.COMMITTING;
    }
    
    /**
     * Creates a success response.
     */
    private ConsensusResponse createSuccessResponse(String message, Long currentValue) {
        return new ConsensusResponse(
            true,
            currentValue,
            stateManager.getNodeId(),
            message,
            stateManager.getConsensusState()
        );
    }
    
    /**
     * Creates an error response.
     */
    private ConsensusResponse createErrorResponse(String message) {
        return new ConsensusResponse(
            false,
            stateManager.getCurrentCount(),
            stateManager.getNodeId(),
            message,
            stateManager.getConsensusState()
        );
    }
    
    /**
     * Internal class to represent recovery response data.
     */
    private static class RecoveryResponse {
        private final String nodeId;
        private final Long currentCount;
        private final Object consensusState;
        private final Instant timestamp;
        
        public RecoveryResponse(String nodeId, Long currentCount, Object consensusState, Instant timestamp) {
            this.nodeId = nodeId;
            this.currentCount = currentCount;
            this.consensusState = consensusState;
            this.timestamp = timestamp;
        }
        
        public String getNodeId() { return nodeId; }
        public Long getCurrentCount() { return currentCount; }
        public Object getConsensusState() { return consensusState; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Gracefully shuts down the consensus manager and its resources.
     */
    public void shutdown() {
        structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STOPPING, 
            Map.of("nodeId", stateManager.getNodeId(), "activeRecoveries", recoveryResponses.size()));
        
        // Cancel any ongoing recovery
        if (currentRecoveryFuture != null && !currentRecoveryFuture.isDone()) {
            currentRecoveryFuture.cancel(true);
        }
        
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Clear recovery state
        recoveryResponses.clear();
        
        logger.info("ConsensusManager shutdown completed for node: {}", stateManager.getNodeId());
    }
}