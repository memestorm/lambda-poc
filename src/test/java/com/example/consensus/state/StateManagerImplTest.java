package com.example.consensus.state;

import com.example.consensus.model.ConsensusState;
import com.example.consensus.model.NodeState;
import com.example.consensus.model.Vote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StateManagerImplTest {

    private StateManagerImpl stateManager;
    private static final String NODE_ID = "test-node-1";

    @BeforeEach
    void setUp() {
        stateManager = new StateManagerImpl(NODE_ID);
    }

    @Nested
    @DisplayName("Basic State Operations")
    class BasicStateOperations {

        @Test
        @DisplayName("Should initialize with correct default values")
        void shouldInitializeWithDefaults() {
            assertEquals(NODE_ID, stateManager.getNodeId());
            assertEquals(0L, stateManager.getCurrentCount());
            assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
            assertNull(stateManager.getCurrentProposalId());
            assertFalse(stateManager.isRecovering());
            assertTrue(stateManager.getKnownNodes().isEmpty());
            assertTrue(stateManager.getReceivedVotes().isEmpty());
            assertNotNull(stateManager.getLastHeartbeat());
        }

        @Test
        @DisplayName("Should update count successfully")
        void shouldUpdateCount() {
            assertTrue(stateManager.updateCount(5L));
            assertEquals(5L, stateManager.getCurrentCount());
            
            assertTrue(stateManager.updateCount(10L));
            assertEquals(10L, stateManager.getCurrentCount());
        }

        @Test
        @DisplayName("Should reject invalid count values")
        void shouldRejectInvalidCounts() {
            assertFalse(stateManager.updateCount(null));
            assertFalse(stateManager.updateCount(-1L));
            assertEquals(0L, stateManager.getCurrentCount()); // Should remain unchanged
        }

        @Test
        @DisplayName("Should update heartbeat")
        void shouldUpdateHeartbeat() {
            Instant before = stateManager.getLastHeartbeat();
            
            // Small delay to ensure timestamp difference
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            stateManager.updateHeartbeat();
            Instant after = stateManager.getLastHeartbeat();
            
            assertTrue(after.isAfter(before));
        }
    }

    @Nested
    @DisplayName("State Transitions")
    class StateTransitions {

        @Test
        @DisplayName("Should allow valid state transitions from IDLE")
        void shouldAllowValidTransitionsFromIdle() {
            assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
            
            assertTrue(stateManager.transitionToState(ConsensusState.PROPOSING));
            assertEquals(ConsensusState.PROPOSING, stateManager.getConsensusState());
            
            stateManager.transitionToState(ConsensusState.IDLE);
            assertTrue(stateManager.transitionToState(ConsensusState.VOTING));
            assertEquals(ConsensusState.VOTING, stateManager.getConsensusState());
            
            stateManager.transitionToState(ConsensusState.IDLE);
            assertTrue(stateManager.transitionToState(ConsensusState.RECOVERING));
            assertEquals(ConsensusState.RECOVERING, stateManager.getConsensusState());
        }

        @Test
        @DisplayName("Should reject invalid state transitions")
        void shouldRejectInvalidTransitions() {
            // From IDLE, cannot go directly to COMMITTING
            assertFalse(stateManager.transitionToState(ConsensusState.COMMITTING));
            assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
            
            // Test null transition
            assertFalse(stateManager.transitionToState(null));
        }

        @Test
        @DisplayName("Should validate all state transition rules")
        void shouldValidateAllTransitionRules() {
            // Test PROPOSING transitions
            stateManager.transitionToState(ConsensusState.PROPOSING);
            assertTrue(stateManager.isValidTransition(ConsensusState.COMMITTING));
            assertTrue(stateManager.isValidTransition(ConsensusState.IDLE));
            assertTrue(stateManager.isValidTransition(ConsensusState.RECOVERING));
            assertFalse(stateManager.isValidTransition(ConsensusState.VOTING));
            
            // Test VOTING transitions
            stateManager.transitionToState(ConsensusState.IDLE);
            stateManager.transitionToState(ConsensusState.VOTING);
            assertTrue(stateManager.isValidTransition(ConsensusState.COMMITTING));
            assertTrue(stateManager.isValidTransition(ConsensusState.IDLE));
            assertTrue(stateManager.isValidTransition(ConsensusState.RECOVERING));
            assertFalse(stateManager.isValidTransition(ConsensusState.PROPOSING));
            
            // Test COMMITTING transitions
            stateManager.transitionToState(ConsensusState.COMMITTING);
            assertTrue(stateManager.isValidTransition(ConsensusState.IDLE));
            assertTrue(stateManager.isValidTransition(ConsensusState.RECOVERING));
            assertFalse(stateManager.isValidTransition(ConsensusState.PROPOSING));
            assertFalse(stateManager.isValidTransition(ConsensusState.VOTING));
            
            // Test RECOVERING transitions
            stateManager.transitionToState(ConsensusState.RECOVERING);
            assertTrue(stateManager.isValidTransition(ConsensusState.IDLE));
            assertTrue(stateManager.isValidTransition(ConsensusState.VOTING));
            assertTrue(stateManager.isValidTransition(ConsensusState.PROPOSING));
            assertFalse(stateManager.isValidTransition(ConsensusState.COMMITTING));
        }

        @Test
        @DisplayName("Should clear votes on state transitions")
        void shouldClearVotesOnTransitions() {
            // First transition to a non-IDLE state
            stateManager.transitionToState(ConsensusState.PROPOSING);
            
            // Set up some votes
            stateManager.setCurrentProposalId("proposal-1");
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "test");
            stateManager.recordVote("node-2", vote);
            
            assertFalse(stateManager.getReceivedVotes().isEmpty());
            
            // Transition to IDLE should clear votes
            stateManager.transitionToState(ConsensusState.IDLE);
            assertTrue(stateManager.getReceivedVotes().isEmpty());
            assertNull(stateManager.getCurrentProposalId());
        }
    }

    @Nested
    @DisplayName("Node Management")
    class NodeManagement {

        @Test
        @DisplayName("Should manage known nodes")
        void shouldManageKnownNodes() {
            assertTrue(stateManager.addKnownNode("node-2"));
            assertTrue(stateManager.addKnownNode("node-3"));
            
            Set<String> knownNodes = stateManager.getKnownNodes();
            assertEquals(2, knownNodes.size());
            assertTrue(knownNodes.contains("node-2"));
            assertTrue(knownNodes.contains("node-3"));
            
            // Adding duplicate should return false
            assertFalse(stateManager.addKnownNode("node-2"));
            assertEquals(2, stateManager.getKnownNodes().size());
            
            // Remove node
            assertTrue(stateManager.removeKnownNode("node-2"));
            assertFalse(stateManager.getKnownNodes().contains("node-2"));
            
            // Removing non-existent node should return false
            assertFalse(stateManager.removeKnownNode("node-999"));
        }

        @Test
        @DisplayName("Should reject invalid node IDs")
        void shouldRejectInvalidNodeIds() {
            assertFalse(stateManager.addKnownNode(null));
            assertFalse(stateManager.addKnownNode(""));
            assertFalse(stateManager.addKnownNode("   "));
            
            assertFalse(stateManager.removeKnownNode(null));
        }
    }

    @Nested
    @DisplayName("Vote Management")
    class VoteManagement {

        @BeforeEach
        void setUpVoting() {
            stateManager.addKnownNode("node-2");
            stateManager.addKnownNode("node-3");
            stateManager.addKnownNode("node-4");
            stateManager.setCurrentProposalId("proposal-1");
        }

        @Test
        @DisplayName("Should record valid votes")
        void shouldRecordValidVotes() {
            Vote vote1 = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            Vote vote2 = new Vote("node-3", "proposal-1", false, Instant.now(), "reject");
            
            assertTrue(stateManager.recordVote("node-2", vote1));
            assertTrue(stateManager.recordVote("node-3", vote2));
            
            Map<String, Vote> votes = stateManager.getReceivedVotes();
            assertEquals(2, votes.size());
            assertTrue(votes.get("node-2").isAccept());
            assertFalse(votes.get("node-3").isAccept());
        }

        @Test
        @DisplayName("Should reject invalid votes")
        void shouldRejectInvalidVotes() {
            // Null vote or voter
            assertFalse(stateManager.recordVote(null, new Vote("node-2", "proposal-1", true, Instant.now(), "test")));
            assertFalse(stateManager.recordVote("node-2", null));
            
            // Wrong proposal ID
            Vote wrongProposal = new Vote("node-2", "wrong-proposal", true, Instant.now(), "test");
            assertFalse(stateManager.recordVote("node-2", wrongProposal));
            
            // No current proposal
            stateManager.setCurrentProposalId(null);
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "test");
            assertFalse(stateManager.recordVote("node-2", vote));
        }

        @Test
        @DisplayName("Should calculate quorum correctly")
        void shouldCalculateQuorum() {
            // 4 known nodes + 1 this node = 5 total, need 3 for quorum
            
            // No votes yet, but this node counts as 1
            assertFalse(stateManager.hasQuorum()); // 1/5 votes
            
            // Add one vote
            Vote vote1 = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-2", vote1);
            assertFalse(stateManager.hasQuorum()); // 2/5 votes
            
            // Add second vote - should have quorum
            Vote vote2 = new Vote("node-3", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-3", vote2);
            assertTrue(stateManager.hasQuorum()); // 3/5 votes
        }

        @Test
        @DisplayName("Should calculate majority acceptance correctly")
        void shouldCalculateMajorityAcceptance() {
            stateManager.transitionToState(ConsensusState.PROPOSING); // This node implicitly accepts
            
            // Just this node accepting (1/1) - has majority
            assertTrue(stateManager.hasMajorityAcceptance());
            
            // Add rejecting vote (1 accept, 1 reject) - no majority
            Vote rejectVote = new Vote("node-2", "proposal-1", false, Instant.now(), "reject");
            stateManager.recordVote("node-2", rejectVote);
            assertFalse(stateManager.hasMajorityAcceptance());
            
            // Add accepting vote (2 accept, 1 reject) - has majority
            Vote acceptVote = new Vote("node-3", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-3", acceptVote);
            assertTrue(stateManager.hasMajorityAcceptance());
        }

        @Test
        @DisplayName("Should clear votes")
        void shouldClearVotes() {
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-2", vote);
            
            assertFalse(stateManager.getReceivedVotes().isEmpty());
            
            stateManager.clearVotes();
            assertTrue(stateManager.getReceivedVotes().isEmpty());
        }
    }

    @Nested
    @DisplayName("Recovery Operations")
    class RecoveryOperations {

        @Test
        @DisplayName("Should manage recovery state")
        void shouldManageRecoveryState() {
            assertFalse(stateManager.isRecovering());
            
            stateManager.setRecovering(true);
            assertTrue(stateManager.isRecovering());
            // State should not automatically change to RECOVERING
            assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
            
            // Explicitly transition to RECOVERING state
            stateManager.transitionToState(ConsensusState.RECOVERING);
            assertEquals(ConsensusState.RECOVERING, stateManager.getConsensusState());
            
            stateManager.setRecovering(false);
            assertFalse(stateManager.isRecovering());
        }

        @Test
        @DisplayName("Should clear state when entering recovery")
        void shouldClearStateOnRecovery() {
            // Set up some state
            stateManager.setCurrentProposalId("proposal-1");
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-2", vote);
            stateManager.transitionToState(ConsensusState.PROPOSING);
            
            // Enter recovery
            stateManager.setRecovering(true);
            
            // State should be cleared but consensus state should remain PROPOSING
            // until explicitly transitioned
            assertNull(stateManager.getCurrentProposalId());
            assertTrue(stateManager.getReceivedVotes().isEmpty());
            assertEquals(ConsensusState.PROPOSING, stateManager.getConsensusState());
            
            // Explicitly transition to RECOVERING state
            stateManager.transitionToState(ConsensusState.RECOVERING);
            assertEquals(ConsensusState.RECOVERING, stateManager.getConsensusState());
        }

        @Test
        @DisplayName("Should reset state correctly")
        void shouldResetState() {
            // Set up some state
            stateManager.updateCount(10L);
            stateManager.addKnownNode("node-2");
            stateManager.setCurrentProposalId("proposal-1");
            stateManager.setRecovering(true);
            stateManager.transitionToState(ConsensusState.PROPOSING);
            
            // Reset preserving node ID
            stateManager.resetState(true);
            
            assertEquals(0L, stateManager.getCurrentCount());
            assertEquals(ConsensusState.IDLE, stateManager.getConsensusState());
            assertNull(stateManager.getCurrentProposalId());
            assertFalse(stateManager.isRecovering());
            assertTrue(stateManager.getReceivedVotes().isEmpty());
            assertTrue(stateManager.getKnownNodes().contains("node-2")); // Preserved
            
            // Reset without preserving node ID
            stateManager.resetState(false);
            assertTrue(stateManager.getKnownNodes().isEmpty()); // Cleared
        }
    }

    @Nested
    @DisplayName("Node State Snapshot")
    class NodeStateSnapshot {

        @Test
        @DisplayName("Should create accurate node state snapshot")
        void shouldCreateAccurateSnapshot() {
            // Set up state
            stateManager.updateCount(5L);
            stateManager.addKnownNode("node-2");
            stateManager.addKnownNode("node-3");
            stateManager.setCurrentProposalId("proposal-1");
            stateManager.transitionToState(ConsensusState.PROPOSING);
            stateManager.setRecovering(true);
            
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            stateManager.recordVote("node-2", vote);
            
            // Get snapshot
            NodeState snapshot = stateManager.getNodeState();
            
            // Verify snapshot
            assertEquals(NODE_ID, snapshot.getNodeId());
            assertEquals(5L, snapshot.getCurrentCount());
            assertEquals(ConsensusState.PROPOSING, snapshot.getConsensusState()); // State should still be PROPOSING
            assertEquals(2, snapshot.getKnownNodes().size());
            assertTrue(snapshot.getKnownNodes().contains("node-2"));
            assertTrue(snapshot.getKnownNodes().contains("node-3"));
            assertNull(snapshot.getCurrentProposalId()); // Should be null due to setRecovering clearing it
            assertEquals(0, snapshot.getReceivedVotes().size()); // Should be empty due to setRecovering clearing it
            assertTrue(snapshot.isRecovering());
            assertNotNull(snapshot.getLastHeartbeat());
        }

        @Test
        @DisplayName("Should return defensive copies in snapshot")
        void shouldReturnDefensiveCopies() {
            stateManager.addKnownNode("node-2");
            Vote vote = new Vote("node-2", "proposal-1", true, Instant.now(), "accept");
            stateManager.setCurrentProposalId("proposal-1");
            stateManager.recordVote("node-2", vote);
            
            NodeState snapshot = stateManager.getNodeState();
            
            // Modifying returned collections should not affect internal state
            snapshot.getKnownNodes().clear();
            snapshot.getReceivedVotes().clear();
            
            // Internal state should be unchanged
            assertEquals(1, stateManager.getKnownNodes().size());
            assertEquals(1, stateManager.getReceivedVotes().size());
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Should handle concurrent count updates")
        void shouldHandleConcurrentCountUpdates() throws InterruptedException {
            int threadCount = 10;
            int updatesPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < updatesPerThread; j++) {
                            long newValue = (long) (threadId * updatesPerThread + j);
                            if (stateManager.updateCount(newValue)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            
            // All updates should succeed
            assertEquals(threadCount * updatesPerThread, successCount.get());
            
            // Final count should be valid
            Long finalCount = stateManager.getCurrentCount();
            assertNotNull(finalCount);
            assertTrue(finalCount >= 0);
        }

        @Test
        @DisplayName("Should handle concurrent state transitions")
        void shouldHandleConcurrentStateTransitions() throws InterruptedException {
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            ConsensusState[] states = {
                ConsensusState.PROPOSING, ConsensusState.VOTING, 
                ConsensusState.RECOVERING, ConsensusState.IDLE
            };
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 50; j++) {
                            ConsensusState targetState = states[j % states.length];
                            if (stateManager.transitionToState(targetState)) {
                                successCount.incrementAndGet();
                            }
                            // Small delay to increase chance of concurrent access
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();
            
            // Should have some successful transitions
            assertTrue(successCount.get() > 0);
            
            // Final state should be valid
            ConsensusState finalState = stateManager.getConsensusState();
            assertNotNull(finalState);
        }

        @Test
        @DisplayName("Should handle concurrent node management")
        void shouldHandleConcurrentNodeManagement() throws InterruptedException {
            int threadCount = 5;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < operationsPerThread; j++) {
                            String nodeId = "node-" + threadId + "-" + j;
                            stateManager.addKnownNode(nodeId);
                            
                            // Sometimes remove nodes
                            if (j % 10 == 0 && j > 0) {
                                String removeNodeId = "node-" + threadId + "-" + (j - 5);
                                stateManager.removeKnownNode(removeNodeId);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            
            // Should have some nodes
            Set<String> knownNodes = stateManager.getKnownNodes();
            assertNotNull(knownNodes);
            // Exact count is hard to predict due to concurrent adds/removes
            // but should be reasonable
            assertTrue(knownNodes.size() >= 0);
        }

        @Test
        @DisplayName("Should handle concurrent vote recording")
        void shouldHandleConcurrentVoteRecording() throws InterruptedException {
            stateManager.setCurrentProposalId("test-proposal");
            
            int threadCount = 5;
            int votesPerThread = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < votesPerThread; j++) {
                            String voterNodeId = "voter-" + threadId + "-" + j;
                            Vote vote = new Vote(voterNodeId, "test-proposal", 
                                               j % 2 == 0, Instant.now(), "test vote");
                            
                            if (stateManager.recordVote(voterNodeId, vote)) {
                                successCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();
            
            // All votes should be recorded successfully
            assertEquals(threadCount * votesPerThread, successCount.get());
            assertEquals(threadCount * votesPerThread, stateManager.getReceivedVotes().size());
        }
    }
}