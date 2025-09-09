package com.example.consensus.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility for analyzing consensus system logs to debug issues and understand system behavior.
 * Provides methods to parse structured JSON logs and extract meaningful insights.
 */
public class LogAnalyzer {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    /**
     * Analyzes consensus operations from log files.
     */
    public static ConsensusAnalysis analyzeConsensusOperations(Path logFile) throws IOException {
        List<LogEntry> entries = parseLogFile(logFile);
        return new ConsensusAnalysis(entries);
    }
    
    /**
     * Analyzes performance metrics from log files.
     */
    public static PerformanceAnalysis analyzePerformance(Path logFile) throws IOException {
        List<LogEntry> entries = parseLogFile(logFile);
        return new PerformanceAnalysis(entries);
    }
    
    /**
     * Analyzes error patterns from log files.
     */
    public static ErrorAnalysis analyzeErrors(Path logFile) throws IOException {
        List<LogEntry> entries = parseLogFile(logFile);
        return new ErrorAnalysis(entries);
    }
    
    /**
     * Traces a specific consensus operation across all nodes.
     */
    public static ConsensusTrace traceConsensusOperation(Path logFile, String proposalId) throws IOException {
        List<LogEntry> entries = parseLogFile(logFile);
        return new ConsensusTrace(entries, proposalId);
    }
    
