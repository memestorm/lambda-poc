FROM openjdk:21-jre-slim

# Create app directory
WORKDIR /app

# Copy the built JAR file
COPY target/lambda-consensus-federation-1.0.0.jar app.jar

# Create logs directory
RUN mkdir -p logs

# Set the main class for the trigger service
ENV JAVA_OPTS="-Xmx256m -Xms128m"

# Run the trigger service
CMD ["java", "-cp", "app.jar", "com.example.consensus.trigger.TriggerServiceMain"]