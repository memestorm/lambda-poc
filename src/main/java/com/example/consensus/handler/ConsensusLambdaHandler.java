package com.example.consensus.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.consensus.logging.PerformanceTracker;
import com.example.consensus.logging.StructuredLogger;
import com.example.consensus.manager.ConsensusManager;
import com.example.consensus.manager.ConsensusManagerImpl;
import com.example.consensus.messaging.SQSMessageHandler;
import com.example.consensus.messaging.SQSMessageHandlerImpl;
import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.ConsensusResponse;
import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.MessageType;
import com.example.consensus.state.StateManager;
import com.example.consensus.state.StateManagerImpl;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AWS Lambda handler for processing consensus requests in the federation.
 * This class serves as the entry point for Lambda function invocations and routes
 * requests based on message type to appropriate consensus operations.
 */
public class ConsensusLambdaHandler implements RequestHandler<ConsensusRequest, ConsensusResponse> {
    
    private final ConsensusManager consensusManager;
    private final SQSMessageHandler sqsHandler;
    private final StateManager stateManager;
    private final StructuredLogger structuredLogger;
    private final PerformanceTracker performanceTracker;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // Shutdown hook for graceful cleanup
    private final Thread shutdownHook = new Thread(this::performGracefulShutdown);
    
    /**
     * Constructor for dependency injection.
     * Used primarily for testing with mock dependencies.
     */
    public ConsensusLambdaHandler(
            ConsensusManager consensusManager,
            SQSMessageHandler sqsHandler,
            StateManager stateManager) {
        this.consensusManager = consensusManager;
        this.sqsHandler = sqsHandler;
        this.stateManager = stateManager;
        this.structuredLogger = new StructuredLogger(ConsensusLambdaHandler.class, 
            stateManager != null ? stateManager.getNodeId() : "unknown");
        this.performanceTracker = new PerformanceTracker(structuredLogger);
    }
    
    /**
     * Default constructor for Lambda runtime.
     * Creates instances of required components using environment configuration.
     */
    public ConsensusLambdaHandler() {
        // Get node ID from environment variable or use default
        String nodeId = System.getenv("NODE_ID");
        if (nodeId == null || nodeId.trim().isEmpty()) {
            nodeId = "lambda-node-" + System.currentTimeMillis();
        }
        
        // Initialize StateManager first as it's needed by ConsensusManager
        this.stateManager = new StateManagerImpl(nodeId);
        
        // Initialize logging components
        this.structuredLogger = new StructuredLogger(ConsensusLambdaHandler.class, nodeId);
        this.performanceTracker = new PerformanceTracker(structuredLogger);
        
        // Log node startup
        structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STARTING, 
            Map.of("nodeId", nodeId));
        
        // Initialize optimized SQSMessageHandler
        this.sqsHandler = new SQSMessageHandlerImpl(nodeId);
        
        // Initialize ConsensusManager with dependencies
        this.consensusManager = new ConsensusManagerImpl(stateManager, sqsHandler);
        
