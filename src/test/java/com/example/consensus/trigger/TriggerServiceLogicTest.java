package com.example.consensus.trigger;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TriggerService logic focusing on random selection and timing logic.
 * These tests use a helper class to avoid SQS client initialization.
 */
class TriggerServiceLogicTest {
    
    private static final List<String> TEST_NODES = Arrays.asList(
        "lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"
    );
    
    /**
     * Helper class to test TriggerService logic without SQS client initialization.
     */
    static class TriggerServiceLogic {
        private final List<String> targetNodes;
        private final Random random;
        private final int minIntervalSeconds;
        private final int maxIntervalSeconds;
        
        public TriggerServiceLogic(List<String> targetNodes, int minIntervalSeconds, int maxIntervalSeconds) {
            this.targetNodes = targetNodes;
            this.minIntervalSeconds = minIntervalSeconds;
            this.maxIntervalSeconds = maxIntervalSeconds;
            this.random = new Random();
        }
        
        public String selectRandomNode() {
            if (targetNodes.isEmpty()) {
                throw new IllegalArgumentException("No target nodes available for selection");
            }
            
            int index = random.nextInt(targetNodes.size());
            return targetNodes.get(index);
        }
        
        public int getRandomInterval() {
            return ThreadLocalRandom.current().nextInt(minIntervalSeconds, maxIntervalSeconds + 1);
        }
        
        public List<String> getTargetNodes() {
            return targetNodes;
        }
        
        public int getMinIntervalSeconds() {
            return minIntervalSeconds;
        }
        
        public int getMaxIntervalSeconds() {
            return maxIntervalSeconds;
        }
    }
    
    @Test
    void testSelectRandomNode() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        // Test that selectRandomNode returns one of the configured nodes
        String selectedNode = logic.selectRandomNode();
        
        assertNotNull(selectedNode);
        assertTrue(TEST_NODES.contains(selectedNode));
    }
    
    @Test
    void testSelectRandomNodeDistribution() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        // Test that random selection has reasonable distribution over multiple calls
        Map<String, Integer> selectionCounts = new HashMap<>();
        int totalSelections = 1000;
        
        // Initialize counts
        for (String node : TEST_NODES) {
            selectionCounts.put(node, 0);
        }
        
        // Perform many selections
        for (int i = 0; i < totalSelections; i++) {
            String selected = logic.selectRandomNode();
            selectionCounts.put(selected, selectionCounts.get(selected) + 1);
        }
        
        // Verify each node was selected at least once
        for (String node : TEST_NODES) {
            int count = selectionCounts.get(node);
            assertTrue(count > 0, "Node " + node + " was never selected");
        }
        
        // Verify distribution is reasonably uniform (each node should get roughly 20% of selections)
        // Allow for some variance - each node should get between 10% and 40% of selections
        int expectedCount = totalSelections / TEST_NODES.size();
        int minExpected = (int) (expectedCount * 0.5); // 50% of expected (10% of total)
        int maxExpected = (int) (expectedCount * 2.0); // 200% of expected (40% of total)
        
        for (String node : TEST_NODES) {
            int count = selectionCounts.get(node);
            assertTrue(count >= minExpected && count <= maxExpected,
                String.format("Node %s selection count %d is outside expected range [%d, %d]",
                    node, count, minExpected, maxExpected));
        }
    }
    
    @Test
    void testGetRandomInterval() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        // Test that getRandomInterval returns values within the configured range
        for (int i = 0; i < 100; i++) {
            int interval = logic.getRandomInterval();
            assertTrue(interval >= 5, "Interval " + interval + " is below minimum");
            assertTrue(interval <= 30, "Interval " + interval + " is above maximum");
        }
    }
    
    @Test
    void testGetRandomIntervalDistribution() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        // Test that random intervals have reasonable distribution
        Map<Integer, Integer> intervalCounts = new HashMap<>();
        int totalIntervals = 1000;
        
        // Perform many interval generations
        for (int i = 0; i < totalIntervals; i++) {
            int interval = logic.getRandomInterval();
            intervalCounts.put(interval, intervalCounts.getOrDefault(interval, 0) + 1);
        }
        
        // Verify we get intervals across the full range
        assertTrue(intervalCounts.size() > 1, "Should generate multiple different intervals");
        
        // Verify all generated intervals are within bounds
        for (Integer interval : intervalCounts.keySet()) {
            assertTrue(interval >= 5 && interval <= 30,
                "Generated interval " + interval + " is outside bounds [5, 30]");
        }
    }
    
    @Test
    void testGetRandomIntervalCustomRange() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 10, 15);
        
        // Test custom range
        for (int i = 0; i < 100; i++) {
            int interval = logic.getRandomInterval();
            assertTrue(interval >= 10, "Interval " + interval + " is below custom minimum");
            assertTrue(interval <= 15, "Interval " + interval + " is above custom maximum");
        }
    }
    
    @Test
    void testGetRandomIntervalSingleValue() {
        // Test edge case where min and max are the same
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 10, 10);
        
        for (int i = 0; i < 10; i++) {
            int interval = logic.getRandomInterval();
            assertEquals(10, interval, "Should always return the single configured value");
        }
    }
    
    @Test
    void testGetTargetNodes() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        List<String> nodes = logic.getTargetNodes();
        assertEquals(TEST_NODES, nodes);
    }
    
    @Test
    void testGetMinMaxIntervalSeconds() {
        TriggerServiceLogic logic = new TriggerServiceLogic(TEST_NODES, 5, 30);
        
        assertEquals(5, logic.getMinIntervalSeconds());
        assertEquals(30, logic.getMaxIntervalSeconds());
    }
    
    @Test
    void testEmptyNodesList() {
        // Test behavior with empty nodes list
        TriggerServiceLogic logic = new TriggerServiceLogic(Arrays.asList(), 5, 10);
        
        assertTrue(logic.getTargetNodes().isEmpty());
        
        // selectRandomNode should throw an exception with empty list
        assertThrows(IllegalArgumentException.class, logic::selectRandomNode);
    }
    
    @Test
    void testSingleNodeList() {
        // Test behavior with single node
        List<String> singleNode = Arrays.asList("lambda-1");
        TriggerServiceLogic logic = new TriggerServiceLogic(singleNode, 5, 30);
        
        // Should always select the single node
        for (int i = 0; i < 10; i++) {
            assertEquals("lambda-1", logic.selectRandomNode());
        }
    }
    
    @Test
    void testConstructorParameters() {
        TriggerServiceLogic logic1 = new TriggerServiceLogic(TEST_NODES, 5, 30);
        assertEquals(5, logic1.getMinIntervalSeconds());
        assertEquals(30, logic1.getMaxIntervalSeconds());
        assertEquals(TEST_NODES, logic1.getTargetNodes());
        
        TriggerServiceLogic logic2 = new TriggerServiceLogic(TEST_NODES, 10, 60);
        assertEquals(10, logic2.getMinIntervalSeconds());
        assertEquals(60, logic2.getMaxIntervalSeconds());
        assertEquals(TEST_NODES, logic2.getTargetNodes());
    }
}