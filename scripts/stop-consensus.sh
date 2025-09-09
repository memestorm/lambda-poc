#!/bin/bash

# Stop Consensus Federation Script
echo "Stopping Lambda Consensus Federation..."

# Stop all containers
docker-compose down

# Remove any orphaned containers
docker-compose down --remove-orphans

# Optional: Remove volumes (uncomment if you want to clean up data)
# docker-compose down -v

echo "Consensus federation stopped successfully!"