    /**
     * Parses a log file and extracts structured log entries.
     */
    private static List<LogEntry> parseLogFile(Path logFile) throws IOException {
        List<LogEntry> entries = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(logFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLogLine(line);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        
        // Sort by timestamp
        entries.sort(Comparator.comparing(LogEntry::getTimestamp));
        return entries;
    }
    
    /**
     * Parses a single log line into a LogEntry.
     */
    private static LogEntry parseLogLine(String line) {
        try {
            // Try to parse as JSON first
            if (line.trim().startsWith("{")) {
                JsonNode json = objectMapper.readTree(line);
                return LogEntry.fromJson(json);
            }
            
            // Fall back to parsing standard log format
            return LogEntry.fromStandardLog(line);
            
        } catch (Exception e) {
            // Skip unparseable lines
            return null;
        }
    }
    
    /**
     * Represents a parsed log entry.
     */
    public static class LogEntry {
        private final Instant timestamp;
        private final String nodeId;
        private final String eventType;
        private final String level;
        private final String message;
        private final Map<String, Object> data;
        
        public LogEntry(Instant timestamp, String nodeId, String eventType, String level, 
                       String message, Map<String, Object> data) {
            this.timestamp = timestamp;
            this.nodeId = nodeId;
            this.eventType = eventType;
            this.level = level;
            this.message = message;
            this.data = data != null ? data : new HashMap<>();
        }
        
        public static LogEntry fromJson(JsonNode json) {
            try {
                Instant timestamp = Instant.parse(json.get("timestamp").asText());
                String nodeId = json.has("nodeId") ? json.get("nodeId").asText() : "unknown";
                String eventType = json.has("eventType") ? json.get("eventType").asText() : "unknown";
                String level = "INFO"; // Default level for JSON logs
                String message = json.has("message") ? json.get("message").asText() : "";
                
                Map<String, Object> data = new HashMap<>();
                json.fields().forEachRemaining(entry -> {
                    if (!Arrays.asList("timestamp", "nodeId", "eventType", "message").contains(entry.getKey())) {
                        data.put(entry.getKey(), entry.getValue().asText());
                    }
                });
                
                return new LogEntry(timestamp, nodeId, eventType, level, message, data);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        
        public static LogEntry fromStandardLog(String line) {
            // Parse standard SLF4J log format: timestamp [thread] level logger - message
            try {
                String[] parts = line.split(" - ", 2);
                if (parts.length < 2) return null;
                
                String headerPart = parts[0];
                String message = parts[1];
                
                // Extract timestamp (first part before space)
                String[] headerParts = headerPart.split(" ");
                if (headerParts.length < 4) return null;
                
                String timestampStr = headerParts[0] + " " + headerParts[1];
                Instant timestamp = parseTimestamp(timestampStr);
                
                String level = headerParts[2];
                String logger = headerParts[3];
                
                // Extract node ID from logger or message
                String nodeId = extractNodeId(logger, message);
                
                return new LogEntry(timestamp, nodeId, "standard_log", level, message, Map.of());
            } catch (Exception e) {
                return null;
            }
        }
        
        private static Instant parseTimestamp(String timestampStr) {
            try {
                // Try different timestamp formats
                return Instant.parse(timestampStr);
            } catch (DateTimeParseException e) {
                // Fall back to current time if parsing fails
                return Instant.now();
            }
        }
        
        private static String extractNodeId(String logger, String message) {
            // Try to extract node ID from message
            if (message.contains("node:")) {
                int start = message.indexOf("node:") + 5;
                int end = message.indexOf(" ", start);
                if (end == -1) end = message.indexOf(",", start);
                if (end == -1) end = message.length();
                return message.substring(start, end).trim();
            }
            
            // Fall back to logger name
            return logger.substring(logger.lastIndexOf('.') + 1);
        }
        
        // Getters
        public Instant getTimestamp() { return timestamp; }
        public String getNodeId() { return nodeId; }
        public String getEventType() { return eventType; }
        public String getLevel() { return level; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
        
        public String getDataValue(String key) {
            Object value = data.get(key);
            return value != null ? value.toString() : null;
        }
    }
    
    /**
     * Analysis of consensus operations.
     */
    public static class ConsensusAnalysis {
        private final List<LogEntry> consensusEntries;
        private final Map<String, List<LogEntry>> proposalTraces;
        
        public ConsensusAnalysis(List<LogEntry> allEntries) {
            this.consensusEntries = allEntries.stream()
                .filter(entry -> "consensus_operation".equals(entry.getEventType()))
                .collect(Collectors.toList());
            
            this.proposalTraces = consensusEntries.stream()
                .filter(entry -> entry.getDataValue("proposalId") != null)
                .collect(Collectors.groupingBy(entry -> entry.getDataValue("proposalId")));
        }
        
        public int getTotalConsensusOperations() {
            return consensusEntries.size();
        }
        
        public Map<String, Long> getOperationCounts() {
            return consensusEntries.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getDataValue("operation"),
                    Collectors.counting()
                ));
        }
        
        public List<String> getCompletedProposals() {
            return proposalTraces.entrySet().stream()
                .filter(entry -> hasCompleteConsensusFlow(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        public List<String> getFailedProposals() {
            return proposalTraces.entrySet().stream()
                .filter(entry -> !hasCompleteConsensusFlow(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        private boolean hasCompleteConsensusFlow(List<LogEntry> entries) {
            Set<String> phases = entries.stream()
                .map(entry -> entry.getDataValue("phase"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            
            return phases.contains("propose") && phases.contains("vote") && phases.contains("commit");
        }
        
        public void printSummary() {
            System.out.println("=== Consensus Analysis Summary ===");
            System.out.println("Total consensus operations: " + getTotalConsensusOperations());
            System.out.println("Operation counts: " + getOperationCounts());
            System.out.println("Completed proposals: " + getCompletedProposals().size());
            System.out.println("Failed proposals: " + getFailedProposals().size());
            System.out.println("Success rate: " + 
                String.format("%.2f%%", 
                    (double) getCompletedProposals().size() / proposalTraces.size() * 100));
        }
    }
    
    /**
     * Analysis of performance metrics.
     */
    public static class PerformanceAnalysis {
        private final List<LogEntry> performanceEntries;
        
        public PerformanceAnalysis(List<LogEntry> allEntries) {
            this.performanceEntries = allEntries.stream()
                .filter(entry -> "performance_metrics".equals(entry.getEventType()))
                .collect(Collectors.toList());
        }
        
        public Map<String, Double> getAverageDurations() {
            return performanceEntries.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getDataValue("operation"),
                    Collectors.averagingDouble(entry -> {
                        String duration = entry.getDataValue("durationMs");
                        return duration != null ? Double.parseDouble(duration) : 0.0;
                    })
                ));
        }
        
        public Map<String, Double> getAverageMessageCounts() {
            return performanceEntries.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getDataValue("operation"),
                    Collectors.averagingDouble(entry -> {
                        String count = entry.getDataValue("messageCount");
                        return count != null ? Double.parseDouble(count) : 0.0;
                    })
                ));
        }
        
        public void printSummary() {
            System.out.println("=== Performance Analysis Summary ===");
            System.out.println("Average durations (ms): " + getAverageDurations());
            System.out.println("Average message counts: " + getAverageMessageCounts());
        }
    }
    
    /**
     * Analysis of error patterns.
     */
    public static class ErrorAnalysis {
        private final List<LogEntry> errorEntries;
        
        public ErrorAnalysis(List<LogEntry> allEntries) {
            this.errorEntries = allEntries.stream()
                .filter(entry -> "error".equals(entry.getEventType()) || "ERROR".equals(entry.getLevel()))
                .collect(Collectors.toList());
        }
        
        public Map<String, Long> getErrorCounts() {
            return errorEntries.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getDataValue("operation"),
                    Collectors.counting()
                ));
        }
        
        public Map<String, Long> getErrorTypes() {
            return errorEntries.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.getDataValue("exceptionType"),
                    Collectors.counting()
                ));
        }
        
        public void printSummary() {
            System.out.println("=== Error Analysis Summary ===");
            System.out.println("Total errors: " + errorEntries.size());
            System.out.println("Error counts by operation: " + getErrorCounts());
            System.out.println("Error types: " + getErrorTypes());
        }
    }
    
    /**
     * Trace of a specific consensus operation.
     */
    public static class ConsensusTrace {
        private final List<LogEntry> traceEntries;
        private final String proposalId;
        
        public ConsensusTrace(List<LogEntry> allEntries, String proposalId) {
            this.proposalId = proposalId;
            this.traceEntries = allEntries.stream()
                .filter(entry -> proposalId.equals(entry.getDataValue("proposalId")))
                .collect(Collectors.toList());
        }
        
        public void printTrace() {
            System.out.println("=== Consensus Trace for Proposal: " + proposalId + " ===");
            for (LogEntry entry : traceEntries) {
                System.out.printf("[%s] %s - %s: %s%n",
                    entry.getTimestamp(),
                    entry.getNodeId(),
                    entry.getEventType(),
                    entry.getMessage()
                );
            }
        }
        
        public List<LogEntry> getTraceEntries() {
            return traceEntries;
        }
    }
}