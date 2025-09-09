package com.example.consensus.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;

/**
 * Utility class for structured JSON logging across the consensus system.
 * Provides consistent logging format for all consensus operations, performance metrics,
 * and error tracking with contextual information.
 */
public class StructuredLogger {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private final Logger logger;
    private final String nodeId;
    
    public StructuredLogger(Class<?> clazz, String nodeId) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.nodeId = nodeId;
    }
    
    /**
     * Logs consensus operation events with structured data.
     */
    public void logConsensusOperation(ConsensusOperation operation, String proposalId, 
                                    Long proposedValue, String phase, Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "consensus_operation");
            logEntry.put("operation", operation.name());
            logEntry.put("proposalId", proposalId);
            logEntry.put("proposedValue", proposedValue);
            logEntry.put("phase", phase);
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log consensus operation", e);
        }
    }
    
    /**
     * Logs performance metrics for consensus operations.
     */
    public void logPerformanceMetrics(String operation, long durationMs, 
                                    int messageCount, Map<String, Object> metrics) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "performance_metrics");
            logEntry.put("operation", operation);
            logEntry.put("durationMs", durationMs);
            logEntry.put("messageCount", messageCount);
            
            if (metrics != null && !metrics.isEmpty()) {
                ObjectNode metricsNode = objectMapper.valueToTree(metrics);
                logEntry.set("metrics", metricsNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log performance metrics", e);
        }
    }
    
    /**
     * Logs error events with context and retry information.
     */
    public void logError(String operation, String errorMessage, Throwable throwable, 
                        int retryAttempt, int maxRetries, Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "error");
            logEntry.put("operation", operation);
            logEntry.put("errorMessage", errorMessage);
            logEntry.put("retryAttempt", retryAttempt);
            logEntry.put("maxRetries", maxRetries);
            
            if (throwable != null) {
                logEntry.put("exceptionType", throwable.getClass().getSimpleName());
                logEntry.put("stackTrace", getStackTraceString(throwable));
            }
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.error(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log error event", e);
        }
    }
    
    /**
     * Logs state transition events.
     */
    public void logStateTransition(String fromState, String toState, String reason, 
                                 Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "state_transition");
            logEntry.put("fromState", fromState);
            logEntry.put("toState", toState);
            logEntry.put("reason", reason);
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log state transition", e);
        }
    }
    
    /**
     * Logs message sending/receiving events.
     */
    public void logMessage(MessageDirection direction, String messageType, String sourceNode, 
                          String targetNode, String proposalId, Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "message");
            logEntry.put("direction", direction.name());
            logEntry.put("messageType", messageType);
            logEntry.put("sourceNode", sourceNode);
            logEntry.put("targetNode", targetNode);
            logEntry.put("proposalId", proposalId);
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log message event", e);
        }
    }
    
    /**
     * Logs recovery operation events.
     */
    public void logRecoveryOperation(RecoveryPhase phase, int attempt, int maxAttempts, 
                                   int responseCount, int requiredQuorum, Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "recovery_operation");
            logEntry.put("phase", phase.name());
            logEntry.put("attempt", attempt);
            logEntry.put("maxAttempts", maxAttempts);
            logEntry.put("responseCount", responseCount);
            logEntry.put("requiredQuorum", requiredQuorum);
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log recovery operation", e);
        }
    }
    
    /**
     * Logs node lifecycle events (startup, shutdown, etc.).
     */
    public void logNodeLifecycle(NodeLifecycleEvent event, Map<String, Object> context) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("timestamp", Instant.now().toString());
            logEntry.put("nodeId", nodeId);
            logEntry.put("eventType", "node_lifecycle");
            logEntry.put("lifecycleEvent", event.name());
            
            if (context != null && !context.isEmpty()) {
                ObjectNode contextNode = objectMapper.valueToTree(context);
                logEntry.set("context", contextNode);
            }
            
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log node lifecycle event", e);
        }
    }
    
    /**
     * Sets MDC context for thread-local logging context.
     */
    public void setMDCContext(String proposalId, String operation) {
        MDC.put("proposalId", proposalId);
        MDC.put("operation", operation);
        MDC.put("nodeId", nodeId);
    }
    
    /**
     * Clears MDC context.
     */
    public void clearMDCContext() {
        MDC.clear();
    }
    
    /**
     * Gets the underlying SLF4J logger for direct access when needed.
     */
    public Logger getLogger() {
        return logger;
    }
    
    /**
     * Converts throwable stack trace to string.
     */
    private String getStackTraceString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * Enum for consensus operation types.
     */
    public enum ConsensusOperation {
        INCREMENT_REQUEST,
        PROPOSE,
        VOTE,
        COMMIT,
        RECOVERY_REQUEST,
        RECOVERY_RESPONSE
    }
    
    /**
     * Enum for message direction.
     */
    public enum MessageDirection {
        SENT,
        RECEIVED
    }
    
    /**
     * Enum for recovery phases.
     */
    public enum RecoveryPhase {
        INITIATED,
        REQUESTS_SENT,
        WAITING_RESPONSES,
        PROCESSING_RESPONSES,
        COMPLETED,
        FAILED,
        RETRYING
    }
    
    /**
     * Enum for node lifecycle events.
     */
    public enum NodeLifecycleEvent {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
        ERROR
    }
}