#!/bin/bash

# Test Consensus Script
echo "Testing consensus by sending increment requests..."

# Function to send increment request to a lambda
send_increment() {
    local node_num=$1
    local port=$((9000 + node_num))
    
    echo "Sending increment request to lambda-${node_num} on port ${port}..."
    
    curl -X POST "http://localhost:${port}/2015-03-31/functions/function/invocations" \
         -H "Content-Type: application/json" \
         -d '{
           "type": "INCREMENT_REQUEST",
           "sourceNodeId": "test-client",
           "targetNodeId": "lambda-'${node_num}'",
           "proposedValue": null,
           "proposalId": null,
           "metadata": {}
         }'
    
    echo ""
}

# Send increment requests to different nodes
for i in {1..3}; do
    send_increment $((RANDOM % 5 + 1))
    sleep 2
done

echo "Test requests sent. Check logs to see consensus in action:"
echo "docker-compose logs -f"