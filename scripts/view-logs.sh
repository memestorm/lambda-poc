#!/bin/bash

# View Logs Script
if [ $# -eq 0 ]; then
    echo "Viewing logs for all services..."
    docker-compose logs -f
else
    SERVICE_NAME=$1
    echo "Viewing logs for ${SERVICE_NAME}..."
    docker-compose logs -f ${SERVICE_NAME}
fi