#!/bin/bash

# Set default ports for log streaming
MYSQL_ERROR_PORT=9001
MYSQL_STDOUT_PORT=9002
NGINX_BACKEND_ERROR_PORT=9003
NGINX_BACKEND_STDOUT_PORT=9004
NGINX_FRONTEND_ERROR_PORT=9005
NGINX_FRONTEND_STDOUT_PORT=9006
NGINX_INGRESS_PORT=9007
NGINX_INGRESS_ERROR_PORT=9008

# Default log format
LOG_FORMAT="standard"

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
        --nginx-backend-error-port=*)
        NGINX_BACKEND_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --nginx-backend-stdout-port=*)
        NGINX_BACKEND_STDOUT_PORT="${arg#*=}"
        shift
        ;;
        --nginx-frontend-error-port=*)
        NGINX_FRONTEND_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --nginx-frontend-stdout-port=*)
        NGINX_FRONTEND_STDOUT_PORT="${arg#*=}"
        shift
        ;;
        --nginx-ingress-port=*)
        NGINX_INGRESS_PORT="${arg#*=}"
        shift
        ;;
        --nginx-ingress-error-port=*)
        NGINX_INGRESS_ERROR_PORT="${arg#*=}"
        shift
        ;;
        --log-format=*)
        LOG_FORMAT="${arg#*=}"
        shift
        ;;
        --no-anomalies)
        NO_ANOMALIES="--no-anomalies"
        shift
        ;;
        --mean-requests-per-second=*)
        MEAN_REQUESTS_PER_SECOND="${arg#*=}"
        shift
        ;;
    esac
done

# Set environment variables to induce anomalies
export INDUCE_HIGH_VISITOR_RATE=false
export INDUCE_HIGH_ERROR_RATE=false
export INDUCE_HIGH_REQUEST_RATE_FROM_SINGLE_IP=false
export INDUCE_HIGH_DISTINCT_URLS_FROM_SINGLE_IP=false

# Build the command with all options
CMD="java -jar target/log-generator-0.0.1-SNAPSHOT.jar"

# Add port options
CMD="$CMD --mysql-error-port=$MYSQL_ERROR_PORT"
CMD="$CMD --mysql-stdout-port=$MYSQL_STDOUT_PORT"
CMD="$CMD --nginx-backend-error-port=$NGINX_BACKEND_ERROR_PORT"
CMD="$CMD --nginx-backend-stdout-port=$NGINX_BACKEND_STDOUT_PORT"
CMD="$CMD --nginx-frontend-error-port=$NGINX_FRONTEND_ERROR_PORT"
CMD="$CMD --nginx-frontend-stdout-port=$NGINX_FRONTEND_STDOUT_PORT"
CMD="$CMD --nginx-ingress-port=$NGINX_INGRESS_PORT"
CMD="$CMD --nginx-ingress-error-port=$NGINX_INGRESS_ERROR_PORT"

# Add log format option
CMD="$CMD --log-format=$LOG_FORMAT"

# Add other options if provided
if [ ! -z "$NO_ANOMALIES" ]; then
    CMD="$CMD $NO_ANOMALIES"
fi

if [ ! -z "$MEAN_REQUESTS_PER_SECOND" ]; then
    CMD="$CMD --mean-requests-per-second=$MEAN_REQUESTS_PER_SECOND"
fi

# Print the ports being used
echo "Starting log generator with the following ports:"
echo "MySQL Error Port: $MYSQL_ERROR_PORT"
echo "MySQL Stdout Port: $MYSQL_STDOUT_PORT"
echo "Nginx Backend Error Port: $NGINX_BACKEND_ERROR_PORT"
echo "Nginx Backend Stdout Port: $NGINX_BACKEND_STDOUT_PORT"
echo "Nginx Frontend Error Port: $NGINX_FRONTEND_ERROR_PORT"
echo "Nginx Frontend Stdout Port: $NGINX_FRONTEND_STDOUT_PORT"
echo "Nginx Ingress Port: $NGINX_INGRESS_PORT"
echo "Nginx Ingress Error Port: $NGINX_INGRESS_ERROR_PORT"
echo "Log Format: $LOG_FORMAT"

# Start the log generator in the background and save the PID to a file
$CMD > log-generator-output.log 2>&1 &

# Get the process ID of the last background process
LOG_GENERATOR_PID=$!
echo $LOG_GENERATOR_PID > log-generator.pid

echo "Log generator started with PID $LOG_GENERATOR_PID"
