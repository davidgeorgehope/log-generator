#!/bin/bash
# Listen on port 9007 for 10 seconds and save the incoming data to a file
timeout 10 nc -l 9007 > captured_ingress_logs.txt
echo "Logs captured to captured_ingress_logs.txt" 