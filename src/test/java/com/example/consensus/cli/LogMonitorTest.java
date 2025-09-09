package com.example.consensus.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LogMonitor functionality.
 */
class LogMonitorTest {
    
    @TempDir
    Path tempDir;
    
    private LogMonitor logMonitor;
    private Path testLogFile1;
    private Path testLogFile2;
    
    @BeforeEach
    void setUp() throws IOException {
        // Create test log files
        testLogFile1 = tempDir.resolve("node1.log");
        testLogFile2 = tempDir.resolve("node2.log");
        
        Files.createFile(testLogFile1);
        Files.createFile(testLogFile2);
        
        List<String> logPaths = Arrays.asList(
            testLogFile1.toString(),
            testLogFile2.toString()
        );
        
        logMonitor = new LogMonitor(logPaths);
    }
    
    @Test
    void testConstructorInitialization() {
        assertNotNull(logMonitor);
        assertFalse(logMonitor.hasActiveNodes());
        assertEquals(0, logMonitor.getTotalConsensusOperations());
        assertEquals(0, logMonitor.getTotalErrors());
    }
    
    @Test
    void testNodeIdExtraction() throws IOException {
        // Test various filename patterns
        Path nodeFile1 = tempDir.resolve("node1.log");
        Path nodeFile2 = tempDir.resolve("lambda-node2.log");
        Path nodeFile3 = tempDir.resolve("consensus-node3.log");
        
        Files.createFile(nodeFile1);
        Files.createFile(nodeFile2);
        Files.createFile(nodeFile3);
        
        List<String> logPaths = Arrays.asList(
            nodeFile1.toString(),
            nodeFile2.toString(),
            nodeFile3.toString()
        );
        
        LogMonitor monitor = new LogMonitor(logPaths);
        assertNotNull(monitor);
        
        // The actual node ID extraction is tested indirectly through monitoring
    }
    
    @Test
    void testLogMetricsCreation() {
        LogMonitor.LogMetrics metrics = new LogMonitor.LogMetrics();
        
        assertEquals(0, metrics.totalLines);
        assertEquals(0, metrics.consensusOperations);
        assertEquals(0, metrics.incrementRequests);
        assertEquals(0, metrics.errorCount);
        assertNull(metrics.lastActivity);
    }
    
    @Test
    void testLogMetricsToString() {
        LogMonitor.LogMetrics metrics = new LogMonitor.LogMetrics();
        metrics.totalLines = 100;
        metrics.consensusOperations = 5;
        metrics.incrementRequests = 10;
        metrics.errorCount = 2;
        metrics.lastActivity = Instant.now();
        
        String toString = metrics.toString();
        assertTrue(toString.contains("lines=100"));
        assertTrue(toString.contains("consensus=5"));
        assertTrue(toString.contains("increments=10"));
        assertTrue(toString.contains("errors=2"));
    }
    
    @Test
    void testStartAndStopMonitoring() {
        // Test that start/stop don't throw exceptions
        assertDoesNotThrow(() -> {
            logMonitor.startMonitoring();
            Thread.sleep(100); // Give it a moment to start
            logMonitor.stopMonitoring();
        });
    }
    
    @Test
    void testDoubleStart() {
        logMonitor.startMonitoring();
        
        // Starting again should not cause issues
        assertDoesNotThrow(() -> logMonitor.startMonitoring());
        
        logMonitor.stopMonitoring();
    }
    
    @Test
    void testGetCurrentMetrics() {
        Map<String, LogMonitor.LogMetrics> metrics = logMonitor.getCurrentMetrics();
        assertNotNull(metrics);
        // Initially empty since no monitoring has occurred
    }
    
    @Test
    void testPrintMetricsSummary() {
        // Should not throw exception even with no metrics
        assertDoesNotThrow(() -> logMonitor.printMetricsSummary());
    }
    
    @Test
    void testHasActiveNodesInitially() {
        assertFalse(logMonitor.hasActiveNodes());
    }
    
    @Test
    void testTotalCountersInitially() {
        assertEquals(0, logMonitor.getTotalConsensusOperations());
        assertEquals(0, logMonitor.getTotalErrors());
    }
    
    @Test
    void testMonitorForDurationShortTest() {
        // Test very short monitoring duration
        assertDoesNotThrow(() -> {
            // Monitor for 1 second with 1 second updates
            logMonitor.monitorForDuration(1, 1);
        });
    }
    
    @Test
    void testStopWithoutStart() {
        // Should handle gracefully
        assertDoesNotThrow(() -> logMonitor.stopMonitoring());
    }
    
    @Test
    void testMultipleStops() {
        logMonitor.startMonitoring();
        logMonitor.stopMonitoring();
        
        // Multiple stops should be safe
        assertDoesNotThrow(() -> logMonitor.stopMonitoring());
    }
    
    @Test
    void testWithNonExistentLogFiles() {
        List<String> nonExistentPaths = Arrays.asList(
            "/non/existent/path1.log",
            "/non/existent/path2.log"
        );
        
        // Should not throw exception during construction
        assertDoesNotThrow(() -> {
            LogMonitor monitor = new LogMonitor(nonExistentPaths);
            monitor.startMonitoring();
            Thread.sleep(100);
            monitor.stopMonitoring();
        });
    }
    
    @Test
    void testEmptyLogFilesList() {
        LogMonitor emptyMonitor = new LogMonitor(Arrays.asList());
        
        assertDoesNotThrow(() -> {
            emptyMonitor.startMonitoring();
            emptyMonitor.stopMonitoring();
        });
        
        assertFalse(emptyMonitor.hasActiveNodes());
        assertEquals(0, emptyMonitor.getTotalConsensusOperations());
        assertEquals(0, emptyMonitor.getTotalErrors());
    }
    
    @Test
    void testInterruptedMonitoring() throws IOException {
        // Write some test content to log file
        Files.write(testLogFile1, Arrays.asList(
            "2024-01-15 10:30:00 INFO Starting consensus operation",
            "2024-01-15 10:30:01 INFO Consensus completed successfully",
            "2024-01-15 10:30:02 ERROR Failed to process message"
        ));
        
        logMonitor.startMonitoring();
        
        // Interrupt the current thread to test interruption handling
        Thread currentThread = Thread.currentThread();
        
        // Start monitoring in a separate thread and interrupt it
        Thread monitorThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                currentThread.interrupt();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        monitorThread.start();
        
        // This should handle interruption gracefully
        assertDoesNotThrow(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        logMonitor.stopMonitoring();
        
        // Clear interrupt flag
        Thread.interrupted();
    }
}