package com.example.consensus.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Monitors log files for consensus operations and aggregates results.
 * Provides real-time monitoring of Lambda consensus federation logs.
 */
public class LogMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(LogMonitor.class);
    
    private final List<Path> logPaths;
    private final ExecutorService executorService;
    private final Map<String, LogMetrics> nodeMetrics;
    private final Pattern consensusPattern;
    private final Pattern errorPattern;
    private final Pattern incrementPattern;
    
    private volatile boolean monitoring = false;
    
    public LogMonitor(List<String> logFilePaths) {
        this.logPaths = logFilePaths.stream()
                .map(Paths::get)
                .toList();
        this.executorService = Executors.newCachedThreadPool();
        this.nodeMetrics = new ConcurrentHashMap<>();
        
        // Compile regex patterns for log parsing
        this.consensusPattern = Pattern.compile(".*consensus.*completed.*", Pattern.CASE_INSENSITIVE);
        this.errorPattern = Pattern.compile(".*(error|failed|exception).*", Pattern.CASE_INSENSITIVE);
        this.incrementPattern = Pattern.compile(".*increment.*request.*", Pattern.CASE_INSENSITIVE);
    }
    
    /**
     * Starts monitoring the specified log files.
     */
    public void startMonitoring() {
        if (monitoring) {
            logger.warn("Log monitoring is already active");
            return;
        }
        
        monitoring = true;
        logger.info("Starting log monitoring for {} files", logPaths.size());
        
        for (Path logPath : logPaths) {
            executorService.submit(() -> monitorLogFile(logPath));
        }
    }
    
    /**
     * Stops log monitoring and shuts down resources.
     */
    public void stopMonitoring() {
        monitoring = false;
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("Log monitoring stopped");
    }
    
    /**
     * Monitors a single log file for consensus-related events.
     */
    private void monitorLogFile(Path logPath) {
        String nodeId = extractNodeIdFromPath(logPath);
        LogMetrics metrics = nodeMetrics.computeIfAbsent(nodeId, k -> new LogMetrics());
        
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            // Skip to end of file for real-time monitoring
            reader.lines().count(); // Consume existing lines
            
            while (monitoring) {
                String line = reader.readLine();
                if (line != null) {
                    processLogLine(line, metrics);
                } else {
                    // No new lines, wait a bit
                    Thread.sleep(100);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (monitoring) {
                logger.error("Error monitoring log file: {}", logPath, e);
            }
        }
    }
    
    /**
     * Processes a single log line and updates metrics.
     */
    private void processLogLine(String line, LogMetrics metrics) {
        metrics.totalLines++;
        
        if (consensusPattern.matcher(line).matches()) {
            metrics.consensusOperations++;
            logger.debug("Detected consensus operation: {}", line.substring(0, Math.min(line.length(), 100)));
        }
        
        if (errorPattern.matcher(line).matches()) {
            metrics.errorCount++;
            logger.debug("Detected error: {}", line.substring(0, Math.min(line.length(), 100)));
        }
        
        if (incrementPattern.matcher(line).matches()) {
            metrics.incrementRequests++;
            logger.debug("Detected increment request: {}", line.substring(0, Math.min(line.length(), 100)));
        }
        
        metrics.lastActivity = Instant.now();
    }
    
    /**
     * Extracts node ID from log file path.
     */
    private String extractNodeIdFromPath(Path logPath) {
        String fileName = logPath.getFileName().toString();
        
        // Try to extract node ID from filename patterns like "node1.log" or "lambda-node1.log"
        if (fileName.contains("node")) {
            int nodeIndex = fileName.indexOf("node");
            String nodeId = fileName.substring(nodeIndex, nodeIndex + 5); // "node1"
            return nodeId.replaceAll("[^a-zA-Z0-9]", "");
        }
        
        // Fallback to filename without extension
        return fileName.replaceAll("\\.[^.]*$", "");
    }
    
    /**
     * Gets current monitoring metrics for all nodes.
     */
    public Map<String, LogMetrics> getCurrentMetrics() {
        return new HashMap<>(nodeMetrics);
    }
    
    /**
     * Prints a summary of current monitoring metrics.
     */
    public void printMetricsSummary() {
        System.out.println("\n=== Log Monitoring Summary ===");
        System.out.println("Timestamp: " + Instant.now());
        
        if (nodeMetrics.isEmpty()) {
            System.out.println("No metrics available");
            return;
        }
        
        System.out.printf("%-10s %-8s %-12s %-8s %-8s %-20s%n", 
                         "Node", "Lines", "Consensus", "Increments", "Errors", "Last Activity");
        System.out.println("-".repeat(80));
        
        for (Map.Entry<String, LogMetrics> entry : nodeMetrics.entrySet()) {
            LogMetrics metrics = entry.getValue();
            System.out.printf("%-10s %-8d %-12d %-8d %-8d %-20s%n",
                             entry.getKey(),
                             metrics.totalLines,
                             metrics.consensusOperations,
                             metrics.incrementRequests,
                             metrics.errorCount,
                             metrics.lastActivity != null ? metrics.lastActivity.toString() : "Never");
        }
        
        // Calculate totals
        int totalConsensus = nodeMetrics.values().stream().mapToInt(m -> m.consensusOperations).sum();
        int totalIncrements = nodeMetrics.values().stream().mapToInt(m -> m.incrementRequests).sum();
        int totalErrors = nodeMetrics.values().stream().mapToInt(m -> m.errorCount).sum();
        
        System.out.println("-".repeat(80));
        System.out.printf("%-10s %-8s %-12d %-8d %-8d%n", 
                         "TOTAL", "-", totalConsensus, totalIncrements, totalErrors);
    }
    
    /**
     * Monitors logs for a specified duration and prints periodic updates.
     */
    public void monitorForDuration(int durationSeconds, int updateIntervalSeconds) {
        startMonitoring();
        
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        
        try {
            while (System.currentTimeMillis() < endTime && monitoring) {
                Thread.sleep(updateIntervalSeconds * 1000L);
                printMetricsSummary();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stopMonitoring();
        }
    }
    
    /**
     * Checks if any nodes are currently active (recent log activity).
     */
    public boolean hasActiveNodes() {
        Instant cutoff = Instant.now().minusSeconds(30); // 30 seconds ago
        
        return nodeMetrics.values().stream()
                .anyMatch(metrics -> metrics.lastActivity != null && 
                         metrics.lastActivity.isAfter(cutoff));
    }
    
    /**
     * Gets the total number of consensus operations across all nodes.
     */
    public int getTotalConsensusOperations() {
        return nodeMetrics.values().stream()
                .mapToInt(m -> m.consensusOperations)
                .sum();
    }
    
    /**
     * Gets the total number of errors across all nodes.
     */
    public int getTotalErrors() {
        return nodeMetrics.values().stream()
                .mapToInt(m -> m.errorCount)
                .sum();
    }
    
    /**
     * Metrics for a single node's log activity.
     */
    public static class LogMetrics {
        public int totalLines = 0;
        public int consensusOperations = 0;
        public int incrementRequests = 0;
        public int errorCount = 0;
        public Instant lastActivity = null;
        
        @Override
        public String toString() {
            return String.format("LogMetrics{lines=%d, consensus=%d, increments=%d, errors=%d, lastActivity=%s}",
                               totalLines, consensusOperations, incrementRequests, errorCount, lastActivity);
        }
    }
}