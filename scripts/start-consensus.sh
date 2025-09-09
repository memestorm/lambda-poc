#!/bin/bash

# Start Consensus Federation Script
echo "Starting Lambda Consensus Federation..."

# Build the application first
echo "Building application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed. Exiting."
    exit 1
fi

# Start all services
echo "Starting Docker containers..."
docker-compose up --build -d

# Wait for services to be healthy
echo "Waiting for services to start..."
sleep 30

# Check service health
echo "Checking service health..."
docker-compose ps

echo "Consensus federation started successfully!"
echo "Lambda endpoints:"
echo "  - Lambda 1: http://localhost:9001"
echo "  - Lambda 2: http://localhost:9002"
echo "  - Lambda 3: http://localhost:9003"
echo "  - Lambda 4: http://localhost:9004"
echo "  - Lambda 5: http://localhost:9005"
echo "SQS Management UI: http://localhost:9325"
echo ""
echo "To view logs: docker-compose logs -f [service-name]"
echo "To stop: ./scripts/stop-consensus.sh"