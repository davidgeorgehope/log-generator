#!/bin/bash

# Check if the PID file exists
if [ -f nginx-ingress-client.pid ]; then
    # Read the PID from the file
    NGINX_INGRESS_CLIENT_PID=$(cat nginx-ingress-client.pid)
    
    # Check if the process is still running
    if ps -p $NGINX_INGRESS_CLIENT_PID > /dev/null; then
        echo "Stopping NGINX Ingress log client with PID $NGINX_INGRESS_CLIENT_PID"
        kill $NGINX_INGRESS_CLIENT_PID
    else
        echo "NGINX Ingress log client is not running but PID file exists"
    fi
    
    # Remove the PID file
    rm nginx-ingress-client.pid
else
    echo "NGINX Ingress log client is not running (no PID file found)"
fi 