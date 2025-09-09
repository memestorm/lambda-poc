package com.example.consensus.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates and analyzes results from CLI testing operations.
 * Provides metrics collection and reporting for batch tests and load tests.
 */
public class CLIResultAggregator {
    
    private static final Logger logger = LoggerFactory.getLogger(CLIResultAggregator.class);
    
    // Batch test tracking
    private String currentBatchId;
    private Instant batchStartTime;
    private final Map<String, AtomicInteger> batchSuccessCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> batchFailureCount = new ConcurrentHashMap<>();
    private final List<Long> batchDurations = Collections.synchronizedList(new ArrayList<>());
    
    // Load test tracking
    private String currentLoadTestId;
    private Instant loadTestStartTime;
    private final AtomicInteger loadTestSuccessCount = new AtomicInteger(0);
    private final AtomicInteger loadTestFailureCount = new AtomicInteger(0);
    private final AtomicLong totalLoadTestDuration = new AtomicLong(0);
    private final List<LoadTestMetric> loadTestMetrics = Collections.synchronizedList(new ArrayList<>());
    
    // Historical data
    private final List<BatchTestSummary> batchHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<LoadTestSummary> loadTestHistory = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Starts a new batch test session.
     */
    public void startBatch(String batchId) {
        this.currentBatchId = batchId;
        this.batchStartTime = Instant.now();
        this.batchSuccessCount.clear();
        this.batchFailureCount.clear();
        this.batchDurations.clear();
        
        logger.info("Started batch test: {}", batchId);
    }
    
    /**
     * Completes the current batch test and records results.
     */
    public void completeBatch(List<BatchResult> results) {
        if (currentBatchId == null) {
            logger.warn("No active batch test to complete");
            return;
        }
        
        Instant endTime = Instant.now();
        long totalDuration = endTime.toEpochMilli() - batchStartTime.toEpochMilli();
        
        // Process results
        Map<String, Integer> nodeSuccessCount = new HashMap<>();
        Map<String, Integer> nodeFailureCount = new HashMap<>();
        List<Long> durations = new ArrayList<>();
        
        for (BatchResult result : results) {
            if (result.success) {
                nodeSuccessCount.merge(result.targetNode, 1, Integer::sum);
            } else {
                nodeFailureCount.merge(result.targetNode, 1, Integer::sum);
            }
            durations.add(result.duration);
        }
        
        // Create summary
        BatchTestSummary summary = new BatchTestSummary(
            currentBatchId,
            batchStartTime,
            endTime,
            totalDuration,
            results.size(),
            (int) results.stream().mapToInt(r -> r.success ? 1 : 0).sum(),
            results.size() - (int) results.stream().mapToInt(r -> r.success ? 1 : 0).sum(),
            durations.stream().mapToLong(Long::longValue).average().orElse(0.0),
            durations.stream().mapToLong(Long::longValue).min().orElse(0),
            durations.stream().mapToLong(Long::longValue).max().orElse(0),
            new HashMap<>(nodeSuccessCount),
            new HashMap<>(nodeFailureCount)
        );
        
        batchHistory.add(summary);
        
        logger.info("Completed batch test: {} - {} requests, {} successful, {} failed", 
                   currentBatchId, results.size(), summary.successCount, summary.failureCount);
        
        currentBatchId = null;
        batchStartTime = null;
    }
    
    /**
     * Starts a new load test session.
     */
    public void startLoadTest(String loadTestId) {
        this.currentLoadTestId = loadTestId;
        this.loadTestStartTime = Instant.now();
        this.loadTestSuccessCount.set(0);
        this.loadTestFailureCount.set(0);
        this.totalLoadTestDuration.set(0);
        this.loadTestMetrics.clear();
        
        logger.info("Started load test: {}", loadTestId);
    }
    
