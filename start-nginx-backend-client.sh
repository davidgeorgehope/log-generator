#!/bin/bash

# Set default ports for Nginx Backend log streaming
NGINX_BACKEND_ERROR_PORT=9003
NGINX_BACKEND_STDOUT_PORT=9004
HOST="localhost"

# Parse command line arguments
for arg in "$@"
do
    case $arg in
        --nginx-backend-error-port=*)
        NGINX_BACKEND_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --nginx-backend-stdout-port=*)
        NGINX_BACKEND_STDOUT_PORT="${arg#*=}"
        shift
        ;;
        --host=*)
        HOST="${arg#*=}"
        shift
        ;;
    esac
done

# Print the ports being used
echo "Starting Nginx Backend log client connecting to:"
echo "Host: $HOST"
echo "Nginx Backend Error Port: $NGINX_BACKEND_ERROR_PORT"
echo "Nginx Backend Stdout Port: $NGINX_BACKEND_STDOUT_PORT"

# Start the Nginx Backend client in the background and save the PID to a file
java -cp target/log-generator-0.0.1-SNAPSHOT.jar org.davidgeorgehope.client.NginxBackendLogClient $NGINX_BACKEND_ERROR_PORT $NGINX_BACKEND_STDOUT_PORT $HOST > nginx-backend-client-output.log 2>&1 &

# Get the process ID of the last background process
NGINX_BACKEND_CLIENT_PID=$!
echo $NGINX_BACKEND_CLIENT_PID > nginx-backend-client.pid

echo "Nginx Backend log client started with PID $NGINX_BACKEND_CLIENT_PID" 