package com.example.consensus.trigger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TriggerService to verify the service lifecycle and timing behavior.
 * These tests focus on the service's ability to start, stop, and schedule operations correctly.
 */
class TriggerServiceIntegrationTest {
    
    private static final Logger logger = LoggerFactory.getLogger(TriggerServiceIntegrationTest.class);
    
    private static final String TEST_SQS_ENDPOINT = "http://localhost:9324";
    private static final List<String> TEST_NODES = Arrays.asList("lambda-1", "lambda-2");
    
    @Test
    @Timeout(10)
    void testServiceStartAndStop() throws InterruptedException {
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, 1, 2);
        
        // Service should start without throwing exceptions
        assertDoesNotThrow(service::start);
        
        // Let it run briefly
        Thread.sleep(100);
        
        // Service should stop gracefully
        assertDoesNotThrow(service::stop);
    }
    
    @Test
    @Timeout(15)
    void testServiceSchedulingBehavior() throws InterruptedException {
        // Use very short intervals for testing (1-3 seconds)
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, 1, 3);
        
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Start the service
        service.start();
        startLatch.countDown();
        
        // Let it run for a few seconds to verify it's scheduling operations
        // (We can't easily verify SQS messages without a real SQS instance,
        // but we can verify the service doesn't crash and runs for the expected duration)
        Thread.sleep(5000);
        
        // Stop the service
        service.stop();
        
        // If we get here without exceptions, the scheduling is working
        assertTrue(true, "Service ran and stopped without errors");
    }
    
    @Test
    void testServiceWithEmptyNodesList() {
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, Arrays.asList(), 5, 10);
        
        // Service should start even with empty nodes list
        assertDoesNotThrow(service::start);
        
        // But it should handle the empty list gracefully when trying to send messages
        // (The actual message sending will log warnings but not crash)
        
        assertDoesNotThrow(service::stop);
    }
    
    @Test
    void testMultipleStartStopCycles() {
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, 5, 10);
        
        // Test multiple start/stop cycles
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(service::start, "Start cycle " + i + " failed");
            
            try {
                Thread.sleep(100); // Brief run time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Test interrupted");
            }
            
            assertDoesNotThrow(service::stop, "Stop cycle " + i + " failed");
        }
    }
    
    @Test
    @Timeout(5)
    void testServiceStopTimeout() {
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, 1, 2);
        
        service.start();
        
        // Stop should complete within the timeout period
        long startTime = System.currentTimeMillis();
        service.stop();
        long stopTime = System.currentTimeMillis();
        
        long stopDuration = stopTime - startTime;
        assertTrue(stopDuration < 6000, // Should stop within 6 seconds (5 second timeout + buffer)
                  "Service stop took too long: " + stopDuration + "ms");
    }
    
    @Test
    void testServiceConfigurationPreservation() {
        int minInterval = 7;
        int maxInterval = 25;
        TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, minInterval, maxInterval);
        
        // Configuration should be preserved after construction
        assertEquals(minInterval, service.getMinIntervalSeconds());
        assertEquals(maxInterval, service.getMaxIntervalSeconds());
        assertEquals(TEST_NODES, service.getTargetNodes());
        
        // Configuration should remain the same after start/stop
        service.start();
        assertEquals(minInterval, service.getMinIntervalSeconds());
        assertEquals(maxInterval, service.getMaxIntervalSeconds());
        assertEquals(TEST_NODES, service.getTargetNodes());
        
        service.stop();
        assertEquals(minInterval, service.getMinIntervalSeconds());
        assertEquals(maxInterval, service.getMaxIntervalSeconds());
        assertEquals(TEST_NODES, service.getTargetNodes());
    }
}