package com.example.consensus.trigger;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight service that sends INCREMENT_REQUEST messages at random intervals
 * to randomly selected Lambda nodes for testing consensus operations.
 */
public class TriggerService {
    
    private static final Logger logger = LoggerFactory.getLogger(TriggerService.class);
    
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final List<String> targetNodes;
    private final String sqsEndpoint;
    private final ScheduledExecutorService scheduler;
    private final Random random;
    
    // Configuration for random intervals (5-30 seconds)
    private final int minIntervalSeconds;
    private final int maxIntervalSeconds;
    
    public TriggerService(String sqsEndpoint, List<String> targetNodes) {
        this(sqsEndpoint, targetNodes, 5, 30);
    }
    
    public TriggerService(String sqsEndpoint, List<String> targetNodes, 
                         int minIntervalSeconds, int maxIntervalSeconds) {
        this.sqsEndpoint = sqsEndpoint;
        this.targetNodes = new ArrayList<>(targetNodes);
        this.minIntervalSeconds = minIntervalSeconds;
        this.maxIntervalSeconds = maxIntervalSeconds;
        this.random = new Random();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "trigger-service");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize SQS client with local endpoint and explicit HTTP client
        this.sqsClient = SqsClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .httpClient(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder().build())
                .build();
        
        logger.info("TriggerService initialized with {} target nodes, interval: {}-{} seconds", 
                   targetNodes.size(), minIntervalSeconds, maxIntervalSeconds);
    }
    
    /**
     * Starts the trigger service, scheduling random increment requests.
     */
    public void start() {
        logger.info("Starting TriggerService...");
        scheduleNextTrigger();
    }
    
    /**
     * Stops the trigger service and shuts down the scheduler.
     */
    public void stop() {
        logger.info("Stopping TriggerService...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sqsClient.close();
        logger.info("TriggerService stopped");
    }
    
    /**
     * Schedules the next trigger event at a random interval.
     */
    private void scheduleNextTrigger() {
        int delaySeconds = getRandomInterval();
        logger.debug("Scheduling next trigger in {} seconds", delaySeconds);
        
        scheduler.schedule(() -> {
            try {
                sendIncrementRequest();
            } catch (Exception e) {
                logger.error("Error sending increment request", e);
            } finally {
                // Schedule the next trigger regardless of success/failure
                scheduleNextTrigger();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Sends an INCREMENT_REQUEST message to a randomly selected Lambda node.
     */
    private void sendIncrementRequest() {
        if (targetNodes.isEmpty()) {
            logger.warn("No target nodes available for increment request");
            return;
        }
        
        String targetNode = selectRandomNode();
        String requestId = generateRequestId();
        
        try {
            ConsensusRequest request = new ConsensusRequest(
                MessageType.INCREMENT_REQUEST,
                "trigger-service",
                targetNode,
                null, // No specific value for increment requests
                requestId,
                Map.of(
                    "timestamp", Instant.now().toString(),
                    "source", "trigger-service"
                )
            );
            
            String messageBody = objectMapper.writeValueAsString(request);
            String queueUrl = buildQueueUrl(targetNode);
            
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();
            
            sqsClient.sendMessage(sendRequest);
            
            logger.info("Sent INCREMENT_REQUEST to node '{}' with requestId '{}'", 
                       targetNode, requestId);
            
        } catch (Exception e) {
            logger.error("Failed to send INCREMENT_REQUEST to node '{}' with requestId '{}'", 
                        targetNode, requestId, e);
        }
    }
    
    /**
     * Randomly selects a target Lambda node from the available nodes.
     * 
     * @return the ID of the selected node
     * @throws IllegalArgumentException if no target nodes are available
     */
    String selectRandomNode() {
        if (targetNodes.isEmpty()) {
            throw new IllegalArgumentException("No target nodes available for selection");
        }
        
        int index = random.nextInt(targetNodes.size());
        String selectedNode = targetNodes.get(index);
        logger.debug("Selected random node: {} (index {} of {})", 
                    selectedNode, index, targetNodes.size());
        return selectedNode;
    }
    
    /**
     * Generates a random interval between min and max seconds (inclusive).
     * 
     * @return random interval in seconds
     */
    int getRandomInterval() {
        return ThreadLocalRandom.current().nextInt(minIntervalSeconds, maxIntervalSeconds + 1);
    }
    
    /**
     * Generates a unique request ID for tracking purposes.
     * 
     * @return unique request ID
     */
    private String generateRequestId() {
        return "trigger-" + System.currentTimeMillis() + "-" + random.nextInt(1000);
    }
    
    /**
     * Builds the SQS queue URL for a given node.
     * 
     * @param nodeId the target node ID
     * @return the queue URL for the node
     */
    private String buildQueueUrl(String nodeId) {
        return sqsEndpoint + "/000000000000/lambda-" + nodeId + "-queue";
    }
    
    /**
     * Gets the list of target nodes.
     * 
     * @return copy of the target nodes list
     */
    public List<String> getTargetNodes() {
        return new ArrayList<>(targetNodes);
    }
    
    /**
     * Gets the configured minimum interval in seconds.
     * 
     * @return minimum interval seconds
     */
    public int getMinIntervalSeconds() {
        return minIntervalSeconds;
    }
    
    /**
     * Gets the configured maximum interval in seconds.
     * 
     * @return maximum interval seconds
     */
    public int getMaxIntervalSeconds() {
        return maxIntervalSeconds;
    }
}