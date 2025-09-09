FROM public.ecr.aws/lambda/java:21

# Install additional tools for debugging and monitoring
RUN yum update -y && yum install -y \
    curl \
    jq \
    procps \
    && yum clean all

# Set JVM optimization flags for performance
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -XX:+OptimizeStringConcat"
ENV _JAVA_OPTIONS="-Xmx512m -Xms256m -XX:NewRatio=1 -XX:+UnlockExperimentalVMOptions"

# Performance and monitoring environment variables
ENV LAMBDA_RUNTIME_DIR="/var/runtime"
ENV LAMBDA_TASK_ROOT="/var/task"

# Copy the JAR file to the Lambda task root
COPY target/lambda-consensus-federation-1.0.0.jar ${LAMBDA_TASK_ROOT}/consensus-lambda.jar

# Copy configuration files for optimized settings
COPY src/main/resources/application.properties ${LAMBDA_TASK_ROOT}/
COPY src/main/resources/simplelogger.properties ${LAMBDA_TASK_ROOT}/

# Set proper permissions for security
RUN chmod 644 ${LAMBDA_TASK_ROOT}/*.jar ${LAMBDA_TASK_ROOT}/*.properties

# Add health check capability
RUN echo '#!/bin/bash\ncurl -f http://localhost:8080/health || exit 1' > /usr/local/bin/health-check.sh && \
    chmod +x /usr/local/bin/health-check.sh

# Set the CMD to your handler with optimized startup
CMD ["com.example.consensus.handler.ConsensusLambdaHandler::handleRequest"]