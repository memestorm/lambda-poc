package com.example.consensus.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PerformanceTracker functionality.
 */
class PerformanceTrackerTest {
    
    @Mock
    private StructuredLogger mockLogger;
    
    private PerformanceTracker performanceTracker;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        performanceTracker = new PerformanceTracker(mockLogger);
    }
    
    @Test
    void testStartOperation() {
        String operationId = "test-operation-1";
        String operationType = "CONSENSUS";
        
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation(operationId, operationType);
        
        assertNotNull(timer);
        assertEquals(operationId, timer.getOperationId());
        assertEquals(operationType, timer.getOperationType());
        assertEquals(0, timer.getMessageCount());
        assertTrue(timer.getDurationMs() >= 0);
    }
    
    @Test
    void testOperationTimerMessageCount() {
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("test", "TEST");
        
        assertEquals(0, timer.getMessageCount());
        
        timer.incrementMessageCount();
        assertEquals(1, timer.getMessageCount());
        
        timer.addMessageCount(5);
        assertEquals(6, timer.getMessageCount());
    }
    
    @Test
    void testOperationTimerDuration() throws InterruptedException {
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("test", "TEST");
        
        long initialDuration = timer.getDurationMs();
        assertTrue(initialDuration >= 0);
        
        // Sleep briefly to ensure duration increases
        Thread.sleep(10);
        
        long laterDuration = timer.getDurationMs();
        assertTrue(laterDuration > initialDuration);
    }
    
    @Test
    void testRecordSuccessfulOperation() {
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("op1", "CONSENSUS");
        timer.addMessageCount(3);
        
        performanceTracker.recordOperation(timer, true, 3, Map.of("extra", "data"));
        
        // Verify logger was called
        verify(mockLogger).logPerformanceMetrics(
            eq("CONSENSUS"),
            anyLong(),
            eq(3),
            argThat(metrics -> {
                Map<String, Object> metricsMap = (Map<String, Object>) metrics;
                return metricsMap.containsKey("success") && 
                       (Boolean) metricsMap.get("success") &&
                       metricsMap.containsKey("extra") &&
                       "data".equals(metricsMap.get("extra"));
            })
        );
        
        // Check metrics were recorded
        PerformanceTracker.OperationMetrics metrics = performanceTracker.getMetrics("CONSENSUS");
        assertNotNull(metrics);
        assertEquals(1, metrics.getTotalOperations());
        assertEquals(1, metrics.getSuccessfulOperations());
        assertEquals(1.0, metrics.getSuccessRate(), 0.001);
        assertEquals(3.0, metrics.getAverageMessageCount(), 0.001);
    }
    
    @Test
    void testRecordFailedOperation() {
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("op2", "RECOVERY");
        timer.addMessageCount(2);
        
        performanceTracker.recordOperation(timer, false, 2, null);
        
        // Verify logger was called
        verify(mockLogger).logPerformanceMetrics(
            eq("RECOVERY"),
            anyLong(),
            eq(2),
            any()
        );
        
        // Check metrics were recorded
        PerformanceTracker.OperationMetrics metrics = performanceTracker.getMetrics("RECOVERY");
        assertNotNull(metrics);
        assertEquals(1, metrics.getTotalOperations());
        assertEquals(0, metrics.getSuccessfulOperations());
        assertEquals(0.0, metrics.getSuccessRate(), 0.001);
    }
    
    @Test
    void testMultipleOperations() {
        // Record multiple operations of the same type
        for (int i = 0; i < 5; i++) {
            PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("op" + i, "TEST");
            timer.addMessageCount(i + 1);
            performanceTracker.recordOperation(timer, i % 2 == 0, i + 1, null);
        }
        
        PerformanceTracker.OperationMetrics metrics = performanceTracker.getMetrics("TEST");
        assertNotNull(metrics);
        assertEquals(5, metrics.getTotalOperations());
        assertEquals(3, metrics.getSuccessfulOperations()); // Operations 0, 2, 4 succeeded
        assertEquals(0.6, metrics.getSuccessRate(), 0.001);
        assertEquals(3.0, metrics.getAverageMessageCount(), 0.001); // (1+2+3+4+5)/5 = 3
        assertEquals(15, metrics.getTotalMessageCount()); // 1+2+3+4+5 = 15
    }
    
    @Test
    void testGetMetricsForNonExistentOperation() {
        PerformanceTracker.OperationMetrics metrics = performanceTracker.getMetrics("NON_EXISTENT");
        assertNull(metrics);
    }
    
    @Test
    void testGetAllMetrics() {
        // Record operations of different types
        PerformanceTracker.OperationTimer timer1 = performanceTracker.startOperation("op1", "TYPE_A");
        performanceTracker.recordOperation(timer1, true, 1, null);
        
        PerformanceTracker.OperationTimer timer2 = performanceTracker.startOperation("op2", "TYPE_B");
        performanceTracker.recordOperation(timer2, false, 2, null);
        
        Map<String, PerformanceTracker.OperationMetrics> allMetrics = performanceTracker.getAllMetrics();
        
        assertEquals(2, allMetrics.size());
        assertTrue(allMetrics.containsKey("TYPE_A"));
        assertTrue(allMetrics.containsKey("TYPE_B"));
        
        assertEquals(1, allMetrics.get("TYPE_A").getSuccessfulOperations());
        assertEquals(0, allMetrics.get("TYPE_B").getSuccessfulOperations());
    }
    
    @Test
    void testResetMetrics() {
        // Record some operations
        PerformanceTracker.OperationTimer timer = performanceTracker.startOperation("op", "TEST");
        performanceTracker.recordOperation(timer, true, 1, null);
        
        // Verify metrics exist
        assertNotNull(performanceTracker.getMetrics("TEST"));
        assertEquals(1, performanceTracker.getAllMetrics().size());
        
        // Reset metrics
        performanceTracker.resetMetrics();
        
        // Verify metrics are cleared
        assertNull(performanceTracker.getMetrics("TEST"));
        assertEquals(0, performanceTracker.getAllMetrics().size());
    }
    
    @Test
    void testOperationMetricsMinMaxDuration() {
        PerformanceTracker.OperationMetrics metrics = new PerformanceTracker.OperationMetrics();
        
        // Initially, min should be 0 and max should be 0
        assertEquals(0, metrics.getMinDurationMs());
        assertEquals(0, metrics.getMaxDurationMs());
        
        // Record operations with different durations
        metrics.recordOperation(100L, true, 1);
        assertEquals(100L, metrics.getMinDurationMs());
        assertEquals(100L, metrics.getMaxDurationMs());
        
        metrics.recordOperation(50L, true, 1);
        assertEquals(50L, metrics.getMinDurationMs());
        assertEquals(100L, metrics.getMaxDurationMs());
        
        metrics.recordOperation(200L, false, 1);
        assertEquals(50L, metrics.getMinDurationMs());
        assertEquals(200L, metrics.getMaxDurationMs());
    }
    
    @Test
    void testOperationMetricsAverages() {
        PerformanceTracker.OperationMetrics metrics = new PerformanceTracker.OperationMetrics();
        
        // Record operations
        metrics.recordOperation(100L, true, 2);
        metrics.recordOperation(200L, false, 4);
        metrics.recordOperation(300L, true, 6);
        
        assertEquals(3, metrics.getTotalOperations());
        assertEquals(2, metrics.getSuccessfulOperations());
        assertEquals(2.0/3.0, metrics.getSuccessRate(), 0.001);
        assertEquals(200.0, metrics.getAverageDurationMs(), 0.001); // (100+200+300)/3
        assertEquals(4.0, metrics.getAverageMessageCount(), 0.001); // (2+4+6)/3
        assertEquals(600L, metrics.getTotalDurationMs());
        assertEquals(12L, metrics.getTotalMessageCount());
    }
}