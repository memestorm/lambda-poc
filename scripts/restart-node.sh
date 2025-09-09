#!/bin/bash

# Restart Individual Node Script
if [ $# -eq 0 ]; then
    echo "Usage: $0 <node-number>"
    echo "Example: $0 1  (to restart lambda-1)"
    exit 1
fi

NODE_NUM=$1
SERVICE_NAME="lambda-${NODE_NUM}"

echo "Restarting ${SERVICE_NAME}..."

# Stop the specific service
docker-compose stop ${SERVICE_NAME}

# Wait a moment
sleep 5

# Start the service again
docker-compose up -d ${SERVICE_NAME}

echo "${SERVICE_NAME} restarted successfully!"
echo "Check logs with: docker-compose logs -f ${SERVICE_NAME}"