package com.example.consensus.trigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * Main class for running the TriggerService as a standalone application.
 * This service sends random INCREMENT_REQUEST messages to Lambda nodes for testing.
 */
public class TriggerServiceMain {
    
    private static final Logger logger = LoggerFactory.getLogger(TriggerServiceMain.class);
    
    // Default configuration
    private static final String DEFAULT_SQS_ENDPOINT = "http://localhost:9324";
    private static final List<String> DEFAULT_TARGET_NODES = Arrays.asList(
        "lambda-1", "lambda-2", "lambda-3", "lambda-4", "lambda-5"
    );
    
    public static void main(String[] args) {
        logger.info("Starting TriggerService application...");
        
        // Parse configuration from environment variables or use defaults
        String sqsEndpoint = System.getenv().getOrDefault("SQS_ENDPOINT", DEFAULT_SQS_ENDPOINT);
        String nodesEnv = System.getenv("TARGET_NODES");
        List<String> targetNodes = nodesEnv != null ? 
            Arrays.asList(nodesEnv.split(",")) : DEFAULT_TARGET_NODES;
        
        int minInterval = Integer.parseInt(System.getenv().getOrDefault("MIN_INTERVAL_SECONDS", "5"));
        int maxInterval = Integer.parseInt(System.getenv().getOrDefault("MAX_INTERVAL_SECONDS", "30"));
        
        logger.info("Configuration: SQS endpoint={}, target nodes={}, interval={}-{} seconds", 
                   sqsEndpoint, targetNodes, minInterval, maxInterval);
        
        // Create and start the trigger service
        TriggerService triggerService = new TriggerService(sqsEndpoint, targetNodes, minInterval, maxInterval);
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received, stopping TriggerService...");
            triggerService.stop();
        }));
        
        // Start the service
        triggerService.start();
        
        logger.info("TriggerService started successfully. Press Ctrl+C to stop.");
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted, shutting down...");
            triggerService.stop();
            Thread.currentThread().interrupt();
        }
    }
}