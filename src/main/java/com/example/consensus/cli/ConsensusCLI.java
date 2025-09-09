package com.example.consensus.cli;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * Command-line interface for testing the Lambda consensus federation.
 * Provides capabilities for sending increment requests, batch testing, and monitoring results.
 */
public class ConsensusCLI {
    
    private static final Logger logger = LoggerFactory.getLogger(ConsensusCLI.class);
    
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String sqsEndpoint;
    private final List<String> availableNodes;
    private final ExecutorService executorService;
    private final CLIResultAggregator resultAggregator;
    
    // Default configuration
    private static final String DEFAULT_SQS_ENDPOINT = "http://localhost:9324";
    private static final List<String> DEFAULT_NODES = Arrays.asList("node1", "node2", "node3", "node4", "node5");
    
    public ConsensusCLI(String sqsEndpoint, List<String> nodes) {
        this.sqsEndpoint = sqsEndpoint;
        this.availableNodes = new ArrayList<>(nodes);
        this.objectMapper = new ObjectMapper();
        this.executorService = Executors.newCachedThreadPool();
        this.resultAggregator = new CLIResultAggregator();
        
        // Initialize SQS client
        this.sqsClient = SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
        
        logger.info("ConsensusCLI initialized with {} nodes", nodes.size());
    }
    
