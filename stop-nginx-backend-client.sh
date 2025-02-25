#!/bin/bash

# Check if the PID file exists
if [ -f nginx-backend-client.pid ]; then
    # Read the PID from the file
    NGINX_BACKEND_CLIENT_PID=$(cat nginx-backend-client.pid)
    # Kill the process
    kill $NGINX_BACKEND_CLIENT_PID
    # Remove the PID file
    rm nginx-backend-client.pid
    echo "Nginx Backend log client with PID $NGINX_BACKEND_CLIENT_PID has been stopped."
else
    echo "PID file not found. Is the Nginx Backend log client running?"
fi 