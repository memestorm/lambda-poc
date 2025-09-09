package com.example.consensus.logging;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks performance metrics for consensus operations including timing,
 * message counts, and success/failure rates.
 */
public class PerformanceTracker {
    
    private final StructuredLogger logger;
    private final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
    
    public PerformanceTracker(StructuredLogger logger) {
        this.logger = logger;
    }
    
    /**
     * Starts tracking an operation.
     */
    public OperationTimer startOperation(String operationId, String operationType) {
        return new OperationTimer(operationId, operationType, Instant.now());
    }
    
    /**
     * Records the completion of an operation.
     */
    public void recordOperation(OperationTimer timer, boolean success, int messageCount, 
                              Map<String, Object> additionalMetrics) {
        long durationMs = timer.getDurationMs();
        String operationType = timer.getOperationType();
        
        // Update aggregate metrics
        OperationMetrics metrics = operationMetrics.computeIfAbsent(operationType, 
            k -> new OperationMetrics());
        metrics.recordOperation(durationMs, success, messageCount);
        
        // Log individual operation metrics
        Map<String, Object> logMetrics = Map.of(
            "success", success,
            "totalOperations", metrics.getTotalOperations(),
            "successRate", metrics.getSuccessRate(),
            "averageDurationMs", metrics.getAverageDurationMs(),
            "averageMessageCount", metrics.getAverageMessageCount()
        );
        
        if (additionalMetrics != null) {
            Map<String, Object> combinedMetrics = new ConcurrentHashMap<>(logMetrics);
            combinedMetrics.putAll(additionalMetrics);
            logger.logPerformanceMetrics(operationType, durationMs, messageCount, combinedMetrics);
        } else {
            logger.logPerformanceMetrics(operationType, durationMs, messageCount, logMetrics);
        }
    }
    
    /**
     * Gets current metrics for an operation type.
     */
    public OperationMetrics getMetrics(String operationType) {
        return operationMetrics.get(operationType);
    }
    
    /**
     * Gets all current metrics.
     */
    public Map<String, OperationMetrics> getAllMetrics() {
        return Map.copyOf(operationMetrics);
    }
    
    /**
     * Resets all metrics.
     */
    public void resetMetrics() {
        operationMetrics.clear();
    }
    
    /**
     * Timer for tracking individual operations.
     */
    public static class OperationTimer {
        private final String operationId;
        private final String operationType;
        private final Instant startTime;
        private final AtomicInteger messageCount = new AtomicInteger(0);
        
        public OperationTimer(String operationId, String operationType, Instant startTime) {
            this.operationId = operationId;
            this.operationType = operationType;
            this.startTime = startTime;
        }
        
        public String getOperationId() {
            return operationId;
        }
        
        public String getOperationType() {
            return operationType;
        }
        
        public long getDurationMs() {
            return Instant.now().toEpochMilli() - startTime.toEpochMilli();
        }
        
        public void incrementMessageCount() {
            messageCount.incrementAndGet();
        }
        
        public void addMessageCount(int count) {
            messageCount.addAndGet(count);
        }
        
        public int getMessageCount() {
            return messageCount.get();
        }
    }
    
    /**
     * Aggregate metrics for an operation type.
     */
    public static class OperationMetrics {
        private final AtomicLong totalOperations = new AtomicLong(0);
        private final AtomicLong successfulOperations = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong totalMessageCount = new AtomicLong(0);
        private final AtomicLong minDurationMs = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        
        public void recordOperation(long durationMs, boolean success, int messageCount) {
            totalOperations.incrementAndGet();
            totalDurationMs.addAndGet(durationMs);
            totalMessageCount.addAndGet(messageCount);
            
            if (success) {
                successfulOperations.incrementAndGet();
            }
            
            // Update min/max duration
            minDurationMs.updateAndGet(current -> Math.min(current, durationMs));
            maxDurationMs.updateAndGet(current -> Math.max(current, durationMs));
        }
        
        public long getTotalOperations() {
            return totalOperations.get();
        }
        
        public long getSuccessfulOperations() {
            return successfulOperations.get();
        }
        
        public double getSuccessRate() {
            long total = totalOperations.get();
            return total > 0 ? (double) successfulOperations.get() / total : 0.0;
        }
        
        public double getAverageDurationMs() {
            long total = totalOperations.get();
            return total > 0 ? (double) totalDurationMs.get() / total : 0.0;
        }
        
        public double getAverageMessageCount() {
            long total = totalOperations.get();
            return total > 0 ? (double) totalMessageCount.get() / total : 0.0;
        }
        
        public long getMinDurationMs() {
            long min = minDurationMs.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }
        
        public long getMaxDurationMs() {
            return maxDurationMs.get();
        }
        
        public long getTotalDurationMs() {
            return totalDurationMs.get();
        }
        
        public long getTotalMessageCount() {
            return totalMessageCount.get();
        }
    }
}