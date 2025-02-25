#!/bin/bash

# Check if the PID file exists
if [ -f nginx-frontend-client.pid ]; then
    # Read the PID from the file
    NGINX_FRONTEND_CLIENT_PID=$(cat nginx-frontend-client.pid)
    # Kill the process
    kill $NGINX_FRONTEND_CLIENT_PID
    # Remove the PID file
    rm nginx-frontend-client.pid
    echo "Nginx Frontend log client with PID $NGINX_FRONTEND_CLIENT_PID has been stopped."
else
    echo "PID file not found. Is the Nginx Frontend log client running?"
fi 