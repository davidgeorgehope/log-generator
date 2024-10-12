#!/bin/bash

# Set environment variables to induce anomalies
export INDUCE_HIGH_VISITOR_RATE=false
export INDUCE_HIGH_ERROR_RATE=false
export INDUCE_HIGH_REQUEST_RATE_FROM_SINGLE_IP=false
export INDUCE_HIGH_DISTINCT_URLS_FROM_SINGLE_IP=false

# Start the log generator in the background and save the PID to a file
java -jar target/log-generator-0.0.1-SNAPSHOT.jar > log-generator-output.log 2>&1 &

# Get the process ID of the last background process
LOG_GENERATOR_PID=$!
echo $LOG_GENERATOR_PID > log-generator.pid

echo "Log generator started with PID $LOG_GENERATOR_PID"
