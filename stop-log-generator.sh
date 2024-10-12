#!/bin/bash

# Check if the PID file exists
if [ -f log-generator.pid ]; then
    # Read the PID from the file
    LOG_GENERATOR_PID=$(cat log-generator.pid)
    # Kill the process
    kill $LOG_GENERATOR_PID
    # Remove the PID file
    rm log-generator.pid
    echo "Log generator with PID $LOG_GENERATOR_PID has been stopped."
else
    echo "PID file not found. Is the log generator running?"
fi
