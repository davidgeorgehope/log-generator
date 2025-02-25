#!/bin/bash

# Check if the log-generator is already running by checking for the process ID file
if [ -f log-generator.pid ]; then
    LOG_GENERATOR_PID=$(cat log-generator.pid)
    if ps -p $LOG_GENERATOR_PID > /dev/null; then
        echo "Log generator is already running with PID $LOG_GENERATOR_PID"
    else
        echo "Log generator PID file exists but process is not running. Starting log generator..."
        ./start-log-generator.sh
    fi
else
    echo "Log generator is not running. Starting it first..."
    ./start-log-generator.sh
fi

# Define ports
NGINX_INGRESS_PORT=9007
NGINX_INGRESS_ERROR_PORT=9008
LOG_GENERATOR_SERVICE="localhost"

# Start the client to read from the log generator
echo "Starting NGINX Ingress log client with the following configuration:"
echo "NGINX Ingress Access Port: $NGINX_INGRESS_PORT"
echo "NGINX Ingress Error Port: $NGINX_INGRESS_ERROR_PORT"
echo "Log Generator Service: $LOG_GENERATOR_SERVICE"

# Start the client in the background
java -cp target/log-generator-0.0.1-SNAPSHOT.jar org.davidgeorgehope.client.NginxIngressLogClient $NGINX_INGRESS_PORT $NGINX_INGRESS_ERROR_PORT $LOG_GENERATOR_SERVICE > nginx-ingress-client-output.log 2>&1 &

# Get the process ID and save it
NGINX_INGRESS_CLIENT_PID=$!
echo $NGINX_INGRESS_CLIENT_PID > nginx-ingress-client.pid

echo "NGINX Ingress log client started with PID $NGINX_INGRESS_CLIENT_PID" 