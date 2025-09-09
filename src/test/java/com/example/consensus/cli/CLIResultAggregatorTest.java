package com.example.consensus.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLIResultAggregator functionality.
 */
class CLIResultAggregatorTest {
    
    private CLIResultAggregator aggregator;
    
    @BeforeEach
    void setUp() {
        aggregator = new CLIResultAggregator();
    }
    
    @Test
    void testBatchTestLifecycle() {
        String batchId = "test-batch-1";
        
        // Start batch test
        aggregator.startBatch(batchId);
        
        // Create test results
        List<CLIResultAggregator.BatchResult> results = Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node1", true, 100L),
            new CLIResultAggregator.BatchResult(2, "node2", true, 150L),
            new CLIResultAggregator.BatchResult(3, "node1", false, 200L),
            new CLIResultAggregator.BatchResult(4, "node3", true, 120L)
        );
        
        // Complete batch test
        aggregator.completeBatch(results);
        
        // Verify summary
        Optional<CLIResultAggregator.BatchTestSummary> summary = aggregator.getLatestBatchSummary();
        assertTrue(summary.isPresent());
        
        CLIResultAggregator.BatchTestSummary batchSummary = summary.get();
        assertEquals(batchId, batchSummary.batchId);
        assertEquals(4, batchSummary.totalRequests);
        assertEquals(3, batchSummary.successCount);
        assertEquals(1, batchSummary.failureCount);
        assertEquals(142.5, batchSummary.averageDuration, 0.1);
        assertEquals(100L, batchSummary.minDuration);
        assertEquals(200L, batchSummary.maxDuration);
    }
    
    @Test
    void testLoadTestLifecycle() {
        String loadTestId = "test-load-1";
        
        // Start load test
        aggregator.startLoadTest(loadTestId);
        
        // Record some results
        aggregator.recordLoadTestResult("node1", true, 100L);
        aggregator.recordLoadTestResult("node2", true, 150L);
        aggregator.recordLoadTestResult("node1", false, 200L);
        aggregator.recordLoadTestResult("node3", true, 120L);
        
        // Complete load test
        aggregator.completeLoadTest();
        
        // Verify summary
        Optional<CLIResultAggregator.LoadTestSummary> summary = aggregator.getLatestLoadTestSummary();
        assertTrue(summary.isPresent());
        
        CLIResultAggregator.LoadTestSummary loadSummary = summary.get();
        assertEquals(loadTestId, loadSummary.loadTestId);
        assertEquals(4, loadSummary.totalRequests);
        assertEquals(3, loadSummary.successCount);
        assertEquals(1, loadSummary.failureCount);
        assertEquals(142.5, loadSummary.averageDuration, 0.1);
        assertTrue(loadSummary.requestsPerSecond > 0);
    }
    
    @Test
    void testEmptyBatchResults() {
        String batchId = "empty-batch";
        
        aggregator.startBatch(batchId);
        aggregator.completeBatch(Arrays.asList());
        
        Optional<CLIResultAggregator.BatchTestSummary> summary = aggregator.getLatestBatchSummary();
        assertTrue(summary.isPresent());
        
        CLIResultAggregator.BatchTestSummary batchSummary = summary.get();
        assertEquals(0, batchSummary.totalRequests);
        assertEquals(0, batchSummary.successCount);
        assertEquals(0, batchSummary.failureCount);
    }
    
    @Test
    void testLoadTestWithoutStart() {
        // Try to record results without starting load test
        aggregator.recordLoadTestResult("node1", true, 100L);
        
        // Should handle gracefully (logged as warning)
        // No exception should be thrown
        assertDoesNotThrow(() -> aggregator.recordLoadTestResult("node1", true, 100L));
    }
    
    @Test
    void testBatchTestWithoutStart() {
        // Try to complete batch without starting
        List<CLIResultAggregator.BatchResult> results = Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node1", true, 100L)
        );
        
        // Should handle gracefully (logged as warning)
        assertDoesNotThrow(() -> aggregator.completeBatch(results));
    }
    
    @Test
    void testMultipleBatchTests() {
        // First batch test
        aggregator.startBatch("batch-1");
        aggregator.completeBatch(Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node1", true, 100L)
        ));
        
        // Second batch test
        aggregator.startBatch("batch-2");
        aggregator.completeBatch(Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node2", true, 200L),
            new CLIResultAggregator.BatchResult(2, "node3", false, 300L)
        ));
        
        // Verify both are stored
        List<CLIResultAggregator.BatchTestSummary> allSummaries = aggregator.getAllBatchSummaries();
        assertEquals(2, allSummaries.size());
        
        // Latest should be batch-2
        Optional<CLIResultAggregator.BatchTestSummary> latest = aggregator.getLatestBatchSummary();
        assertTrue(latest.isPresent());
        assertEquals("batch-2", latest.get().batchId);
    }
    
    @Test
    void testMultipleLoadTests() {
        // First load test
        aggregator.startLoadTest("load-1");
        aggregator.recordLoadTestResult("node1", true, 100L);
        aggregator.completeLoadTest();
        
        // Second load test
        aggregator.startLoadTest("load-2");
        aggregator.recordLoadTestResult("node2", true, 200L);
        aggregator.recordLoadTestResult("node3", false, 300L);
        aggregator.completeLoadTest();
        
        // Verify both are stored
        List<CLIResultAggregator.LoadTestSummary> allSummaries = aggregator.getAllLoadTestSummaries();
        assertEquals(2, allSummaries.size());
        
        // Latest should be load-2
        Optional<CLIResultAggregator.LoadTestSummary> latest = aggregator.getLatestLoadTestSummary();
        assertTrue(latest.isPresent());
        assertEquals("load-2", latest.get().loadTestId);
    }
    
    @Test
    void testBatchResultCreation() {
        CLIResultAggregator.BatchResult result = new CLIResultAggregator.BatchResult(
            1, "node1", true, 150L
        );
        
        assertEquals(1, result.requestId);
        assertEquals("node1", result.targetNode);
        assertTrue(result.success);
        assertEquals(150L, result.duration);
    }
    
    @Test
    void testLoadTestMetricCreation() {
        Instant timestamp = Instant.now();
        CLIResultAggregator.LoadTestMetric metric = new CLIResultAggregator.LoadTestMetric(
            timestamp, "node2", false, 250L
        );
        
        assertEquals(timestamp, metric.timestamp);
        assertEquals("node2", metric.targetNode);
        assertFalse(metric.success);
        assertEquals(250L, metric.duration);
    }
    
    @Test
    void testComprehensiveReport() {
        // Add some test data
        aggregator.startBatch("test-batch");
        aggregator.completeBatch(Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node1", true, 100L)
        ));
        
        aggregator.startLoadTest("test-load");
        aggregator.recordLoadTestResult("node1", true, 100L);
        aggregator.completeLoadTest();
        
        // Test that comprehensive report doesn't throw exceptions
        assertDoesNotThrow(() -> aggregator.printComprehensiveReport());
    }
    
    @Test
    void testEmptyHistoryReport() {
        // Test comprehensive report with no history
        assertDoesNotThrow(() -> aggregator.printComprehensiveReport());
    }
}