    /**
     * Records a single load test result.
     */
    public void recordLoadTestResult(String targetNode, boolean success, long duration) {
        if (currentLoadTestId == null) {
            logger.warn("No active load test to record result for");
            return;
        }
        
        if (success) {
            loadTestSuccessCount.incrementAndGet();
        } else {
            loadTestFailureCount.incrementAndGet();
        }
        
        totalLoadTestDuration.addAndGet(duration);
        
        LoadTestMetric metric = new LoadTestMetric(
            Instant.now(),
            targetNode,
            success,
            duration
        );
        
        loadTestMetrics.add(metric);
        
        // Log periodic updates every 100 requests
        int totalRequests = loadTestSuccessCount.get() + loadTestFailureCount.get();
        if (totalRequests % 100 == 0) {
            logger.info("Load test progress: {} requests completed ({} successful, {} failed)", 
                       totalRequests, loadTestSuccessCount.get(), loadTestFailureCount.get());
        }
    }
    
    /**
     * Completes the current load test and records summary.
     */
    public void completeLoadTest() {
        if (currentLoadTestId == null) {
            logger.warn("No active load test to complete");
            return;
        }
        
        Instant endTime = Instant.now();
        long totalDuration = endTime.toEpochMilli() - loadTestStartTime.toEpochMilli();
        int totalRequests = loadTestSuccessCount.get() + loadTestFailureCount.get();
        
        double averageDuration = totalRequests > 0 ? 
            (double) totalLoadTestDuration.get() / totalRequests : 0.0;
        
        double requestsPerSecond = totalDuration > 0 ? 
            (totalRequests * 1000.0) / totalDuration : 0.0;
        
        // Calculate percentiles
        List<Long> sortedDurations = loadTestMetrics.stream()
            .mapToLong(m -> m.duration)
            .sorted()
            .boxed()
            .toList();
        
        long p50 = getPercentile(sortedDurations, 50);
        long p95 = getPercentile(sortedDurations, 95);
        long p99 = getPercentile(sortedDurations, 99);
        
        LoadTestSummary summary = new LoadTestSummary(
            currentLoadTestId,
            loadTestStartTime,
            endTime,
            totalDuration,
            totalRequests,
            loadTestSuccessCount.get(),
            loadTestFailureCount.get(),
            averageDuration,
            requestsPerSecond,
            p50, p95, p99,
            new ArrayList<>(loadTestMetrics)
        );
        
        loadTestHistory.add(summary);
        
        logger.info("Completed load test: {} - {} requests in {}ms, {:.2f} req/s, {:.2f}% success rate", 
                   currentLoadTestId, totalRequests, totalDuration, requestsPerSecond,
                   (loadTestSuccessCount.get() * 100.0) / totalRequests);
        
        currentLoadTestId = null;
        loadTestStartTime = null;
    }
    
    /**
     * Gets the latest batch test summary.
     */
    public Optional<BatchTestSummary> getLatestBatchSummary() {
        return batchHistory.isEmpty() ? 
            Optional.empty() : 
            Optional.of(batchHistory.get(batchHistory.size() - 1));
    }
    
    /**
     * Gets the latest load test summary.
     */
    public Optional<LoadTestSummary> getLatestLoadTestSummary() {
        return loadTestHistory.isEmpty() ? 
            Optional.empty() : 
            Optional.of(loadTestHistory.get(loadTestHistory.size() - 1));
    }
    
    /**
     * Gets all batch test summaries.
     */
    public List<BatchTestSummary> getAllBatchSummaries() {
        return new ArrayList<>(batchHistory);
    }
    
    /**
     * Gets all load test summaries.
     */
    public List<LoadTestSummary> getAllLoadTestSummaries() {
        return new ArrayList<>(loadTestHistory);
    }
    
