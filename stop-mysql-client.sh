#!/bin/bash

# Check if the PID file exists
if [ -f mysql-client.pid ]; then
    # Read the PID from the file
    MYSQL_CLIENT_PID=$(cat mysql-client.pid)
    # Kill the process
    kill $MYSQL_CLIENT_PID
    # Remove the PID file
    rm mysql-client.pid
    echo "MySQL log client with PID $MYSQL_CLIENT_PID has been stopped."
else
    echo "PID file not found. Is the MySQL log client running?"
fi 