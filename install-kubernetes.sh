#!/bin/bash

# Log Generator with Elastic Agent Integration - Kubernetes Installation Script
# ------------------------------------------------------------------
# This script installs the log generator with Elastic agent integration 
# in a Kubernetes cluster.

set -e

# ANSI color codes for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
ELASTICSEARCH_USER="elastic"
ELASTICSEARCH_PASSWORD="changeme"
KIBANA_URL="https://nginx-test-2ada07.kb.us-central1.gcp.cloud.es.io"
ELASTICSEARCH_URL="https://nginx-test-2ada07.es.us-central1.gcp.cloud.es.io"
FLEET_URL="https://nginx-test-2ada07.fleet.us-central1.gcp.cloud.es.io"
NAMESPACE="default"
IMAGE_TAG="latest"
SKIP_TOKEN_GENERATION=false
DEBUG=false

# Function to display script usage
display_usage() {
  echo -e "\n${BLUE}Usage:${NC} $0 [options]"
  echo -e "\n${BLUE}Options:${NC}"
  echo -e "  --es-user <username>         Elasticsearch username (default: elastic)"
  echo -e "  --es-password <password>     Elasticsearch password (default: changeme)"
  echo -e "  --kibana-url <url>           Kibana URL (default: https://kibana.example.com)"
  echo -e "  --es-url <url>               Elasticsearch URL (default: https://elasticsearch.example.com)"
  echo -e "  --fleet-url <url>            Fleet URL (default: https://fleet.example.com)"
  echo -e "  --namespace <namespace>      Kubernetes namespace (default: default)"
  echo -e "  --image-tag <tag>            Docker image tag (default: latest)"
  echo -e "  --skip-token-generation      Skip Elastic enrollment token generation (default: false)"
  echo -e "  --debug                      Enable debug output (default: false)"
  echo -e "  --help                       Display this help message and exit"
  echo -e "\n${BLUE}Example:${NC}"
  echo -e "  $0 --es-user elastic --es-password mypassword --kibana-url https://kibana.mycompany.com"
  echo -e "\n"
}

# Parse command-line arguments
while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    --es-user)
      ELASTICSEARCH_USER="$2"
      shift 2
      ;;
    --es-password)
      ELASTICSEARCH_PASSWORD="$2"
      shift 2
      ;;
    --kibana-url)
      KIBANA_URL="$2"
      shift 2
      ;;
    --es-url)
      ELASTICSEARCH_URL="$2"
      shift 2
      ;;
    --fleet-url)
      FLEET_URL="$2"
      shift 2
      ;;
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --image-tag)
      IMAGE_TAG="$2"
      shift 2
      ;;
    --skip-token-generation)
      SKIP_TOKEN_GENERATION=true
      shift
      ;;
    --debug)
      DEBUG=true
      shift
      ;;
    --help)
      display_usage
      exit 0
      ;;
    *)
      echo -e "${RED}Error: Unknown option $1${NC}"
      display_usage
      exit 1
      ;;
  esac
done

# Debug function
debug_log() {
  if [[ "$DEBUG" == "true" ]]; then
    echo -e "${YELLOW}[DEBUG] $1${NC}"
  fi
}

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Check if file exists with better error messaging
check_file_exists() {
  local file_path="$1"
  local file_desc="$2"
  
  if [[ ! -f "$file_path" ]]; then
    echo -e "${RED}Error: $file_desc file not found at $file_path${NC}"
    debug_log "Current directory is $(pwd)"
    debug_log "Directory listing: $(ls -la $(dirname "$file_path"))"
    return 1
  fi
  
  debug_log "File $file_path exists"
  return 0
}

# Check prerequisites
echo -e "\n${BLUE}Checking prerequisites...${NC}"

if ! command_exists kubectl; then
  echo -e "${RED}Error: kubectl is required but not installed. Please install kubectl and try again.${NC}"
  exit 1
fi

if ! command_exists curl; then
  echo -e "${RED}Error: curl is required but not installed. Please install curl and try again.${NC}"
  exit 1
fi

if ! command_exists jq; then
  echo -e "${YELLOW}Warning: jq is not installed. Installing it for JSON processing...${NC}"
  if command_exists apt-get; then
    sudo apt-get update && sudo apt-get install -y jq
  elif command_exists brew; then
    brew install jq
  elif command_exists yum; then
    sudo yum install -y jq
  else
    echo -e "${RED}Error: Could not install jq. Please install it manually and try again.${NC}"
    exit 1
  fi
fi

