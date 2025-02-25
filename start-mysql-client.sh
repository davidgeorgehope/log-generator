#!/bin/bash

# Set default ports for MySQL log streaming
MYSQL_ERROR_PORT=9001
MYSQL_STDOUT_PORT=9002
HOST="localhost"

# Parse command line arguments
for arg in "$@"
do
    case $arg in
        --mysql-error-port=*)
        MYSQL_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --mysql-stdout-port=*)
        MYSQL_STDOUT_PORT="${arg#*=}"
        shift
        ;;
        --host=*)
        HOST="${arg#*=}"
        shift
        ;;
    esac
done

# Print the ports being used
echo "Starting MySQL log client connecting to:"
echo "Host: $HOST"
echo "MySQL Error Port: $MYSQL_ERROR_PORT"
echo "MySQL Stdout Port: $MYSQL_STDOUT_PORT"

# Start the MySQL client in the background and save the PID to a file
java -cp target/log-generator-0.0.1-SNAPSHOT.jar org.davidgeorgehope.client.MySQLLogClient $MYSQL_ERROR_PORT $MYSQL_STDOUT_PORT $HOST > mysql-client-output.log 2>&1 &

# Get the process ID of the last background process
MYSQL_CLIENT_PID=$!
echo $MYSQL_CLIENT_PID > mysql-client.pid

echo "MySQL log client started with PID $MYSQL_CLIENT_PID" 