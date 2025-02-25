#!/bin/bash

# Set default ports for Nginx Frontend log streaming
NGINX_FRONTEND_ERROR_PORT=9005
NGINX_FRONTEND_STDOUT_PORT=9006
HOST="localhost"

# Parse command line arguments
for arg in "$@"
do
    case $arg in
        --nginx-frontend-error-port=*)
        NGINX_FRONTEND_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --nginx-frontend-stdout-port=*)
        NGINX_FRONTEND_STDOUT_PORT="${arg#*=}"
        shift
        ;;
        --host=*)
        HOST="${arg#*=}"
        shift
        ;;
    esac
done

# Print the ports being used
echo "Starting Nginx Frontend log client connecting to:"
echo "Host: $HOST"
echo "Nginx Frontend Error Port: $NGINX_FRONTEND_ERROR_PORT"
echo "Nginx Frontend Stdout Port: $NGINX_FRONTEND_STDOUT_PORT"

# Start the Nginx Frontend client in the background and save the PID to a file
java -cp target/log-generator-0.0.1-SNAPSHOT.jar org.davidgeorgehope.client.NginxFrontendLogClient $NGINX_FRONTEND_ERROR_PORT $NGINX_FRONTEND_STDOUT_PORT $HOST > nginx-frontend-client-output.log 2>&1 &

# Get the process ID of the last background process
NGINX_FRONTEND_CLIENT_PID=$!
echo $NGINX_FRONTEND_CLIENT_PID > nginx-frontend-client.pid

echo "Nginx Frontend log client started with PID $NGINX_FRONTEND_CLIENT_PID" 