        // Register shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        
        // Log successful startup
        structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STARTED, 
            Map.of("nodeId", nodeId, "consensusState", stateManager.getConsensusState().toString()));
    }
    
    @Override
    public ConsensusResponse handleRequest(ConsensusRequest request, Context context) {
        LambdaLogger logger = context.getLogger();
        PerformanceTracker.OperationTimer timer = null;
        
        try {
            // Validate request first
            if (request == null) {
                logger.log("Processing null request");
                structuredLogger.logError("handleRequest", "Invalid request: request is null", 
                    null, 0, 0, Map.of());
                return createErrorResponse("Invalid request: request is null", null);
            }
            if (request.getType() == null) {
                logger.log("Processing request with null type");
                structuredLogger.logError("handleRequest", "Invalid request: missing type", 
                    null, 0, 0, Map.of("sourceNode", request.getSourceNodeId()));
                return createErrorResponse("Invalid request: missing type", null);
            }
            
            // Start performance tracking
            timer = performanceTracker.startOperation(
                request.getProposalId() != null ? request.getProposalId() : "unknown",
                request.getType().toString()
            );
            
            // Set MDC context for logging
            structuredLogger.setMDCContext(request.getProposalId(), request.getType().toString());
            
            // Log incoming message
            structuredLogger.logMessage(
                StructuredLogger.MessageDirection.RECEIVED,
                request.getType().toString(),
                request.getSourceNodeId(),
                request.getTargetNodeId(),
                request.getProposalId(),
                Map.of(
                    "proposedValue", request.getProposedValue() != null ? request.getProposedValue().toString() : "null",
                    "hasMetadata", request.getMetadata() != null && !request.getMetadata().isEmpty()
                )
            );
            
            // Log incoming request (legacy format for compatibility)
            logger.log(String.format("Processing request: type=%s, source=%s, target=%s, proposalId=%s", 
                request.getType(), 
                request.getSourceNodeId(), 
                request.getTargetNodeId(), 
                request.getProposalId()));
            
            // Route request based on message type
            ConsensusResponse response = routeRequest(request, logger, timer);
            
            // Record performance metrics
            if (timer != null) {
                performanceTracker.recordOperation(timer, response.isSuccess(), 
                    timer.getMessageCount(), Map.of(
                        "responseMessage", response.getMessage(),
                        "finalState", response.getState().toString()
                    ));
            }
            
            // Log response
            logger.log(String.format("Request processed: success=%s, message=%s, state=%s", 
                response.isSuccess(), 
                response.getMessage(), 
                response.getState()));
            
            return response;
            
        } catch (Exception e) {
            logger.log("Error processing request: " + e.getMessage());
            
            // Log structured error
            structuredLogger.logError("handleRequest", "Internal error processing request", 
                e, 0, 0, Map.of(
                    "requestType", request != null && request.getType() != null ? request.getType().toString() : "unknown",
                    "sourceNode", request != null ? request.getSourceNodeId() : "unknown"
                ));
            
            // Record failed operation
            if (timer != null) {
                performanceTracker.recordOperation(timer, false, timer.getMessageCount(), 
                    Map.of("errorType", e.getClass().getSimpleName()));
            }
            
            return createErrorResponse("Internal error: " + e.getMessage(), e);
        } finally {
            // Clear MDC context
            structuredLogger.clearMDCContext();
        }
    }
    
    /**
     * Routes the request to appropriate handler based on message type.
     * 
     * @param request The consensus request to route
     * @param logger Lambda logger for debugging
     * @param timer Performance timer for tracking
     * @return ConsensusResponse from the appropriate handler
     */
    private ConsensusResponse routeRequest(ConsensusRequest request, LambdaLogger logger, 
                                         PerformanceTracker.OperationTimer timer) {
        MessageType type = request.getType();
        
        try {
            switch (type) {
                case INCREMENT_REQUEST:
                    return handleIncrementRequest(request, logger, timer);
                    
                case PROPOSE:
                    return handleProposal(request, logger, timer);
                    
                case VOTE:
                    return handleVote(request, logger, timer);
                    
                case COMMIT:
                    return handleCommit(request, logger, timer);
                    
                case RECOVERY_REQUEST:
                    return handleRecoveryRequest(request, logger, timer);
                    
                case RECOVERY_RESPONSE:
                    return handleRecoveryResponse(request, logger, timer);
                    
                default:
                    logger.log("Unknown message type: " + type);
                    structuredLogger.logError("routeRequest", "Unknown message type: " + type, 
                        null, 0, 0, Map.of("messageType", type.toString()));
                    return createErrorResponse("Unknown message type: " + type, null);
            }
        } catch (Exception e) {
            logger.log("Error routing request type " + type + ": " + e.getMessage());
            structuredLogger.logError("routeRequest", "Error routing request", e, 0, 0, 
                Map.of("messageType", type.toString()));
            return createErrorResponse("Error processing " + type + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles INCREMENT_REQUEST messages by initiating consensus for count increment.
     */
    private ConsensusResponse handleIncrementRequest(ConsensusRequest request, LambdaLogger logger, 
                                                   PerformanceTracker.OperationTimer timer) {
        logger.log("Handling increment request");
        
        try {
            // Get current count and propose increment
            Long currentCount = stateManager.getCurrentCount();
            Long proposedValue = currentCount + 1;
            
            logger.log(String.format("Proposing increment from %d to %d", currentCount, proposedValue));
            
            // Log consensus operation
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.INCREMENT_REQUEST,
                request.getProposalId(),
                proposedValue,
                "initiate",
                Map.of(
                    "currentCount", currentCount,
                    "proposedValue", proposedValue
                )
            );
            
            // Initiate consensus proposal
            boolean success = consensusManager.initiateProposal(proposedValue);
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the proposal initiation
            }
            
            if (success) {
                structuredLogger.logConsensusOperation(
                    StructuredLogger.ConsensusOperation.INCREMENT_REQUEST,
                    request.getProposalId(),
                    proposedValue,
                    "initiated",
                    Map.of("success", true)
                );
                return createSuccessResponse("Increment proposal initiated", proposedValue);
            } else {
                structuredLogger.logError("handleIncrementRequest", "Failed to initiate increment proposal", 
                    null, 0, 0, Map.of("proposedValue", proposedValue));
                return createErrorResponse("Failed to initiate increment proposal", null);
            }
            
        } catch (Exception e) {
            logger.log("Error handling increment request: " + e.getMessage());
            structuredLogger.logError("handleIncrementRequest", "Error processing increment", e, 0, 0, 
                Map.of("proposalId", request.getProposalId()));
            return createErrorResponse("Error processing increment: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles PROPOSE messages by processing consensus proposals from other nodes.
     */
    private ConsensusResponse handleProposal(ConsensusRequest request, LambdaLogger logger, 
                                           PerformanceTracker.OperationTimer timer) {
        logger.log(String.format("Handling proposal: proposalId=%s, value=%d", 
            request.getProposalId(), request.getProposedValue()));
        
        try {
            // Log consensus operation
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.PROPOSE,
                request.getProposalId(),
                request.getProposedValue(),
                "received",
                Map.of(
                    "sourceNode", request.getSourceNodeId(),
                    "currentCount", stateManager.getCurrentCount()
                )
            );
            
            // Delegate to consensus manager
            ConsensusResponse response = consensusManager.processRequest(request);
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the proposal processing
            }
            
            // Log the result
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.PROPOSE,
                request.getProposalId(),
                request.getProposedValue(),
                "processed",
                Map.of(
                    "success", response.isSuccess(),
                    "responseMessage", response.getMessage()
                )
            );
            
            return response;
            
        } catch (Exception e) {
            logger.log("Error handling proposal: " + e.getMessage());
            structuredLogger.logError("handleProposal", "Error processing proposal", e, 0, 0, 
                Map.of("proposalId", request.getProposalId(), "sourceNode", request.getSourceNodeId()));
            return createErrorResponse("Error processing proposal: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles VOTE messages by processing votes from other nodes.
     */
    private ConsensusResponse handleVote(ConsensusRequest request, LambdaLogger logger, 
                                       PerformanceTracker.OperationTimer timer) {
        logger.log(String.format("Handling vote: proposalId=%s, from=%s", 
            request.getProposalId(), request.getSourceNodeId()));
        
        try {
            // Extract vote decision from metadata
            boolean accept = extractVoteDecision(request);
            
            // Log consensus operation
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.VOTE,
                request.getProposalId(),
                request.getProposedValue(),
                "received",
                Map.of(
                    "sourceNode", request.getSourceNodeId(),
                    "voteDecision", accept
                )
            );
            
            // Process the vote
            boolean success = consensusManager.processVote(
                request.getProposalId(), 
                request.getSourceNodeId(), 
                accept);
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the vote processing
            }
            
            // Log the result
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.VOTE,
                request.getProposalId(),
                request.getProposedValue(),
                "processed",
                Map.of(
                    "success", success,
                    "voteDecision", accept,
                    "sourceNode", request.getSourceNodeId()
                )
            );
            
            if (success) {
                return createSuccessResponse("Vote processed", stateManager.getCurrentCount());
            } else {
                structuredLogger.logError("handleVote", "Failed to process vote", null, 0, 0, 
                    Map.of("proposalId", request.getProposalId(), "sourceNode", request.getSourceNodeId()));
                return createErrorResponse("Failed to process vote", null);
            }
            
        } catch (Exception e) {
            logger.log("Error handling vote: " + e.getMessage());
            structuredLogger.logError("handleVote", "Error processing vote", e, 0, 0, 
                Map.of("proposalId", request.getProposalId(), "sourceNode", request.getSourceNodeId()));
            return createErrorResponse("Error processing vote: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles COMMIT messages by applying consensus decisions.
     */
    private ConsensusResponse handleCommit(ConsensusRequest request, LambdaLogger logger, 
                                         PerformanceTracker.OperationTimer timer) {
        logger.log(String.format("Handling commit: proposalId=%s, value=%d", 
            request.getProposalId(), request.getProposedValue()));
        
        try {
            Long previousCount = stateManager.getCurrentCount();
            
            // Log consensus operation
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.COMMIT,
                request.getProposalId(),
                request.getProposedValue(),
                "received",
                Map.of(
                    "sourceNode", request.getSourceNodeId(),
                    "previousCount", previousCount
                )
            );
            
            // Commit the decision
            boolean success = consensusManager.commitDecision(
                request.getProposalId(), 
                request.getProposedValue());
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the commit processing
            }
            
            // Log the result
            structuredLogger.logConsensusOperation(
                StructuredLogger.ConsensusOperation.COMMIT,
                request.getProposalId(),
                request.getProposedValue(),
                "processed",
                Map.of(
                    "success", success,
                    "previousCount", previousCount,
                    "newCount", stateManager.getCurrentCount()
                )
            );
            
            if (success) {
                return createSuccessResponse("Decision committed", request.getProposedValue());
            } else {
                structuredLogger.logError("handleCommit", "Failed to commit decision", null, 0, 0, 
                    Map.of("proposalId", request.getProposalId(), "proposedValue", request.getProposedValue()));
                return createErrorResponse("Failed to commit decision", null);
            }
            
        } catch (Exception e) {
            logger.log("Error handling commit: " + e.getMessage());
            structuredLogger.logError("handleCommit", "Error processing commit", e, 0, 0, 
                Map.of("proposalId", request.getProposalId(), "proposedValue", request.getProposedValue()));
            return createErrorResponse("Error processing commit: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles RECOVERY_REQUEST messages by providing current state to recovering nodes.
     */
    private ConsensusResponse handleRecoveryRequest(ConsensusRequest request, LambdaLogger logger, 
                                                  PerformanceTracker.OperationTimer timer) {
        logger.log(String.format("Handling recovery request from node: %s", request.getSourceNodeId()));
        
        try {
            // Log recovery operation
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.INITIATED,
                0, 0, 0, 0,
                Map.of(
                    "requestingNode", request.getSourceNodeId(),
                    "currentCount", stateManager.getCurrentCount(),
                    "consensusState", stateManager.getConsensusState().toString()
                )
            );
            
            // Get current count for recovery
            Long currentCount = consensusManager.handleRecoveryRequest(request.getSourceNodeId());
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the recovery request processing
            }
            
            // Log successful recovery response
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.COMPLETED,
                0, 0, 1, 1,
                Map.of(
                    "requestingNode", request.getSourceNodeId(),
                    "providedCount", currentCount != null ? currentCount : "null"
                )
            );
            
            return createSuccessResponse("Recovery data provided", currentCount);
            
        } catch (Exception e) {
            logger.log("Error handling recovery request: " + e.getMessage());
            structuredLogger.logError("handleRecoveryRequest", "Error processing recovery request", e, 0, 0, 
                Map.of("requestingNode", request.getSourceNodeId()));
            return createErrorResponse("Error processing recovery request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles RECOVERY_RESPONSE messages during node recovery process.
     */
    private ConsensusResponse handleRecoveryResponse(ConsensusRequest request, LambdaLogger logger, 
                                                   PerformanceTracker.OperationTimer timer) {
        logger.log(String.format("Handling recovery response from node: %s, value=%d", 
            request.getSourceNodeId(), request.getProposedValue()));
        
        try {
            // Log recovery operation
            structuredLogger.logRecoveryOperation(
                StructuredLogger.RecoveryPhase.PROCESSING_RESPONSES,
                0, 0, 1, 0,
                Map.of(
                    "respondingNode", request.getSourceNodeId(),
                    "reportedCount", request.getProposedValue() != null ? request.getProposedValue() : "null",
                    "isRecovering", stateManager.isRecovering()
                )
            );
            
            // Delegate to consensus manager for recovery processing
            ConsensusResponse response = consensusManager.processRequest(request);
            
            if (timer != null) {
                timer.incrementMessageCount(); // Count the recovery response processing
            }
            
            // Log the result
            structuredLogger.logRecoveryOperation(
                response.isSuccess() ? StructuredLogger.RecoveryPhase.COMPLETED : StructuredLogger.RecoveryPhase.FAILED,
                0, 0, 1, 0,
                Map.of(
                    "respondingNode", request.getSourceNodeId(),
                    "success", response.isSuccess(),
                    "responseMessage", response.getMessage()
                )
            );
            
            return response;
            
        } catch (Exception e) {
            logger.log("Error handling recovery response: " + e.getMessage());
            structuredLogger.logError("handleRecoveryResponse", "Error processing recovery response", e, 0, 0, 
                Map.of("respondingNode", request.getSourceNodeId(), "reportedCount", request.getProposedValue()));
            return createErrorResponse("Error processing recovery response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extracts vote decision from request metadata.
     * 
     * @param request The vote request
     * @return true if vote is accept, false if reject
     */
    private boolean extractVoteDecision(ConsensusRequest request) {
        if (request.getMetadata() != null && request.getMetadata().containsKey("accept")) {
            Object acceptValue = request.getMetadata().get("accept");
            if (acceptValue instanceof Boolean) {
                return (Boolean) acceptValue;
            }
            if (acceptValue instanceof String) {
                return Boolean.parseBoolean((String) acceptValue);
            }
        }
        // Default to reject if vote decision cannot be determined
        return false;
    }
    
    /**
     * Creates a successful response with the current node state.
     * 
     * @param message Success message
     * @param currentValue Current count value
     * @return ConsensusResponse indicating success
     */
    private ConsensusResponse createSuccessResponse(String message, Long currentValue) {
        return new ConsensusResponse(
            true,
            currentValue != null ? currentValue : stateManager.getCurrentCount(),
            stateManager.getNodeId(),
            message,
            stateManager.getConsensusState()
        );
    }
    
    /**
     * Creates an error response with appropriate error information.
     * 
     * @param message Error message
     * @param exception Optional exception that caused the error
     * @return ConsensusResponse indicating failure
     */
    private ConsensusResponse createErrorResponse(String message, Exception exception) {
        String fullMessage = message;
        if (exception != null) {
            fullMessage += " (" + exception.getClass().getSimpleName() + ")";
        }
        
        try {
            return new ConsensusResponse(
                false,
                stateManager != null ? stateManager.getCurrentCount() : null,
                stateManager != null ? stateManager.getNodeId() : "unknown",
                fullMessage,
                stateManager != null ? stateManager.getConsensusState() : ConsensusState.IDLE
            );
        } catch (Exception e) {
            // If even creating the error response fails, return a minimal response
            return new ConsensusResponse(
                false,
                null,
                "unknown",
                fullMessage,
                ConsensusState.IDLE
            );
        }
    }
    
    /**
     * Gets the consensus manager instance.
     * 
     * @return The consensus manager
     */
    protected ConsensusManager getConsensusManager() {
        return consensusManager;
    }
    
    /**
     * Gets the SQS message handler instance.
     * 
     * @return The SQS message handler
     */
    protected SQSMessageHandler getSqsHandler() {
        return sqsHandler;
    }
    
    /**
     * Gets the state manager instance.
     * 
     * @return The state manager
     */
    protected StateManager getStateManager() {
        return stateManager;
    }
    
    /**
     * Performs graceful shutdown of all resources.
     * This method is called by the shutdown hook to ensure clean resource cleanup.
     */
    private void performGracefulShutdown() {
        if (isShuttingDown.compareAndSet(false, true)) {
            structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STOPPING, 
                Map.of("nodeId", stateManager.getNodeId()));
            
            try {
                // Stop accepting new requests
                if (consensusManager instanceof ConsensusManagerImpl) {
                    ((ConsensusManagerImpl) consensusManager).shutdown();
                }
                
                // Shutdown SQS handler and its resources
                if (sqsHandler instanceof SQSMessageHandlerImpl) {
                    ((SQSMessageHandlerImpl) sqsHandler).shutdown();
                }
                
                // Log final performance metrics
                if (sqsHandler instanceof SQSMessageHandlerImpl) {
                    Map<String, Object> metrics = ((SQSMessageHandlerImpl) sqsHandler).getPerformanceMetrics();
                    structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STOPPED, 
                        Map.of("nodeId", stateManager.getNodeId(), "finalMetrics", metrics));
                }
                
            } catch (Exception e) {
                structuredLogger.logError("performGracefulShutdown", "Error during shutdown", e, 0, 0, 
                    Map.of("nodeId", stateManager.getNodeId()));
            }
        }
    }
    
    /**
     * Checks if the handler is currently shutting down.
     * 
     * @return true if shutdown is in progress, false otherwise
     */
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    /**
     * Manually triggers graceful shutdown.
     * Useful for testing or explicit shutdown scenarios.
     */
    public void shutdown() {
        performGracefulShutdown();
    }
}