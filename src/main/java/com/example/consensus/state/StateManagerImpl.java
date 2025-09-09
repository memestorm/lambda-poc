package com.example.consensus.state;

import com.example.consensus.logging.StructuredLogger;
import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.NodeState;
import com.example.consensus.model.Vote;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe implementation of StateManager for managing Lambda node state.
 * Uses read-write locks to ensure thread safety while allowing concurrent reads.
 */
public class StateManagerImpl implements StateManager {
    
    private static final Logger logger = LoggerFactory.getLogger(StateManagerImpl.class);
    
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final String nodeId;
    private final StructuredLogger structuredLogger;
    
    // Mutable state protected by locks
    private volatile Long currentCount;
    private volatile ConsensusState consensusState;
    private volatile String currentProposalId;
    private volatile boolean isRecovering;
    private volatile Instant lastHeartbeat;
    
    // Thread-safe collections
    private final Set<String> knownNodes;
    private final Map<String, Vote> receivedVotes;
    
    /**
     * Creates a new StateManager for the specified node.
     * 
     * @param nodeId The unique identifier for this Lambda node
     */
    public StateManagerImpl(String nodeId) {
        this.nodeId = nodeId;
        this.structuredLogger = new StructuredLogger(StateManagerImpl.class, nodeId);
        this.currentCount = 0L;
        this.consensusState = ConsensusState.IDLE;
        this.currentProposalId = null;
        this.isRecovering = false;
        this.lastHeartbeat = Instant.now();
        this.knownNodes = ConcurrentHashMap.newKeySet();
        this.receivedVotes = new ConcurrentHashMap<>();
        
        logger.info("StateManager initialized for node: {}", nodeId);
        
        // Log node lifecycle event
        structuredLogger.logNodeLifecycle(StructuredLogger.NodeLifecycleEvent.STARTED, 
            Map.of(
                "initialCount", currentCount,
                "initialState", consensusState.toString()
            ));
    }
    