    /**
     * Prints a comprehensive report of all test results.
     */
    public void printComprehensiveReport() {
        System.out.println("\n=== Comprehensive Test Report ===");
        
        if (!batchHistory.isEmpty()) {
            System.out.println("\nBatch Test History:");
            for (BatchTestSummary summary : batchHistory) {
                System.out.printf("  %s: %d requests, %.2f%% success, %.2fms avg\n",
                    summary.batchId, summary.totalRequests, 
                    (summary.successCount * 100.0) / summary.totalRequests,
                    summary.averageDuration);
            }
        }
        
        if (!loadTestHistory.isEmpty()) {
            System.out.println("\nLoad Test History:");
            for (LoadTestSummary summary : loadTestHistory) {
                System.out.printf("  %s: %d requests, %.2f req/s, %.2f%% success\n",
                    summary.loadTestId, summary.totalRequests, 
                    summary.requestsPerSecond,
                    (summary.successCount * 100.0) / summary.totalRequests);
            }
        }
        
        if (batchHistory.isEmpty() && loadTestHistory.isEmpty()) {
            System.out.println("No test history available.");
        }
    }
    
    /**
     * Calculates percentile from sorted list of values.
     */
    private long getPercentile(List<Long> sortedValues, int percentile) {
        if (sortedValues.isEmpty()) return 0;
        
        int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        
        return sortedValues.get(index);
    }
    
    /**
     * Summary data for a batch test.
     */
    public static class BatchTestSummary {
        public final String batchId;
        public final Instant startTime;
        public final Instant endTime;
        public final long totalDurationMs;
        public final int totalRequests;
        public final int successCount;
        public final int failureCount;
        public final double averageDuration;
        public final long minDuration;
        public final long maxDuration;
        public final Map<String, Integer> successByNode;
        public final Map<String, Integer> failureByNode;
        
        public BatchTestSummary(String batchId, Instant startTime, Instant endTime, 
                               long totalDurationMs, int totalRequests, int successCount, 
                               int failureCount, double averageDuration, long minDuration, 
                               long maxDuration, Map<String, Integer> successByNode, 
                               Map<String, Integer> failureByNode) {
            this.batchId = batchId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalDurationMs = totalDurationMs;
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.averageDuration = averageDuration;
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
            this.successByNode = successByNode;
            this.failureByNode = failureByNode;
        }
    }
    
    /**
     * Summary data for a load test.
     */
    public static class LoadTestSummary {
        public final String loadTestId;
        public final Instant startTime;
        public final Instant endTime;
        public final long totalDurationMs;
        public final int totalRequests;
        public final int successCount;
        public final int failureCount;
        public final double averageDuration;
        public final double requestsPerSecond;
        public final long p50Duration;
        public final long p95Duration;
        public final long p99Duration;
        public final List<LoadTestMetric> metrics;
        
        public LoadTestSummary(String loadTestId, Instant startTime, Instant endTime, 
                              long totalDurationMs, int totalRequests, int successCount, 
                              int failureCount, double averageDuration, double requestsPerSecond,
                              long p50Duration, long p95Duration, long p99Duration,
                              List<LoadTestMetric> metrics) {
            this.loadTestId = loadTestId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.totalDurationMs = totalDurationMs;
            this.totalRequests = totalRequests;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.averageDuration = averageDuration;
            this.requestsPerSecond = requestsPerSecond;
            this.p50Duration = p50Duration;
            this.p95Duration = p95Duration;
            this.p99Duration = p99Duration;
            this.metrics = metrics;
        }
    }
    
    /**
     * Individual load test metric.
     */
    public static class LoadTestMetric {
        public final Instant timestamp;
        public final String targetNode;
        public final boolean success;
        public final long duration;
        
        public LoadTestMetric(Instant timestamp, String targetNode, boolean success, long duration) {
            this.timestamp = timestamp;
            this.targetNode = targetNode;
            this.success = success;
            this.duration = duration;
        }
    }
    
    /**
     * Result data class for batch operations.
     */
    public static class BatchResult {
        public final int requestId;
        public final String targetNode;
        public final boolean success;
        public final long duration;
        
        public BatchResult(int requestId, String targetNode, boolean success, long duration) {
            this.requestId = requestId;
            this.targetNode = targetNode;
            this.success = success;
            this.duration = duration;
        }
    }
}