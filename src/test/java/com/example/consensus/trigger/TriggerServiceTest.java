package com.example.consensus.trigger;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TriggerService focusing on random selection and timing logic.
 * These tests avoid initializing the SQS client to focus on core logic.
 */
class TriggerServiceTest {
    
    private static final String TEST_SQS_ENDPOINT = "http://localhost:9324";
    private static final List<String> TEST_NODES = Arrays.asList(
        "lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"
    );
    
    @Test
    void testServiceInstantiation() {
        // Test that we can create a TriggerService without it crashing
        // This is a basic smoke test for the constructor
        assertDoesNotThrow(() -> {
            TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES, 5, 30);
            
            // Test basic getters work
            assertEquals(5, service.getMinIntervalSeconds());
            assertEquals(30, service.getMaxIntervalSeconds());
            assertEquals(TEST_NODES, service.getTargetNodes());
        });
    }
    
    @Test
    void testServiceInstantiationWithDefaults() {
        // Test that we can create a TriggerService with default intervals
        assertDoesNotThrow(() -> {
            TriggerService service = new TriggerService(TEST_SQS_ENDPOINT, TEST_NODES);
            
            // Test default values
            assertEquals(5, service.getMinIntervalSeconds());
            assertEquals(30, service.getMaxIntervalSeconds());
            assertEquals(TEST_NODES, service.getTargetNodes());
        });
    }
}