# Check for Python
if ! command_exists python3; then
  echo -e "${RED}Error: Python 3 is required but not installed. Please install Python 3 and try again.${NC}"
  exit 1
fi

# Verify connection to Kubernetes cluster
echo -e "${BLUE}Verifying Kubernetes cluster connection...${NC}"
if ! kubectl cluster-info &>/dev/null; then
  echo -e "${RED}Error: Unable to connect to Kubernetes cluster. Please check your kubeconfig.${NC}"
  exit 1
fi

# Create namespace if it doesn't exist and it's not the default namespace
if [[ "$NAMESPACE" != "default" ]]; then
  echo -e "\n${BLUE}Creating namespace $NAMESPACE if it doesn't exist...${NC}"
  kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
fi

# Check if secret exists and delete it if found
echo -e "\n${BLUE}Checking for existing Elasticsearch credentials secret...${NC}"
if kubectl get secret elasticsearch-credentials -n "$NAMESPACE" &>/dev/null; then
  echo -e "${YELLOW}Found existing secret 'elasticsearch-credentials'. Deleting it...${NC}"
  kubectl delete secret elasticsearch-credentials -n "$NAMESPACE"
  echo -e "${GREEN}Existing secret deleted successfully.${NC}"
else
  echo -e "${BLUE}No existing secret found. Creating new secret...${NC}"
fi

# Create secret for Elasticsearch credentials
echo -e "\n${BLUE}Creating Elasticsearch credentials secret...${NC}"
kubectl create secret generic elasticsearch-credentials \
  --namespace "$NAMESPACE" \
  --from-literal=ELASTICSEARCH_USER="$ELASTICSEARCH_USER" \
  --from-literal=ELASTICSEARCH_PASSWORD="$ELASTICSEARCH_PASSWORD" \
  --from-literal=KIBANA_URL="$KIBANA_URL" \
  --from-literal=ELASTICSEARCH_URL="$ELASTICSEARCH_URL" \
  --from-literal=FLEET_URL="$FLEET_URL" \
  --dry-run=client -o yaml | kubectl apply -f -

echo -e "${GREEN}Secret created successfully.${NC}"

