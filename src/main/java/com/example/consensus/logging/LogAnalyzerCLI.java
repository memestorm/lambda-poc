package com.example.consensus.logging;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Command-line interface for analyzing consensus system logs.
 * Provides utilities for debugging consensus issues and understanding system behavior.
 */
public class LogAnalyzerCLI {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String command = args[0];
        String logFilePath = args[1];
        Path logFile = Paths.get(logFilePath);
        
        try {
            switch (command.toLowerCase()) {
                case "consensus":
                    analyzeConsensus(logFile);
                    break;
                case "performance":
                    analyzePerformance(logFile);
                    break;
                case "errors":
                    analyzeErrors(logFile);
                    break;
                case "trace":
                    if (args.length < 3) {
                        System.err.println("Trace command requires proposal ID");
                        System.exit(1);
                    }
                    traceProposal(logFile, args[2]);
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
        } catch (IOException e) {
            System.err.println("Error reading log file: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error analyzing logs: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: LogAnalyzerCLI <command> <log-file> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  consensus <log-file>           - Analyze consensus operations");
        System.out.println("  performance <log-file>         - Analyze performance metrics");
        System.out.println("  errors <log-file>              - Analyze error patterns");
        System.out.println("  trace <log-file> <proposal-id> - Trace specific consensus operation");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java LogAnalyzerCLI consensus /var/log/consensus.log");
        System.out.println("  java LogAnalyzerCLI trace /var/log/consensus.log proposal-123");
    }
    
    private static void analyzeConsensus(Path logFile) throws IOException {
        System.out.println("Analyzing consensus operations from: " + logFile);
        System.out.println("=" .repeat(60));
        
        LogAnalyzer.ConsensusAnalysis analysis = LogAnalyzer.analyzeConsensusOperations(logFile);
        analysis.printSummary();
        
        System.out.println();
        System.out.println("Completed Proposals:");
        analysis.getCompletedProposals().forEach(proposalId -> 
            System.out.println("  - " + proposalId));
        
        System.out.println();
        System.out.println("Failed Proposals:");
        analysis.getFailedProposals().forEach(proposalId -> 
            System.out.println("  - " + proposalId));
    }
    
    private static void analyzePerformance(Path logFile) throws IOException {
        System.out.println("Analyzing performance metrics from: " + logFile);
        System.out.println("=" .repeat(60));
        
        LogAnalyzer.PerformanceAnalysis analysis = LogAnalyzer.analyzePerformance(logFile);
        analysis.printSummary();
    }
    
    private static void analyzeErrors(Path logFile) throws IOException {
        System.out.println("Analyzing error patterns from: " + logFile);
        System.out.println("=" .repeat(60));
        
        LogAnalyzer.ErrorAnalysis analysis = LogAnalyzer.analyzeErrors(logFile);
        analysis.printSummary();
    }
    
    private static void traceProposal(Path logFile, String proposalId) throws IOException {
        System.out.println("Tracing consensus operation: " + proposalId);
        System.out.println("Log file: " + logFile);
        System.out.println("=" .repeat(60));
        
        LogAnalyzer.ConsensusTrace trace = LogAnalyzer.traceConsensusOperation(logFile, proposalId);
        trace.printTrace();
        
        if (trace.getTraceEntries().isEmpty()) {
            System.out.println("No entries found for proposal ID: " + proposalId);
            System.out.println("Make sure the proposal ID is correct and exists in the log file.");
        }
    }
}