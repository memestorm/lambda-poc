package com.example.consensus.logging;

import java.util.Map;

/**
 * Example demonstrating the structured logging capabilities of the consensus system.
 * This class shows how to use the logging infrastructure for monitoring and debugging.
 */
public class LoggingExample {
    
    public static void main(String[] args) {
        String nodeId = "example-node-1";
        StructuredLogger logger = new StructuredLogger(LoggingExample.class, nodeId);
        PerformanceTracker tracker = new PerformanceTracker(logger);
        
        System.out.println("=== Consensus System Logging Example ===");
        System.out.println("This example demonstrates structured JSON logging for consensus operations.");
        System.out.println("Check the console output for JSON log entries.\n");
        
        // Example 1: Node lifecycle logging
        logger.logNodeLifecycle(
            StructuredLogger.NodeLifecycleEvent.STARTING,
            Map.of("nodeId", nodeId, "initialCount", 0L)
        );
        
        // Example 2: Consensus operation logging
        String proposalId = "example-proposal-123";
        logger.logConsensusOperation(
            StructuredLogger.ConsensusOperation.INCREMENT_REQUEST,
            proposalId,
            42L,
            "initiate",
            Map.of(
                "currentCount", 41L,
                "proposedValue", 42L,
                "sourceNode", nodeId
            )
        );
        
        // Example 3: Performance tracking
        PerformanceTracker.OperationTimer timer = tracker.startOperation(proposalId, "CONSENSUS");
        
        // Simulate some work
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        timer.addMessageCount(3); // Simulate sending 3 messages
        tracker.recordOperation(timer, true, 3, Map.of(
            "consensusPhase", "completed",
            "participantCount", 5
        ));
        
        // Example 4: State transition logging
        logger.logStateTransition(
            "IDLE", "PROPOSING", "Consensus proposal initiated",
            Map.of(
                "proposalId", proposalId,
                "currentCount", 42L
            )
        );
        
        // Example 5: Message logging
        logger.logMessage(
            StructuredLogger.MessageDirection.SENT,
            "PROPOSE",
            nodeId,
            "target-node-2",
            proposalId,
            Map.of("proposedValue", 42L, "timeout", 10000)
        );
        
        // Example 6: Recovery operation logging
        logger.logRecoveryOperation(
            StructuredLogger.RecoveryPhase.INITIATED,
            1, 3, 0, 3,
            Map.of(
                "reason", "Node restart detected",
                "lastKnownCount", 41L
            )
        );
        
        // Example 7: Error logging
        logger.logError(
            "exampleOperation",
            "Simulated error for demonstration",
            new RuntimeException("Example exception"),
            1, 3,
            Map.of(
                "proposalId", proposalId,
                "errorContext", "demonstration"
            )
        );
        
        // Example 8: Final lifecycle event
        logger.logNodeLifecycle(
            StructuredLogger.NodeLifecycleEvent.STOPPED,
            Map.of("finalCount", 42L, "reason", "Example completed")
        );
        
        System.out.println("\n=== Example Completed ===");
        System.out.println("All log entries above are in structured JSON format.");
        System.out.println("In a real deployment, these would be written to log files");
        System.out.println("and could be analyzed using the LogAnalyzer utilities.");
        
        // Show performance metrics
        PerformanceTracker.OperationMetrics metrics = tracker.getMetrics("CONSENSUS");
        if (metrics != null) {
            System.out.println("\nPerformance Metrics:");
            System.out.println("- Total operations: " + metrics.getTotalOperations());
            System.out.println("- Success rate: " + String.format("%.2f%%", metrics.getSuccessRate() * 100));
            System.out.println("- Average duration: " + String.format("%.2f ms", metrics.getAverageDurationMs()));
            System.out.println("- Average message count: " + String.format("%.2f", metrics.getAverageMessageCount()));
        }
    }
}