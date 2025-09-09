# Implementation Plan

- [x] 1. Set up project structure and core interfaces
  - Create Maven project structure with proper directory layout
  - Define core interfaces for ConsensusManager, SQSMessageHandler, and StateManager
  - Set up Maven dependencies for AWS SDK v2, Lambda runtime, and testing frameworks
  - _Requirements: 1.1, 1.3_

- [x] 2. Implement message models and serialization
  - Create ConsensusRequest and ConsensusResponse data classes
  - Implement MessageType enum and all message payload classes
  - Write JSON serialization/deserialization utilities for SQS message handling
  - Create unit tests for message serialization and validation
  - _Requirements: 2.1, 2.2_

- [ ] 3. Implement StateManager for node state tracking
  - Create NodeState class with current count, consensus state, and node membership
  - Implement state transition methods (idle, proposing, voting, committing, recovering)
  - Write thread-safe state update methods for concurrent access
  - Create unit tests for state transitions and concurrent access scenarios
  - _Requirements: 3.1, 3.2, 6.2_

- [ ] 4. Implement SQSMessageHandler for queue operations
  - Create SQS client wrapper with local endpoint configuration
  - Implement message sending methods for direct and broadcast queues
  - Write message polling and processing logic with proper error handling
  - Implement retry logic with exponential backoff for failed operations
  - Create unit tests for SQS operations and error scenarios
  - _Requirements: 2.1, 2.2, 2.4_

- [ ] 5. Implement core consensus algorithm
  - Create ConsensusManager class with proposal initiation logic
  - Implement voting phase processing for received proposals
  - Write commit phase logic for applying agreed-upon changes
  - Add timeout handling for consensus operations (60 seconds total, 10 seconds per vote)
  - Create unit tests for each consensus phase and timeout scenarios
  - _Requirements: 3.2, 3.3, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4_

- [x] 6. Implement node recovery mechanisms
  - Create recovery request/response message handling
  - Implement quorum validation logic (minimum 3 out of 5 nodes)
  - Write state synchronization logic for adopting majority count value
  - Add recovery retry logic with 30-second intervals
  - Create unit tests for recovery scenarios and quorum validation
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 7.1, 7.2, 7.3, 7.4_

- [x] 7. Implement Lambda function handler
  - Create ConsensusLambdaHandler implementing RequestHandler interface
  - Integrate ConsensusManager, SQSMessageHandler, and StateManager components
  - Implement request routing based on MessageType
  - Add proper error handling and response formatting
  - Create integration tests for handler with mock SQS operations
  - _Requirements: 4.1, 4.2, 8.1, 8.2_

- [x] 8. Create simple trigger service for random increment requests
  - Implement lightweight service that sends INCREMENT_REQUEST messages at random intervals
  - Add logic to randomly select target Lambda node for each increment request
  - Configure random interval timing (e.g., every 5-30 seconds)
  - Create simple logging for trigger events and target selection
  - Write unit tests for random selection and timing logic
  - _Requirements: 4.1, 8.1_

- [x] 9. Add comprehensive logging and monitoring
  - Implement structured JSON logging for all consensus operations
  - Add detailed logging for proposal, voting, and commit phases
  - Create performance metrics logging (consensus duration, message counts)
  - Implement error logging with context and retry information
  - Write log analysis utilities for debugging consensus issues
  - _Requirements: 8.1, 8.2, 8.4_

- [x] 10. Create Docker configuration and container setup
  - Write Dockerfile using AWS Lambda Java base image
  - Create Docker Compose configuration for 5 Lambda containers plus trigger service
  - Set up local SQS service (ElasticMQ) in Docker Compose
  - Configure environment variables for node IDs and SQS endpoints
  - Create container startup scripts and integrate trigger service
  - _Requirements: 1.1, 1.2, 1.4_

- [x] 11. Implement integration tests for multi-node scenarios
  - Create Testcontainers-based integration tests
  - Write tests for successful consensus with all 5 nodes
  - Implement tests for node failure and recovery scenarios
  - Create tests for concurrent increment requests and conflict resolution
  - Add tests for network partition simulation and quorum behavior
  - _Requirements: 3.3, 4.4, 5.4, 6.3, 6.4, 7.1, 7.2, 7.3_

- [x] 12. Create CLI trigger utility for testing
  - Implement command-line tool for sending increment requests to any Lambda
  - Add support for targeting specific nodes or broadcasting to all nodes
  - Create batch testing capabilities for load testing consensus operations
  - Implement log monitoring and result aggregation for test runs
  - Write documentation for CLI usage and testing scenarios
  - _Requirements: 4.1, 8.1_

- [x] 13. Add end-to-end system tests
  - Create comprehensive test suite covering all consensus scenarios
  - Implement automated testing for rolling restarts of individual nodes
  - Write performance tests measuring consensus latency and throughput
  - Create chaos testing scenarios with random node failures
  - Add validation tests ensuring all nodes converge to same count value
  - _Requirements: 3.2, 3.3, 4.4, 5.4, 6.2, 6.3_

- [x] 14. Optimize performance and add final polish
  - Profile consensus operations and optimize message processing
  - Implement connection pooling and resource management optimizations
  - Add graceful shutdown handling for clean container stops
  - Create comprehensive README with setup and usage instructions
  - Write troubleshooting guide for common issues and debugging
  - _Requirements: 1.2, 1.4, 8.1, 8.4_