    /**
     * Main entry point for the CLI application.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String sqsEndpoint = System.getProperty("sqs.endpoint", DEFAULT_SQS_ENDPOINT);
        String nodesProperty = System.getProperty("nodes", String.join(",", DEFAULT_NODES));
        List<String> nodes = Arrays.asList(nodesProperty.split(","));
        
        ConsensusCLI cli = new ConsensusCLI(sqsEndpoint, nodes);
        
        try {
            cli.executeCommand(args);
        } catch (Exception e) {
            logger.error("CLI execution failed", e);
            System.exit(1);
        } finally {
            cli.shutdown();
        }
    }
    
    /**
     * Executes the specified command with arguments.
     */
    public void executeCommand(String[] args) throws Exception {
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "send" -> handleSendCommand(args);
            case "broadcast" -> handleBroadcastCommand(args);
            case "batch" -> handleBatchCommand(args);
            case "load-test" -> handleLoadTestCommand(args);
            case "monitor" -> handleMonitorCommand(args);
            case "help" -> printUsage();
            default -> {
                System.err.println("Unknown command: " + command);
                printUsage();
                System.exit(1);
            }
        }
    }
    
    /**
     * Handles the 'send' command to send increment request to a specific node.
     */
    private void handleSendCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: send <nodeId> [count]");
            return;
        }
        
        String targetNode = args[1];
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        
        if (!availableNodes.contains(targetNode)) {
            System.err.println("Invalid node ID. Available nodes: " + availableNodes);
            return;
        }
        
        System.out.println("Sending " + count + " increment request(s) to node: " + targetNode);
        
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(executorService.submit(() -> sendIncrementRequest(targetNode)));
        }
        
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        
        System.out.println("Successfully sent " + successCount + " out of " + count + " requests");
    }
    
    /**
     * Handles the 'broadcast' command to send increment requests to all nodes.
     */
    private void handleBroadcastCommand(String[] args) throws Exception {
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        
        System.out.println("Broadcasting " + count + " increment request(s) to all " + availableNodes.size() + " nodes");
        
        List<Future<Boolean>> futures = new ArrayList<>();
        for (String node : availableNodes) {
            for (int i = 0; i < count; i++) {
                futures.add(executorService.submit(() -> sendIncrementRequest(node)));
            }
        }
        
        int successCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            }
        }
        
        int totalRequests = availableNodes.size() * count;
        System.out.println("Successfully sent " + successCount + " out of " + totalRequests + " requests");
    }
    
    /**
     * Handles the 'batch' command for batch testing operations.
     */
    private void handleBatchCommand(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: batch <requests> <concurrency> [target-node|all]");
            return;
        }
        
        int totalRequests = Integer.parseInt(args[1]);
        int concurrency = Integer.parseInt(args[2]);
        String target = args.length > 3 ? args[3] : "random";
        
        System.out.println("Starting batch test: " + totalRequests + " requests with concurrency " + concurrency);
        
        resultAggregator.startBatch("batch-" + System.currentTimeMillis());
        
        ExecutorService batchExecutor = Executors.newFixedThreadPool(concurrency);
        List<Future<CLIResultAggregator.BatchResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            futures.add(batchExecutor.submit(() -> {
                String targetNode = selectTargetNode(target);
                long startTime = System.currentTimeMillis();
                boolean success = sendIncrementRequest(targetNode);
                long duration = System.currentTimeMillis() - startTime;
                
                return new CLIResultAggregator.BatchResult(requestId, targetNode, success, duration);
            }));
        }
        
        List<CLIResultAggregator.BatchResult> results = new ArrayList<>();
        for (Future<CLIResultAggregator.BatchResult> future : futures) {
            results.add(future.get());
        }
        
        batchExecutor.shutdown();
        resultAggregator.completeBatch(results);
        
        printBatchResults(results);
    }
    
    /**
     * Handles the 'load-test' command for sustained load testing.
     */
    private void handleLoadTestCommand(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: load-test <duration-seconds> <requests-per-second> <concurrency>");
            return;
        }
        
        int durationSeconds = Integer.parseInt(args[1]);
        int requestsPerSecond = Integer.parseInt(args[2]);
        int concurrency = Integer.parseInt(args[3]);
        
        System.out.println("Starting load test: " + requestsPerSecond + " req/s for " + durationSeconds + "s with concurrency " + concurrency);
        
        resultAggregator.startLoadTest("load-test-" + System.currentTimeMillis());
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(concurrency);
        List<Future<?>> futures = new ArrayList<>();
        
        long intervalMs = 1000 / requestsPerSecond;
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        
        while (System.currentTimeMillis() < endTime) {
            futures.add(scheduler.submit(() -> {
                String targetNode = selectTargetNode("random");
                long startTime = System.currentTimeMillis();
                boolean success = sendIncrementRequest(targetNode);
                long duration = System.currentTimeMillis() - startTime;
                
                resultAggregator.recordLoadTestResult(targetNode, success, duration);
            }));
            
            Thread.sleep(intervalMs);
        }
        
        scheduler.shutdown();
        scheduler.awaitTermination(30, TimeUnit.SECONDS);
        
        resultAggregator.completeLoadTest();
        printLoadTestResults();
    }
    
    /**
     * Handles the 'monitor' command for monitoring consensus operations.
     */
    private void handleMonitorCommand(String[] args) throws Exception {
        int durationSeconds = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        
        System.out.println("Monitoring consensus operations for " + durationSeconds + " seconds...");
        System.out.println("Press Ctrl+C to stop monitoring");
        
        // This would integrate with log monitoring in a real implementation
        // For now, we'll simulate monitoring by periodically checking system status
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        
        while (System.currentTimeMillis() < endTime) {
            System.out.println("[" + Instant.now() + "] Monitoring active - " + 
                             "Available nodes: " + availableNodes.size());
            Thread.sleep(5000);
        }
        
        System.out.println("Monitoring completed");
    }
    
    /**
     * Sends an increment request to the specified target node.
     */
    private boolean sendIncrementRequest(String targetNode) {
        try {
            String requestId = generateRequestId();
            
            ConsensusRequest request = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "cli-trigger",
                targetNode,
                null,
                requestId,
                Map.of(
                    "timestamp", Instant.now().toString(),
                    "source", "cli-trigger"
                )
            );
            
            String messageBody = objectMapper.writeValueAsString(request);
            String queueUrl = buildQueueUrl(targetNode);
            
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();
            
            sqsClient.sendMessage(sendRequest);
            
            logger.debug("Sent INCREMENT_REQUEST to node '{}' with requestId '{}'", targetNode, requestId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to send INCREMENT_REQUEST to node '{}'", targetNode, e);
            return false;
        }
    }
    
    /**
     * Selects a target node based on the specified strategy.
     */
    private String selectTargetNode(String strategy) {
        return switch (strategy.toLowerCase()) {
            case "all" -> availableNodes.get(new Random().nextInt(availableNodes.size()));
            case "random" -> availableNodes.get(new Random().nextInt(availableNodes.size()));
            default -> availableNodes.contains(strategy) ? strategy : 
                      availableNodes.get(new Random().nextInt(availableNodes.size()));
        };
    }
    
    /**
     * Builds the SQS queue URL for a given node.
     */
    private String buildQueueUrl(String nodeId) {
        return sqsEndpoint + "/000000000000/lambda-" + nodeId + "-queue";
    }
    
    /**
     * Generates a unique request ID.
     */
    private String generateRequestId() {
        return "cli-" + System.currentTimeMillis() + "-" + new Random().nextInt(1000);
    }
    
    /**
     * Prints batch test results.
     */
    private void printBatchResults(List<CLIResultAggregator.BatchResult> results) {
        int successCount = (int) results.stream().mapToInt(r -> r.success ? 1 : 0).sum();
        double avgDuration = results.stream().mapToLong(r -> r.duration).average().orElse(0.0);
        long maxDuration = results.stream().mapToLong(r -> r.duration).max().orElse(0);
        long minDuration = results.stream().mapToLong(r -> r.duration).min().orElse(0);
        
        System.out.println("\n=== Batch Test Results ===");
        System.out.println("Total requests: " + results.size());
        System.out.println("Successful: " + successCount);
        System.out.println("Failed: " + (results.size() - successCount));
        System.out.println("Success rate: " + String.format("%.2f%%", (successCount * 100.0) / results.size()));
        System.out.println("Average duration: " + String.format("%.2f ms", avgDuration));
        System.out.println("Min duration: " + minDuration + " ms");
        System.out.println("Max duration: " + maxDuration + " ms");
    }
    
    /**
     * Prints load test results.
     */
    private void printLoadTestResults() {
        System.out.println("\n=== Load Test Results ===");
        System.out.println("Load test completed - check logs for detailed metrics");
        // In a real implementation, this would show detailed load test metrics
    }
    
    /**
     * Prints CLI usage information.
     */
    private static void printUsage() {
        System.out.println("Lambda Consensus Federation CLI");
        System.out.println("Usage: java -jar consensus-cli.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  send <nodeId> [count]              Send increment request(s) to specific node");
        System.out.println("  broadcast [count]                  Send increment request(s) to all nodes");
        System.out.println("  batch <requests> <concurrency> [target]  Run batch test");
        System.out.println("  load-test <duration> <rps> <concurrency>  Run load test");
        System.out.println("  monitor [duration]                 Monitor consensus operations");
        System.out.println("  help                              Show this help message");
        System.out.println();
        System.out.println("System Properties:");
        System.out.println("  -Dsqs.endpoint=<url>              SQS endpoint (default: http://localhost:9324)");
        System.out.println("  -Dnodes=<node1,node2,...>         Available nodes (default: node1,node2,node3,node4,node5)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar consensus-cli.jar send node1");
        System.out.println("  java -jar consensus-cli.jar broadcast 5");
        System.out.println("  java -jar consensus-cli.jar batch 100 10 random");
        System.out.println("  java -jar consensus-cli.jar load-test 60 5 10");
        System.out.println("  java -jar consensus-cli.jar monitor 120");
    }
    
    /**
     * Shuts down the CLI and releases resources.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sqsClient.close();
    }
    

}