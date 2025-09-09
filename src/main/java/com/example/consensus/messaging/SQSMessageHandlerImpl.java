package com.example.consensus.messaging;

import com.example.consensus.model.ConsensusRequest;
import com.example.consensus.model.JsonMessageSerializer;
import com.example.consensus.logging.StructuredLogger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Optimized implementation of SQSMessageHandler with connection pooling,
 * retry logic, and performance optimizations.
 */
public class SQSMessageHandlerImpl implements SQSMessageHandler {
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_BASE_DELAY_MS = 100;
    private static final int DEFAULT_MAX_MESSAGES = 10;
    private static final int DEFAULT_WAIT_TIME_SECONDS = 20;
    private static final int CONNECTION_POOL_SIZE = 10;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);
    
    private final String nodeId;
    private final SqsClient sqsClient;
    private final JsonMessageSerializer serializer;
    private final StructuredLogger logger;
    private final ExecutorService executorService;
    private final Map<String, String> queueUrlCache;
    private final AtomicInteger messagesSent;
    private final AtomicInteger messagesReceived;
    
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private long baseDelayMs = DEFAULT_BASE_DELAY_MS;
    
    public SQSMessageHandlerImpl(String nodeId) {
        this.nodeId = nodeId;
        this.serializer = new JsonMessageSerializer();
        this.logger = new StructuredLogger(SQSMessageHandlerImpl.class, nodeId);
        this.queueUrlCache = new ConcurrentHashMap<>();
        this.messagesSent = new AtomicInteger(0);
        this.messagesReceived = new AtomicInteger(0);
        
        // Create optimized thread pool for message processing
        this.executorService = new ThreadPoolExecutor(
            2, // core pool size
            CONNECTION_POOL_SIZE, // maximum pool size
            60L, TimeUnit.SECONDS, // keep alive time
            new LinkedBlockingQueue<>(100), // work queue
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "sqs-handler-" + nodeId + "-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
        );
        
        // Initialize SQS client with optimized configuration
        this.sqsClient = createOptimizedSqsClient();
        
        // Initialize queues
        initializeQueues();
    }
    
    /**
     * Creates an optimized SQS client with connection pooling and timeouts.
     */
    private SqsClient createOptimizedSqsClient() {
        String sqsEndpoint = System.getenv("SQS_ENDPOINT");
        if (sqsEndpoint == null || sqsEndpoint.trim().isEmpty()) {
            sqsEndpoint = "http://localhost:9324"; // Default ElasticMQ endpoint
        }
        
        return SqsClient.builder()
            .endpointOverride(URI.create(sqsEndpoint))
            .httpClientBuilder(software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient.builder()
                .connectionTimeout(CONNECTION_TIMEOUT)
                .socketTimeout(READ_TIMEOUT))
            .build();
    }
    
    @Override
    public boolean sendMessage(String targetNodeId, ConsensusRequest message) {
        if (targetNodeId == null || message == null) {
            logger.logError("sendMessage", "Invalid parameters", null, 0, 0, 
                Map.of("targetNodeId", targetNodeId, "messageNull", message == null));
            return false;
        }
        
        return executeWithRetry(() -> {
            String queueUrl = getQueueUrl(targetNodeId);
            if (queueUrl == null) {
                logger.logError("sendMessage", "Queue URL not found", null, 0, 0, 
                    Map.of("targetNodeId", targetNodeId));
                return false;
            }
            
            try {
                String messageBody = JsonMessageSerializer.serialize(message);
                
                SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(Map.of(
                        "MessageType", MessageAttributeValue.builder()
                            .stringValue(message.getType().toString())
                            .dataType("String")
                            .build(),
                        "SourceNodeId", MessageAttributeValue.builder()
                            .stringValue(message.getSourceNodeId())
                            .dataType("String")
                            .build()
                    ))
                    .build();
                
                SendMessageResponse response = sqsClient.sendMessage(request);
                
                if (response.messageId() != null) {
                    messagesSent.incrementAndGet();
                    logger.logMessage(
                        StructuredLogger.MessageDirection.SENT,
                        message.getType().toString(),
                        message.getSourceNodeId(),
                        targetNodeId,
                        message.getProposalId(),
                        Map.of(
                            "messageId", response.messageId(),
                            "queueUrl", queueUrl
                        )
                    );
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                logger.logError("sendMessage", "Failed to send message", e, 0, 0, 
                    Map.of("targetNodeId", targetNodeId, "messageType", message.getType().toString()));
                return false;
            }
        }, "sendMessage to " + targetNodeId);
    }
    
    @Override
    public int broadcastMessage(ConsensusRequest message) {
        if (message == null) {
            logger.logError("broadcastMessage", "Message is null", null, 0, 0, Map.of());
            return 0;
        }
        
        // Get all known nodes from environment or use default set
        Set<String> knownNodes = getKnownNodes();
        knownNodes.remove(nodeId); // Don't send to self
        
        if (knownNodes.isEmpty()) {
            logger.logError("broadcastMessage", "No known nodes to broadcast to", null, 0, 0, 
                Map.of("messageType", message.getType().toString()));
            return 0;
        }
        
        // Use parallel processing for better performance
        List<CompletableFuture<Boolean>> futures = knownNodes.stream()
            .map(targetNodeId -> CompletableFuture.supplyAsync(
                () -> sendMessage(targetNodeId, message), 
                executorService))
            .toList();
        
        // Wait for all sends to complete with timeout
        int successCount = 0;
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            allFutures.get(30, TimeUnit.SECONDS); // 30 second timeout
            
            for (CompletableFuture<Boolean> future : futures) {
                if (future.isDone() && !future.isCompletedExceptionally() && future.get()) {
                    successCount++;
                }
            }
        } catch (TimeoutException e) {
            logger.logError("broadcastMessage", "Broadcast timeout", e, 0, 0, 
                Map.of("messageType", message.getType().toString(), "targetNodes", knownNodes.size()));
        } catch (Exception e) {
            logger.logError("broadcastMessage", "Broadcast failed", e, 0, 0, 
                Map.of("messageType", message.getType().toString()));
        }
        
        logger.logMessage(
            StructuredLogger.MessageDirection.SENT,
            message.getType().toString(),
            message.getSourceNodeId(),
            "broadcast",
            message.getProposalId(),
            Map.of(
                "targetNodes", knownNodes.size(),
                "successfulSends", successCount
            )
        );
        
        return successCount;
    }
    
    @Override
    public List<ConsensusRequest> pollMessages(int maxMessages, int waitTimeSeconds) {
        String queueUrl = getQueueUrl(nodeId);
        if (queueUrl == null) {
            logger.logError("pollMessages", "Own queue URL not found", null, 0, 0, 
                Map.of("nodeId", nodeId));
            return Collections.emptyList();
        }
        
        return executeWithRetry(() -> {
            try {
                ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(Math.min(maxMessages, 10)) // SQS limit is 10
                    .waitTimeSeconds(Math.min(waitTimeSeconds, 20)) // SQS limit is 20
                    .messageAttributeNames("All")
                    .build();
                
                ReceiveMessageResponse response = sqsClient.receiveMessage(request);
                List<ConsensusRequest> messages = new ArrayList<>();
                
                for (Message message : response.messages()) {
                    try {
                        ConsensusRequest consensusRequest = JsonMessageSerializer.deserializeRequest(
                            message.body());
                        
                        // Create a new request with receipt handle in metadata
                        Map<String, Object> metadata = consensusRequest.getMetadata() != null ? 
                            new HashMap<>(consensusRequest.getMetadata()) : new HashMap<>();
                        metadata.put("receiptHandle", message.receiptHandle());
                        
                        // Create new request with updated metadata
                        consensusRequest = new ConsensusRequest(
                            consensusRequest.getType(),
                            consensusRequest.getSourceNodeId(),
                            consensusRequest.getTargetNodeId(),
                            consensusRequest.getProposedValue(),
                            consensusRequest.getProposalId(),
                            metadata
                        );
                        
                        messages.add(consensusRequest);
                        messagesReceived.incrementAndGet();
                        
                        logger.logMessage(
                            StructuredLogger.MessageDirection.RECEIVED,
                            consensusRequest.getType().toString(),
                            consensusRequest.getSourceNodeId(),
                            nodeId,
                            consensusRequest.getProposalId(),
                            Map.of(
                                "messageId", message.messageId(),
                                "receiptHandle", message.receiptHandle()
                            )
                        );
                        
                    } catch (Exception e) {
                        logger.logError("pollMessages", "Failed to deserialize message", e, 0, 0, 
                            Map.of("messageId", message.messageId()));
                    }
                }
                
                return messages;
                
            } catch (Exception e) {
                logger.logError("pollMessages", "Failed to poll messages", e, 0, 0, 
                    Map.of("queueUrl", queueUrl));
                return Collections.emptyList();
            }
        }, "pollMessages");
    }
    
    @Override
    public Optional<ConsensusRequest> receiveMessage(int waitTimeSeconds) {
        List<ConsensusRequest> messages = pollMessages(1, waitTimeSeconds);
        return messages.isEmpty() ? Optional.empty() : Optional.of(messages.get(0));
    }
    
    @Override
    public boolean deleteMessage(String receiptHandle) {
        if (receiptHandle == null || receiptHandle.trim().isEmpty()) {
            logger.logError("deleteMessage", "Invalid receipt handle", null, 0, 0, Map.of());
            return false;
        }
        
        String queueUrl = getQueueUrl(nodeId);
        if (queueUrl == null) {
            logger.logError("deleteMessage", "Own queue URL not found", null, 0, 0, 
                Map.of("nodeId", nodeId));
            return false;
        }
        
        return executeWithRetry(() -> {
            try {
                DeleteMessageRequest request = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
                
                sqsClient.deleteMessage(request);
                return true;
                
            } catch (Exception e) {
                logger.logError("deleteMessage", "Failed to delete message", e, 0, 0, 
                    Map.of("receiptHandle", receiptHandle));
                return false;
            }
        }, "deleteMessage");
    }
    
    @Override
    public boolean initializeQueues() {
        Set<String> allNodes = getKnownNodes();
        allNodes.add(nodeId); // Include own node
        
        boolean allSuccess = true;
        for (String node : allNodes) {
            if (!createQueueIfNotExists(node)) {
                allSuccess = false;
            }
        }
        
        return allSuccess;
    }
    
    @Override
    public String getQueueUrl(String nodeId) {
        return queueUrlCache.computeIfAbsent(nodeId, this::resolveQueueUrl);
    }
    
    @Override
    public boolean isServiceAvailable() {
        return executeWithRetry(() -> {
            try {
                sqsClient.listQueues(ListQueuesRequest.builder().build());
                return true;
            } catch (Exception e) {
                logger.logError("isServiceAvailable", "SQS service check failed", e, 0, 0, Map.of());
                return false;
            }
        }, "serviceAvailabilityCheck");
    }
    
    @Override
    public void setRetryConfiguration(int maxRetries, long baseDelayMs) {
        this.maxRetries = Math.max(0, maxRetries);
        this.baseDelayMs = Math.max(10, baseDelayMs);
    }
    
    @Override
    public String getNodeId() {
        return nodeId;
    }
    
    /**
     * Gets performance metrics for monitoring.
     */
    public Map<String, Object> getPerformanceMetrics() {
        return Map.of(
            "messagesSent", messagesSent.get(),
            "messagesReceived", messagesReceived.get(),
            "queuesCached", queueUrlCache.size(),
            "activeThreads", ((ThreadPoolExecutor) executorService).getActiveCount(),
            "completedTasks", ((ThreadPoolExecutor) executorService).getCompletedTaskCount()
        );
    }
    
    /**
     * Graceful shutdown of resources.
     */
    public void shutdown() {
        logger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STOPPING, 
            Map.of("nodeId", nodeId, "metrics", getPerformanceMetrics()));
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        try {
            sqsClient.close();
        } catch (Exception e) {
            logger.logError("shutdown", "Error closing SQS client", e, 0, 0, Map.of());
        }
    }
    
    // Private helper methods
    
    private Set<String> getKnownNodes() {
        String knownNodesEnv = System.getenv("KNOWN_NODES");
        if (knownNodesEnv == null || knownNodesEnv.trim().isEmpty()) {
            // Default to 5 nodes
            return Set.of("lambda-node-1", "lambda-node-2", "lambda-node-3", 
                         "lambda-node-4", "lambda-node-5");
        }
        
        return Set.of(knownNodesEnv.split(","));
    }
    
    private boolean createQueueIfNotExists(String nodeId) {
        return executeWithRetry(() -> {
            try {
                String queueName = "consensus-" + nodeId + "-queue";
                
                CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(Map.of(
                        QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, "1209600", // 14 days
                        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, "20"
                    ))
                    .build();
                
                CreateQueueResponse response = sqsClient.createQueue(request);
                queueUrlCache.put(nodeId, response.queueUrl());
                
                return true;
                
            } catch (QueueNameExistsException e) {
                // Queue already exists, get its URL
                return resolveQueueUrl(nodeId) != null;
            } catch (Exception e) {
                logger.logError("createQueueIfNotExists", "Failed to create queue", e, 0, 0, 
                    Map.of("nodeId", nodeId));
                return false;
            }
        }, "createQueue for " + nodeId);
    }
    
    private String resolveQueueUrl(String nodeId) {
        return executeWithRetry(() -> {
            try {
                String queueName = "consensus-" + nodeId + "-queue";
                GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                    .queueName(queueName)
                    .build();
                
                GetQueueUrlResponse response = sqsClient.getQueueUrl(request);
                return response.queueUrl();
                
            } catch (Exception e) {
                logger.logError("resolveQueueUrl", "Failed to resolve queue URL", e, 0, 0, 
                    Map.of("nodeId", nodeId));
                return null;
            }
        }, "resolveQueueUrl for " + nodeId);
    }
    
    private <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < maxRetries) {
                    long delay = baseDelayMs * (1L << attempt); // Exponential backoff
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        logger.logError("executeWithRetry", "Operation failed after retries", lastException, 
            maxRetries, 0, Map.of("operation", operationName));
        
        // Return appropriate default value based on return type
        return null;
    }
}