# Generate enrollment tokens if not skipped
if [[ "$SKIP_TOKEN_GENERATION" == "false" ]]; then
  echo -e "\n${BLUE}Setting up Elastic Agent policies and generating enrollment tokens...${NC}"
  
  # Check if the Python script exists and initialize token variables
  mysql_token="EXAMPLE_MYSQL_TOKEN_PLACEHOLDER"
  nginx_frontend_token="EXAMPLE_NGINX_FRONTEND_TOKEN_PLACEHOLDER"
  nginx_backend_token="EXAMPLE_NGINX_BACKEND_TOKEN_PLACEHOLDER"
  
  SCRIPT_PATH="./install-elastic-agent.py"
  if ! check_file_exists "$SCRIPT_PATH" "Python installer script"; then
    echo -e "${RED}Error: Python installer script not found at $SCRIPT_PATH${NC}"
    echo -e "${YELLOW}Continuing with placeholder tokens...${NC}"
  else
    # Ensure the script is executable
    if [[ ! -x "$SCRIPT_PATH" ]]; then
      echo -e "${YELLOW}Making Python script executable...${NC}"
      chmod +x "$SCRIPT_PATH"
      if [[ $? -ne 0 ]]; then
        echo -e "${RED}Error: Failed to make Python script executable${NC}"
        echo -e "${YELLOW}Continuing with placeholder tokens...${NC}"
      fi
    fi
    
    # Check Python dependencies
    echo -e "${BLUE}Checking Python dependencies...${NC}"
    PYTHON_DEPS_CHECK=$(python3 -c "
import sys
try:
    import requests
    import json
    print('OK')
except ImportError as e:
    print(f'Missing dependency: {str(e)}')
    sys.exit(1)
" 2>&1)

    if [[ "$PYTHON_DEPS_CHECK" != "OK" ]]; then
      echo -e "${RED}Python dependency check failed: ${PYTHON_DEPS_CHECK}${NC}"
      echo -e "${YELLOW}Attempting to install required Python packages...${NC}"
      
      python3 -m pip install requests || {
        echo -e "${RED}Failed to install required Python packages${NC}"
        echo -e "${YELLOW}Continuing with placeholder tokens...${NC}"
      }
    else
      echo -e "${GREEN}Python dependencies check passed${NC}"
      
      BASE_DIR=$(pwd)
      
      # Run the script for MySQL policy
      echo -e "${BLUE}Setting up MySQL policy and generating token...${NC}"
      if [[ "$DEBUG" == "true" ]]; then
        echo -e "${YELLOW}[DEBUG] Running: python3 $SCRIPT_PATH --kibana-url $KIBANA_URL --fleet-url $FLEET_URL --username $ELASTICSEARCH_USER --password *** --client-type mysql --namespace $NAMESPACE --base-dir $BASE_DIR --output-token${NC}"
      fi
      
      mysql_token=$(python3 "$SCRIPT_PATH" \
        --kibana-url "$KIBANA_URL" \
        --fleet-url "$FLEET_URL" \
        --username "$ELASTICSEARCH_USER" \
        --password "$ELASTICSEARCH_PASSWORD" \
        --client-type "mysql" \
        --namespace "$NAMESPACE" \
        --base-dir "$BASE_DIR" \
        --output-token 2>&1)
        
      # Check if the output contains an error message
      if [[ "$mysql_token" == *"error"* || "$mysql_token" == *"Error"* || "$mysql_token" == *"Traceback"* ]]; then
        echo -e "${RED}Error running MySQL enrollment token generation:${NC}"
        echo -e "${RED}$mysql_token${NC}"
      elif [[ -n "$mysql_token" ]]; then
        echo -e "${GREEN}Successfully generated MySQL enrollment token${NC}"
      else
        echo -e "${RED}Failed to generate MySQL enrollment token${NC}"
      fi
      
      # Run the script for Nginx Frontend policy
      echo -e "${BLUE}Setting up Nginx Frontend policy and generating token...${NC}"
      if [[ "$DEBUG" == "true" ]]; then
        echo -e "${YELLOW}[DEBUG] Running: python3 $SCRIPT_PATH --kibana-url $KIBANA_URL --fleet-url $FLEET_URL --username $ELASTICSEARCH_USER --password *** --client-type nginx-frontend --namespace $NAMESPACE --base-dir $BASE_DIR --output-token${NC}"
      fi
      
      nginx_frontend_token=$(python3 "$SCRIPT_PATH" \
        --kibana-url "$KIBANA_URL" \
        --fleet-url "$FLEET_URL" \
        --username "$ELASTICSEARCH_USER" \
        --password "$ELASTICSEARCH_PASSWORD" \
        --client-type "nginx-frontend" \
        --namespace "$NAMESPACE" \
        --base-dir "$BASE_DIR" \
        --output-token 2>&1)
        
      # Check if the output contains an error message
      if [[ "$nginx_frontend_token" == *"error"* || "$nginx_frontend_token" == *"Error"* || "$nginx_frontend_token" == *"Traceback"* ]]; then
        echo -e "${RED}Error running Nginx Frontend enrollment token generation:${NC}"
        echo -e "${RED}$nginx_frontend_token${NC}"
      elif [[ -n "$nginx_frontend_token" ]]; then
        echo -e "${GREEN}Successfully generated Nginx Frontend enrollment token${NC}"
      else
        echo -e "${RED}Failed to generate Nginx Frontend enrollment token${NC}"
      fi
      
      # Run the script for Nginx Backend policy
      echo -e "${BLUE}Setting up Nginx Backend policy and generating token...${NC}"
      if [[ "$DEBUG" == "true" ]]; then
        echo -e "${YELLOW}[DEBUG] Running: python3 $SCRIPT_PATH --kibana-url $KIBANA_URL --fleet-url $FLEET_URL --username $ELASTICSEARCH_USER --password *** --client-type nginx-backend --namespace $NAMESPACE --base-dir $BASE_DIR --output-token${NC}"
      fi
      
      nginx_backend_token=$(python3 "$SCRIPT_PATH" \
        --kibana-url "$KIBANA_URL" \
        --fleet-url "$FLEET_URL" \
        --username "$ELASTICSEARCH_USER" \
        --password "$ELASTICSEARCH_PASSWORD" \
        --client-type "nginx-backend" \
        --namespace "$NAMESPACE" \
        --base-dir "$BASE_DIR" \
        --output-token 2>&1)
        
      # Check if the output contains an error message
      if [[ "$nginx_backend_token" == *"error"* || "$nginx_backend_token" == *"Error"* || "$nginx_backend_token" == *"Traceback"* ]]; then
        echo -e "${RED}Error running Nginx Backend enrollment token generation:${NC}"
        echo -e "${RED}$nginx_backend_token${NC}"
      elif [[ -n "$nginx_backend_token" ]]; then
        echo -e "${GREEN}Successfully generated Nginx Backend enrollment token${NC}"
      else
        echo -e "${RED}Failed to generate Nginx Backend enrollment token${NC}"
      fi
    fi
  fi
  
  # Check if enrollment tokens ConfigMap exists and delete it
  echo -e "\n${BLUE}Checking for existing enrollment tokens ConfigMap...${NC}"
  if kubectl get configmap enrollment-tokens -n "$NAMESPACE" &>/dev/null; then
    echo -e "${YELLOW}Found existing ConfigMap 'enrollment-tokens'. Deleting it...${NC}"
    kubectl delete configmap enrollment-tokens -n "$NAMESPACE"
    echo -e "${GREEN}Existing ConfigMap deleted successfully.${NC}"
  fi
  
  # Create ConfigMap with enrollment tokens
  echo -e "\n${BLUE}Creating enrollment tokens ConfigMap...${NC}"
  kubectl create configmap enrollment-tokens \
    --namespace "$NAMESPACE" \
    --from-literal=mysql-enrollment-token="$mysql_token" \
    --from-literal=nginx-frontend-enrollment-token="$nginx_frontend_token" \
    --from-literal=nginx-backend-enrollment-token="$nginx_backend_token" \
    --dry-run=client -o yaml | kubectl apply -f -
  
  echo -e "${GREEN}Enrollment tokens ConfigMap created successfully.${NC}"
else
  echo -e "${YELLOW}Skipping enrollment token generation as requested.${NC}"
fi

# Check if the deployment file exists
DEPLOYMENT_FILE="kubernetes/elastic-agents.yaml"

if ! check_file_exists "$DEPLOYMENT_FILE" "Deployment"; then
  echo -e "${RED}Error: Deployment file $DEPLOYMENT_FILE not found.${NC}"
  exit 1
fi

# Update the image tag in the deployment file if necessary
if [ "$IMAGE_TAG" != "latest" ]; then
  echo -e "\n${BLUE}Updating image tag to $IMAGE_TAG in deployment file...${NC}"
  TMP_FILE=$(mktemp)
  sed "s|djhope99/log-generator:latest|djhope99/log-generator:$IMAGE_TAG|g" "$DEPLOYMENT_FILE" > "$TMP_FILE"
  DEPLOYMENT_FILE="$TMP_FILE"
fi

# Deploy log clients
echo -e "\n${BLUE}Deploying log clients...${NC}"
kubectl apply -f "$DEPLOYMENT_FILE" --namespace "$NAMESPACE"
echo -e "${GREEN}Log clients deployed successfully.${NC}"

# Clean up temporary file if created
if [ -n "$TMP_FILE" ]; then
  rm -f "$TMP_FILE"
fi

# Verify deployment
echo -e "\n${BLUE}Verifying deployment...${NC}"
kubectl get deployments --namespace "$NAMESPACE" -l "app in (mysql-log-client,nginx-backend-log-client,nginx-frontend-log-client)"

# Wait for pods to be ready
echo -e "\n${BLUE}Waiting for pods to be ready...${NC}"
kubectl wait --for=condition=ready pod \
  --selector="app in (mysql-log-client,nginx-backend-log-client,nginx-frontend-log-client)" \
  --timeout=300s \
  --namespace "$NAMESPACE" || {
    echo -e "${YELLOW}Warning: Not all pods are ready after 5 minutes.${NC}"
    echo -e "${YELLOW}Check the status of your pods with:${NC}"
    echo -e "${YELLOW}kubectl get pods -n $NAMESPACE${NC}"
  }

echo -e "\n${GREEN}Installation complete!${NC}"
echo -e "\n${BLUE}To check the logs of your pods, use these commands:${NC}"
echo -e "kubectl logs -n $NAMESPACE -l app=mysql-log-client -c mysql-log-generator"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-backend-log-client -c nginx-backend-log-generator"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-frontend-log-client -c nginx-frontend-log-generator"

echo -e "\n${BLUE}To check the Elastic Agent logs:${NC}"
echo -e "kubectl logs -n $NAMESPACE -l app=mysql-log-client -c elastic-agent"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-backend-log-client -c elastic-agent"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-frontend-log-client -c elastic-agent"

echo -e "\n${BLUE}To access your Elastic stack:${NC}"
echo -e "Kibana URL: $KIBANA_URL"
echo -e "Elasticsearch URL: $ELASTICSEARCH_URL"
echo -e "Username: $ELASTICSEARCH_USER"
echo -e "\n${GREEN}Thank you for using the Log Generator with Elastic Agent Integration!${NC}" 
