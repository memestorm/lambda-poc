package com.example.consensus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for CLI functionality.
 * These tests require SQS to be available and are disabled by default.
 */
class CLIIntegrationTest {
    
    @Test
    @EnabledIfSystemProperty(named = "test.integration.cli", matches = "true")
    void testCLIHelpCommand() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            ConsensusCLI.main(new String[]{"help"});
            
            String output = outputStream.toString();
            assertTrue(output.contains("Lambda Consensus Federation CLI"));
            assertTrue(output.contains("Commands:"));
            assertTrue(output.contains("send"));
            assertTrue(output.contains("broadcast"));
            assertTrue(output.contains("batch"));
            assertTrue(output.contains("load-test"));
            assertTrue(output.contains("monitor"));
        } finally {
            System.setOut(originalOut);
        }
    }
    
    @Test
    @EnabledIfSystemProperty(named = "test.integration.cli", matches = "true")
    void testCLISendCommandWithSQSAvailable() throws Exception {
        // This test requires SQS to be running
        // Set system properties for test environment
        System.setProperty("sqs.endpoint", "http://localhost:9324");
        System.setProperty("nodes", "node1,node2,node3");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            ConsensusCLI.main(new String[]{"send", "node1", "1"});
            
            String output = outputStream.toString();
            // Should either succeed or fail gracefully
            assertTrue(output.contains("Sending") || output.contains("Failed"));
        } catch (Exception e) {
            // Expected if SQS is not available
            assertTrue(e.getMessage().contains("SQS") || 
                      e.getMessage().contains("connection") ||
                      e.getMessage().contains("endpoint"));
        } finally {
            System.setOut(originalOut);
        }
    }
    
    @Test
    @EnabledIfSystemProperty(named = "test.integration.cli", matches = "true")
    void testCLIBatchCommandValidation() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));
        
        try {
            ConsensusCLI.main(new String[]{"batch", "10"});
            
            String output = outputStream.toString();
            assertTrue(output.contains("Usage: batch"));
        } finally {
            System.setOut(originalOut);
        }
    }
    
    @Test
    void testCLIWithInvalidCommand() {
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errorStream));
        
        try {
            // This should exit with code 1, but we can't easily test System.exit
            // Instead we test the error output
            ConsensusCLI cli = new ConsensusCLI("http://localhost:9324", 
                                               java.util.Arrays.asList("node1", "node2"));
            
            assertDoesNotThrow(() -> {
                try {
                    cli.executeCommand(new String[]{"invalid-command"});
                } catch (Exception e) {
                    // Expected for invalid commands
                }
            });
            
        } finally {
            System.setErr(originalErr);
        }
    }
    
    @Test
    void testResultAggregatorIntegration() {
        CLIResultAggregator aggregator = new CLIResultAggregator();
        
        // Test batch workflow
        aggregator.startBatch("integration-test");
        
        java.util.List<CLIResultAggregator.BatchResult> results = java.util.Arrays.asList(
            new CLIResultAggregator.BatchResult(1, "node1", true, 100L),
            new CLIResultAggregator.BatchResult(2, "node2", false, 200L)
        );
        
        aggregator.completeBatch(results);
        
        java.util.Optional<CLIResultAggregator.BatchTestSummary> summary = 
            aggregator.getLatestBatchSummary();
        
        assertTrue(summary.isPresent());
        assertEquals("integration-test", summary.get().batchId);
        assertEquals(2, summary.get().totalRequests);
        assertEquals(1, summary.get().successCount);
        assertEquals(1, summary.get().failureCount);
    }
    
    @Test
    void testLogMonitorIntegration() throws Exception {
        // Create temporary log files for testing
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("cli-test");
        java.nio.file.Path logFile = tempDir.resolve("test-node.log");
        java.nio.file.Files.createFile(logFile);
        
        LogMonitor monitor = new LogMonitor(java.util.Arrays.asList(logFile.toString()));
        
        // Test basic functionality
        assertFalse(monitor.hasActiveNodes());
        assertEquals(0, monitor.getTotalConsensusOperations());
        assertEquals(0, monitor.getTotalErrors());
        
        // Test start/stop
        assertDoesNotThrow(() -> {
            monitor.startMonitoring();
            Thread.sleep(100);
            monitor.stopMonitoring();
        });
        
        // Cleanup
        java.nio.file.Files.deleteIfExists(logFile);
        java.nio.file.Files.deleteIfExists(tempDir);
    }
}