    @Override
    public Long getCurrentCount() {
        lock.readLock().lock();
        try {
            return currentCount;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean updateCount(Long newCount) {
        if (newCount == null || newCount < 0) {
            logger.warn("Invalid count value: {}", newCount);
            structuredLogger.logError("updateCount", "Invalid count value", null, 0, 0, 
                Map.of("attemptedValue", newCount != null ? newCount.toString() : "null"));
            return false;
        }
        
        lock.writeLock().lock();
        try {
            Long oldCount = this.currentCount;
            this.currentCount = newCount;
            logger.info("Node {} count updated from {} to {}", nodeId, oldCount, newCount);
            
            // Log state change
            structuredLogger.logStateTransition(
                oldCount.toString(), newCount.toString(), "Count updated",
                Map.of(
                    "previousCount", oldCount,
                    "newCount", newCount,
                    "consensusState", consensusState.toString()
                )
            );
            
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public ConsensusState getConsensusState() {
        lock.readLock().lock();
        try {
            return consensusState;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean transitionToState(ConsensusState newState) {
        if (newState == null) {
            logger.warn("Cannot transition to null state");
            structuredLogger.logError("transitionToState", "Cannot transition to null state", 
                null, 0, 0, Map.of("currentState", consensusState.toString()));
            return false;
        }
        
        lock.writeLock().lock();
        try {
            if (!isValidTransition(newState)) {
                logger.warn("Invalid state transition from {} to {} for node {}", 
                           consensusState, newState, nodeId);
                structuredLogger.logError("transitionToState", "Invalid state transition", 
                    null, 0, 0, Map.of(
                        "fromState", consensusState.toString(),
                        "toState", newState.toString()
                    ));
                return false;
            }
            
            ConsensusState oldState = this.consensusState;
            this.consensusState = newState;
            logger.info("Node {} transitioned from {} to {}", nodeId, oldState, newState);
            
            // Log structured state transition
            structuredLogger.logStateTransition(
                oldState.toString(), newState.toString(), "Consensus state transition",
                Map.of(
                    "currentCount", currentCount,
                    "currentProposalId", currentProposalId != null ? currentProposalId : "null",
                    "receivedVotesCount", receivedVotes.size(),
                    "isRecovering", isRecovering
                )
            );
            
            // Clear votes when transitioning to IDLE or starting new proposal
            if (newState == ConsensusState.IDLE || newState == ConsensusState.PROPOSING) {
                int clearedVotes = receivedVotes.size();
                receivedVotes.clear();
                if (newState == ConsensusState.IDLE) {
                    currentProposalId = null;
                }
                
                if (clearedVotes > 0) {
                    logger.debug("Cleared {} votes during state transition to {}", clearedVotes, newState);
                }
            }
            
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public String getNodeId() {
        return nodeId;
    }
    
    @Override
    public Set<String> getKnownNodes() {
        return new HashSet<>(knownNodes);
    }
    
    @Override
    public boolean addKnownNode(String nodeId) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            return false;
        }
        
        boolean added = knownNodes.add(nodeId);
        if (added) {
            logger.debug("Added known node: {}", nodeId);
        }
        return added;
    }
    
    @Override
    public boolean removeKnownNode(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        
        boolean removed = knownNodes.remove(nodeId);
        if (removed) {
            logger.debug("Removed known node: {}", nodeId);
            // Also remove any votes from this node
            receivedVotes.remove(nodeId);
        }
        return removed;
    }
    
    @Override
    public String getCurrentProposalId() {
        lock.readLock().lock();
        try {
            return currentProposalId;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void setCurrentProposalId(String proposalId) {
        lock.writeLock().lock();
        try {
            this.currentProposalId = proposalId;
            // Clear existing votes when setting new proposal
            if (proposalId != null) {
                receivedVotes.clear();
            }
            logger.debug("Node {} set current proposal ID to: {}", nodeId, proposalId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean recordVote(String voterNodeId, Vote vote) {
        if (voterNodeId == null || vote == null) {
            logger.warn("Cannot record null vote or voter");
            return false;
        }
        
        // Validate vote is for current proposal
        String currentProposal = getCurrentProposalId();
        if (currentProposal == null || !currentProposal.equals(vote.getProposalId())) {
            logger.warn("Vote proposal ID {} does not match current proposal {}", 
                       vote.getProposalId(), currentProposal);
            return false;
        }
        
        Vote previousVote = receivedVotes.put(voterNodeId, vote);
        logger.debug("Recorded vote from node {}: {} (replaced: {})", 
                    voterNodeId, vote.isAccept() ? "ACCEPT" : "REJECT", previousVote != null);
        return true;
    }
    
    @Override
    public Map<String, Vote> getReceivedVotes() {
        return new HashMap<>(receivedVotes);
    }
    
    @Override
    public void clearVotes() {
        receivedVotes.clear();
        logger.debug("Cleared all votes for node {}", nodeId);
    }
    
    @Override
    public boolean hasQuorum() {
        int totalNodes = knownNodes.size() + 1; // +1 for this node
        int requiredQuorum = (totalNodes / 2) + 1;
        int receivedVoteCount = receivedVotes.size() + 1; // +1 for this node's implicit vote
        
        boolean hasQuorum = receivedVoteCount >= requiredQuorum;
        logger.debug("Node {} quorum check: {}/{} votes, quorum: {}", 
                    nodeId, receivedVoteCount, totalNodes, hasQuorum);
        return hasQuorum;
    }
    
    @Override
    public boolean hasMajorityAcceptance() {
        long acceptCount = receivedVotes.values().stream()
                .mapToLong(vote -> vote.isAccept() ? 1 : 0)
                .sum();
        
        // Add this node's implicit acceptance if it's the proposer
        if (consensusState == ConsensusState.PROPOSING) {
            acceptCount++;
        }
        
        int totalVotes = receivedVotes.size() + (consensusState == ConsensusState.PROPOSING ? 1 : 0);
        
        // If no votes at all, return false
        if (totalVotes == 0) {
            return false;
        }
        
        boolean hasMajority = acceptCount > (totalVotes / 2);
        
        logger.debug("Node {} majority check: {}/{} accept votes, majority: {}", 
                    nodeId, acceptCount, totalVotes, hasMajority);
        return hasMajority;
    }
    
    @Override
    public void updateHeartbeat() {
        lock.writeLock().lock();
        try {
            this.lastHeartbeat = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public Instant getLastHeartbeat() {
        lock.readLock().lock();
        try {
            return lastHeartbeat;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean isRecovering() {
        lock.readLock().lock();
        try {
            return isRecovering;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void setRecovering(boolean recovering) {
        lock.writeLock().lock();
        try {
            this.isRecovering = recovering;
            if (recovering) {
                // Clear consensus state when entering recovery, but don't automatically change state
                // The state transition should be done explicitly via transitionToState
                this.currentProposalId = null;
                this.receivedVotes.clear();
            }
            logger.info("Node {} recovery state set to: {}", nodeId, recovering);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public NodeState getNodeState() {
        lock.readLock().lock();
        try {
            return new NodeState(
                nodeId,
                currentCount,
                consensusState,
                new HashSet<>(knownNodes),
                currentProposalId,
                new HashMap<>(receivedVotes),
                lastHeartbeat,
                isRecovering
            );
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public void resetState(boolean preserveNodeId) {
        lock.writeLock().lock();
        try {
            this.currentCount = 0L;
            this.consensusState = ConsensusState.IDLE;
            this.currentProposalId = null;
            this.isRecovering = false;
            this.lastHeartbeat = Instant.now();
            this.receivedVotes.clear();
            
            if (!preserveNodeId) {
                this.knownNodes.clear();
            }
            
            logger.info("Node {} state reset (preserveNodeId: {})", nodeId, preserveNodeId);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    @Override
    public boolean isValidTransition(ConsensusState targetState) {
        if (targetState == null) {
            return false;
        }
        
        ConsensusState currentState = getConsensusState();
        
        // Define valid state transitions
        switch (currentState) {
            case IDLE:
                return targetState == ConsensusState.PROPOSING || 
                       targetState == ConsensusState.VOTING || 
                       targetState == ConsensusState.RECOVERING;
                       
            case PROPOSING:
                return targetState == ConsensusState.COMMITTING || 
                       targetState == ConsensusState.IDLE ||
                       targetState == ConsensusState.RECOVERING;
                       
            case VOTING:
                return targetState == ConsensusState.COMMITTING || 
                       targetState == ConsensusState.IDLE ||
                       targetState == ConsensusState.RECOVERING;
                       
            case COMMITTING:
                return targetState == ConsensusState.IDLE ||
                       targetState == ConsensusState.RECOVERING;
                       
            case RECOVERING:
                return targetState == ConsensusState.IDLE ||
                       targetState == ConsensusState.VOTING ||
                       targetState == ConsensusState.PROPOSING;
                       
            default:
                return false;
        }
    }
}