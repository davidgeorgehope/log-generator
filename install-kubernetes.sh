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

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
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

# Function to wait for Kibana to be available
wait_for_kibana() {
  echo -e "\n${BLUE}Checking Kibana availability...${NC}"
  
  local max_retries=5
  local retry_delay=10
  local attempt=1
  
  while [[ $attempt -le $max_retries ]]; do
    echo -e "${BLUE}Attempt $attempt of $max_retries...${NC}"
    
    if curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" "$KIBANA_URL/api/status" | grep -q "available"; then
      echo -e "${GREEN}Kibana is available!${NC}"
      return 0
    fi
    
    echo -e "${YELLOW}Kibana not available yet. Waiting ${retry_delay} seconds...${NC}"
    sleep $retry_delay
    ((attempt++))
  done
  
  echo -e "${RED}Error: Kibana did not become available after $max_retries attempts.${NC}"
  return 1
}

# Function to get agent policy ID by name
get_agent_policy_id() {
  local policy_name="$1"
  local response
  
  response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    "${KIBANA_URL}/api/fleet/agent_policies?kuery=name:\"${policy_name}\"")
  
  if echo "$response" | jq -e '.items | length > 0' > /dev/null; then
    echo "$response" | jq -r '.items[] | select(.name == "'"$policy_name"'") | .id'
    return 0
  fi
  
  return 1
}

# Function to create agent policy
create_agent_policy() {
  local policy_file="$1"
  local policy_name
  local policy_id
  
  # Read policy name from file
  policy_name=$(cat "$policy_file" | jq -r '.name')
  
  echo -e "${BLUE}Checking if agent policy '$policy_name' exists...${NC}"
  
  # Check if policy exists
  policy_id=$(get_agent_policy_id "$policy_name")
  if [[ -n "$policy_id" ]]; then
    echo -e "${GREEN}Policy '$policy_name' already exists with ID: $policy_id${NC}"
    return 0
  fi
  
  echo -e "${BLUE}Creating agent policy '$policy_name'...${NC}"
  
  # Create policy
  local response
  response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    -X POST \
    -d @"$policy_file" \
    "${KIBANA_URL}/api/fleet/agent_policies?sys_monitoring=true")
  
  if echo "$response" | jq -e '.item.id' > /dev/null; then
    policy_id=$(echo "$response" | jq -r '.item.id')
    echo -e "${GREEN}Created agent policy '$policy_name' with ID: $policy_id${NC}"
    return 0
  else
    echo -e "${RED}Failed to create agent policy: $(echo "$response" | jq -r '.message')${NC}"
    return 1
  fi
}

# Function to install integration
install_integration() {
  local integration_file="$1"
  local integration_name
  local agent_policy_name
  local agent_policy_id
  
  # Read policy name from integration file
  agent_policy_name=$(cat "$integration_file" | jq -r '.agent_policy_name')
  integration_name=$(cat "$integration_file" | jq -r '.package_policy.name')
  
  echo -e "${BLUE}Installing integration '$integration_name' for policy '$agent_policy_name'...${NC}"
  
  # Get policy ID
  agent_policy_id=$(get_agent_policy_id "$agent_policy_name")
  if [[ -z "$agent_policy_id" ]]; then
    echo -e "${RED}Agent policy '$agent_policy_name' not found.${NC}"
    return 1
  fi
  
  # Create temporary file with policy ID inserted
  local temp_file
  temp_file=$(mktemp)
  cat "$integration_file" | jq '.package_policy.policy_id = "'"$agent_policy_id"'"' > "$temp_file"
  
  # Install integration
  local response
  response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    -X POST \
    -d @"$temp_file" \
    "${KIBANA_URL}/api/fleet/package_policies")
  
  rm "$temp_file"
  
  if echo "$response" | jq -e '.item.id' > /dev/null; then
    echo -e "${GREEN}Installed integration '$integration_name' successfully.${NC}"
    return 0
  else
    # Check if it's already installed - this is not an error
    if echo "$response" | grep -q "already exists"; then
      echo -e "${YELLOW}Integration '$integration_name' already exists.${NC}"
      return 0
    fi
    echo -e "${RED}Failed to install integration: $(echo "$response" | jq -r '.message')${NC}"
    return 1
  fi
}

# Function to generate enrollment token
generate_enrollment_token() {
  local policy_name="$1"
  local policy_id
  
  echo -e "${BLUE}Generating enrollment token for policy '$policy_name'...${NC}"
  
  # Get policy ID
  policy_id=$(get_agent_policy_id "$policy_name")
  if [[ -z "$policy_id" ]]; then
    echo -e "${RED}Agent policy '$policy_name' not found.${NC}"
    return 1
  fi
  
  # Get enrollment keys
  local response
  response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    "${KIBANA_URL}/api/fleet/enrollment-api-keys")
  
  # Extract token for this policy
  local token
  token=$(echo "$response" | jq -r ".items[] | select(.policy_id == \"$policy_id\") | .api_key")
  
  if [[ -n "$token" ]]; then
    echo "$token"
    return 0
  else
    echo -e "${RED}No enrollment token found for policy ID $policy_id${NC}"
    return 1
  fi
}

# Generate enrollment tokens if not skipped
if [[ "$SKIP_TOKEN_GENERATION" == "false" ]]; then
  echo -e "\n${BLUE}Setting up Elastic Agent policies and generating enrollment tokens...${NC}"
  
  # Wait for Kibana to be available
  wait_for_kibana
  
  # Create agent policies for each client type
  create_agent_policy "elastic/agent_policies/mysql-agent-policy.json"
  create_agent_policy "elastic/agent_policies/nginx-frontend-agent-policy.json"
  create_agent_policy "elastic/agent_policies/nginx-backend-agent-policy.json"
  
  # Install integrations for each client type
  install_integration "elastic/integrations/mysql.json"
  install_integration "elastic/integrations/nginx-frontend.json"
  install_integration "elastic/integrations/nginx-backend.json"
  
  # Generate enrollment tokens
  mysql_token=$(generate_enrollment_token "MySQL Monitoring Policy")
  nginx_frontend_token=$(generate_enrollment_token "Nginx Frontend Monitoring Policy")
  nginx_backend_token=$(generate_enrollment_token "Nginx Backend Monitoring Policy")
  
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

if [ ! -f "$DEPLOYMENT_FILE" ]; then
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
