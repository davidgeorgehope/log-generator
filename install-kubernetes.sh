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
    
    local response
    response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" "$KIBANA_URL/api/status")
    debug_log "Kibana response: $response"
    
    if echo "$response" | jq -e '.status.overall.level == "available"' > /dev/null 2>&1; then
      echo -e "${GREEN}Kibana is available!${NC}"
      
      # Wait a bit more to ensure all Fleet APIs are ready
      echo -e "${BLUE}Waiting for Fleet API to be fully ready...${NC}"
      sleep 10
      
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
  local max_retries=3
  local retry_delay=5
  local attempt=1
  
  debug_log "Getting agent policy ID for '$policy_name'"
  
  while [[ $attempt -le $max_retries ]]; do
    debug_log "Attempt $attempt of $max_retries to get policy ID"
    
    response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
      -H "Content-Type: application/json" \
      -H "kbn-xsrf: true" \
      "${KIBANA_URL}/api/fleet/agent_policies?kuery=name:\"${policy_name}\"")
    
    debug_log "Agent policy response: $response"
    
    if echo "$response" | jq -e '.items | length > 0' > /dev/null; then
      local policy_id
      policy_id=$(echo "$response" | jq -r '.items[] | select(.name == "'"$policy_name"'") | .id')
      
      if [[ -n "$policy_id" && "$policy_id" != "null" ]]; then
        echo "$policy_id"
        return 0
      fi
    fi
    
    if [[ $attempt -lt $max_retries ]]; then
      echo -e "${YELLOW}Policy '$policy_name' not found on attempt $attempt. Retrying in ${retry_delay} seconds...${NC}"
      sleep $retry_delay
      # Increase the delay for subsequent retries
      retry_delay=$((retry_delay * 2))
    fi
    
    ((attempt++))
  done
  
  return 1
}

# Function to create agent policy
create_agent_policy() {
  local policy_file="$1"
  local policy_name
  local policy_id
  
  # Check if policy file exists
  if ! check_file_exists "$policy_file" "Agent policy"; then
    echo -e "${RED}Skipping agent policy creation due to missing file.${NC}"
    return 1
  fi
  
  # Read policy name from file
  policy_name=$(cat "$policy_file" | jq -r '.name')
  if [[ -z "$policy_name" || "$policy_name" == "null" ]]; then
    echo -e "${RED}Error: Unable to extract policy name from $policy_file${NC}"
    debug_log "File contents: $(cat "$policy_file")"
    return 1
  fi
  
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
  
  debug_log "Create policy response: $response"
  
  if echo "$response" | jq -e '.item.id' > /dev/null; then
    policy_id=$(echo "$response" | jq -r '.item.id')
    echo -e "${GREEN}Created agent policy '$policy_name' with ID: $policy_id${NC}"
    return 0
  else
    echo -e "${RED}Failed to create agent policy: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
    return 1
  fi
}

# Function to install integration
install_integration() {
  local integration_file="$1"
  local integration_name
  local agent_policy_name
  local agent_policy_id
  
  # Check if integration file exists
  if ! check_file_exists "$integration_file" "Integration"; then
    echo -e "${RED}Skipping integration installation due to missing file.${NC}"
    return 1
  fi
  
  # Read policy name from integration file
  agent_policy_name=$(cat "$integration_file" | jq -r '.agent_policy_name')
  integration_name=$(cat "$integration_file" | jq -r '.package_policy.name')
  
  if [[ -z "$agent_policy_name" || "$agent_policy_name" == "null" || -z "$integration_name" || "$integration_name" == "null" ]]; then
    echo -e "${RED}Error: Unable to extract policy name or integration name from $integration_file${NC}"
    debug_log "File contents: $(cat "$integration_file")"
    return 1
  fi
  
  echo -e "${BLUE}Installing integration '$integration_name' for policy '$agent_policy_name'...${NC}"
  
  # Get policy ID
  echo -e "${BLUE}Looking up policy ID for '$agent_policy_name'...${NC}"
  agent_policy_id=$(get_agent_policy_id "$agent_policy_name")
  if [[ -z "$agent_policy_id" ]]; then
    echo -e "${RED}Agent policy '$agent_policy_name' not found.${NC}"
    # List available policies for debugging
    echo -e "${YELLOW}Available policies:${NC}"
    debug_policies=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
      -H "Content-Type: application/json" \
      -H "kbn-xsrf: true" \
      "${KIBANA_URL}/api/fleet/agent_policies")
    echo "$debug_policies" | jq -r '.items[] | .name + " (ID: " + .id + ")"' || echo "Failed to list policies"
    return 1
  fi
  
  echo -e "${GREEN}Found policy ID: $agent_policy_id for '$agent_policy_name'${NC}"
  
  # Create temporary file with policy ID inserted
  local temp_file
  temp_file=$(mktemp)
  cat "$integration_file" | jq '.package_policy.policy_id = "'"$agent_policy_id"'"' > "$temp_file"
  
  debug_log "Prepared integration payload: $(cat "$temp_file")"
  
  # Install integration
  local response
  response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
    -H "Content-Type: application/json" \
    -H "kbn-xsrf: true" \
    -X POST \
    -d @"$temp_file" \
    "${KIBANA_URL}/api/fleet/package_policies")
  
  rm "$temp_file"
  
  debug_log "Integration installation response: $response"
  
  if echo "$response" | jq -e '.item.id' > /dev/null; then
    echo -e "${GREEN}Installed integration '$integration_name' successfully.${NC}"
    return 0
  else
    # Check if it's already installed - this is not an error
    if echo "$response" | grep -q "already exists"; then
      echo -e "${YELLOW}Integration '$integration_name' already exists.${NC}"
      return 0
    fi
    echo -e "${RED}Failed to install integration: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
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
  
  debug_log "Enrollment API keys response: $response"
  
  # Extract token for this policy
  local token
  token=$(echo "$response" | jq -r ".items[] | select(.policy_id == \"$policy_id\") | .api_key")
  
  if [[ -n "$token" && "$token" != "null" ]]; then
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
  
  # Print current directory and check for policy files
  debug_log "Current directory: $(pwd)"
  debug_log "Policy directory listing: $(ls -la elastic/agent_policies/ 2>/dev/null || echo 'Directory not found')"
  debug_log "Integrations directory listing: $(ls -la elastic/integrations/ 2>/dev/null || echo 'Directory not found')"
  
  # Use default enrollment tokens if policies don't exist
  mysql_token=""
  nginx_frontend_token=""
  nginx_backend_token=""
  
  # Store policy IDs for direct use
  mysql_policy_id=""
  nginx_frontend_policy_id=""
  nginx_backend_policy_id=""
  
  # Try to create agent policies for each client type
  if [[ -d "elastic/agent_policies" ]]; then
    # Create MySQL policy
    if check_file_exists "elastic/agent_policies/mysql-agent-policy.json" "Agent policy"; then
      mysql_policy_name=$(cat "elastic/agent_policies/mysql-agent-policy.json" | jq -r '.name')
      existing_id=$(get_agent_policy_id "$mysql_policy_name")
      
      if [[ -n "$existing_id" ]]; then
        echo -e "${GREEN}Policy '$mysql_policy_name' already exists with ID: $existing_id${NC}"
        mysql_policy_id="$existing_id"
      else
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"elastic/agent_policies/mysql-agent-policy.json" \
          "${KIBANA_URL}/api/fleet/agent_policies?sys_monitoring=true")
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          mysql_policy_id=$(echo "$response" | jq -r '.item.id')
          echo -e "${GREEN}Created agent policy '$mysql_policy_name' with ID: $mysql_policy_id${NC}"
        else
          echo -e "${RED}Failed to create MySQL agent policy: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
    fi
    
    # Create Nginx Frontend policy
    if check_file_exists "elastic/agent_policies/nginx-frontend-agent-policy.json" "Agent policy"; then
      nginx_frontend_policy_name=$(cat "elastic/agent_policies/nginx-frontend-agent-policy.json" | jq -r '.name')
      existing_id=$(get_agent_policy_id "$nginx_frontend_policy_name")
      
      if [[ -n "$existing_id" ]]; then
        echo -e "${GREEN}Policy '$nginx_frontend_policy_name' already exists with ID: $existing_id${NC}"
        nginx_frontend_policy_id="$existing_id"
      else
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"elastic/agent_policies/nginx-frontend-agent-policy.json" \
          "${KIBANA_URL}/api/fleet/agent_policies?sys_monitoring=true")
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          nginx_frontend_policy_id=$(echo "$response" | jq -r '.item.id')
          echo -e "${GREEN}Created agent policy '$nginx_frontend_policy_name' with ID: $nginx_frontend_policy_id${NC}"
        else
          echo -e "${RED}Failed to create Nginx Frontend agent policy: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
    fi
    
    # Create Nginx Backend policy
    if check_file_exists "elastic/agent_policies/nginx-backend-agent-policy.json" "Agent policy"; then
      nginx_backend_policy_name=$(cat "elastic/agent_policies/nginx-backend-agent-policy.json" | jq -r '.name')
      existing_id=$(get_agent_policy_id "$nginx_backend_policy_name")
      
      if [[ -n "$existing_id" ]]; then
        echo -e "${GREEN}Policy '$nginx_backend_policy_name' already exists with ID: $existing_id${NC}"
        nginx_backend_policy_id="$existing_id"
      else
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"elastic/agent_policies/nginx-backend-agent-policy.json" \
          "${KIBANA_URL}/api/fleet/agent_policies?sys_monitoring=true")
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          nginx_backend_policy_id=$(echo "$response" | jq -r '.item.id')
          echo -e "${GREEN}Created agent policy '$nginx_backend_policy_name' with ID: $nginx_backend_policy_id${NC}"
        else
          echo -e "${RED}Failed to create Nginx Backend agent policy: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
    fi
    
    # Install integrations if we have policy IDs
    if [[ -d "elastic/integrations" ]]; then
      # Install MySQL integration
      if [[ -n "$mysql_policy_id" ]] && check_file_exists "elastic/integrations/mysql.json" "Integration"; then
        mysql_integration_name=$(cat "elastic/integrations/mysql.json" | jq -r '.package_policy.name')
        echo -e "${BLUE}Installing integration '$mysql_integration_name' for MySQL policy...${NC}"
        
        temp_file=$(mktemp)
        cat "elastic/integrations/mysql.json" | jq '.package_policy.policy_id = "'"$mysql_policy_id"'"' > "$temp_file"
        
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"$temp_file" \
          "${KIBANA_URL}/api/fleet/package_policies")
        
        rm "$temp_file"
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          echo -e "${GREEN}Installed MySQL integration successfully.${NC}"
        elif echo "$response" | grep -q "already exists"; then
          echo -e "${YELLOW}MySQL integration already exists.${NC}"
        else
          echo -e "${RED}Failed to install MySQL integration: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
      
      # Install Nginx Frontend integration
      if [[ -n "$nginx_frontend_policy_id" ]] && check_file_exists "elastic/integrations/nginx-frontend.json" "Integration"; then
        nginx_frontend_integration_name=$(cat "elastic/integrations/nginx-frontend.json" | jq -r '.package_policy.name')
        echo -e "${BLUE}Installing integration '$nginx_frontend_integration_name' for Nginx Frontend policy...${NC}"
        
        temp_file=$(mktemp)
        cat "elastic/integrations/nginx-frontend.json" | jq '.package_policy.policy_id = "'"$nginx_frontend_policy_id"'"' > "$temp_file"
        
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"$temp_file" \
          "${KIBANA_URL}/api/fleet/package_policies")
        
        rm "$temp_file"
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          echo -e "${GREEN}Installed Nginx Frontend integration successfully.${NC}"
        elif echo "$response" | grep -q "already exists"; then
          echo -e "${YELLOW}Nginx Frontend integration already exists.${NC}"
        else
          echo -e "${RED}Failed to install Nginx Frontend integration: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
      
      # Install Nginx Backend integration
      if [[ -n "$nginx_backend_policy_id" ]] && check_file_exists "elastic/integrations/nginx-backend.json" "Integration"; then
        nginx_backend_integration_name=$(cat "elastic/integrations/nginx-backend.json" | jq -r '.package_policy.name')
        echo -e "${BLUE}Installing integration '$nginx_backend_integration_name' for Nginx Backend policy...${NC}"
        
        temp_file=$(mktemp)
        cat "elastic/integrations/nginx-backend.json" | jq '.package_policy.policy_id = "'"$nginx_backend_policy_id"'"' > "$temp_file"
        
        response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
          -H "Content-Type: application/json" \
          -H "kbn-xsrf: true" \
          -X POST \
          -d @"$temp_file" \
          "${KIBANA_URL}/api/fleet/package_policies")
        
        rm "$temp_file"
        
        if echo "$response" | jq -e '.item.id' > /dev/null; then
          echo -e "${GREEN}Installed Nginx Backend integration successfully.${NC}"
        elif echo "$response" | grep -q "already exists"; then
          echo -e "${YELLOW}Nginx Backend integration already exists.${NC}"
        else
          echo -e "${RED}Failed to install Nginx Backend integration: $(echo "$response" | jq -r '.message // "Unknown error"')${NC}"
        fi
      fi
    else
      echo -e "${YELLOW}Warning: Integrations directory does not exist!${NC}"
    fi
    
    # Generate enrollment tokens using stored policy IDs
    if [[ -n "$mysql_policy_id" ]]; then
      echo -e "${BLUE}Generating enrollment token for MySQL policy...${NC}"
      response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
        -H "Content-Type: application/json" \
        -H "kbn-xsrf: true" \
        "${KIBANA_URL}/api/fleet/enrollment-api-keys")
      
      mysql_token=$(echo "$response" | jq -r ".items[] | select(.policy_id == \"$mysql_policy_id\") | .api_key")
      if [[ -z "$mysql_token" || "$mysql_token" == "null" ]]; then
        echo -e "${RED}No enrollment token found for MySQL policy ID $mysql_policy_id${NC}"
      else
        echo -e "${GREEN}Retrieved MySQL enrollment token successfully${NC}"
      fi
    fi
    
    if [[ -n "$nginx_frontend_policy_id" ]]; then
      echo -e "${BLUE}Generating enrollment token for Nginx Frontend policy...${NC}"
      response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
        -H "Content-Type: application/json" \
        -H "kbn-xsrf: true" \
        "${KIBANA_URL}/api/fleet/enrollment-api-keys")
      
      nginx_frontend_token=$(echo "$response" | jq -r ".items[] | select(.policy_id == \"$nginx_frontend_policy_id\") | .api_key")
      if [[ -z "$nginx_frontend_token" || "$nginx_frontend_token" == "null" ]]; then
        echo -e "${RED}No enrollment token found for Nginx Frontend policy ID $nginx_frontend_policy_id${NC}"
      else
        echo -e "${GREEN}Retrieved Nginx Frontend enrollment token successfully${NC}"
      fi
    fi
    
    if [[ -n "$nginx_backend_policy_id" ]]; then
      echo -e "${BLUE}Generating enrollment token for Nginx Backend policy...${NC}"
      response=$(curl -s -k -u "$ELASTICSEARCH_USER:$ELASTICSEARCH_PASSWORD" \
        -H "Content-Type: application/json" \
        -H "kbn-xsrf: true" \
        "${KIBANA_URL}/api/fleet/enrollment-api-keys")
      
      nginx_backend_token=$(echo "$response" | jq -r ".items[] | select(.policy_id == \"$nginx_backend_policy_id\") | .api_key")
      if [[ -z "$nginx_backend_token" || "$nginx_backend_token" == "null" ]]; then
        echo -e "${RED}No enrollment token found for Nginx Backend policy ID $nginx_backend_policy_id${NC}"
      else
        echo -e "${GREEN}Retrieved Nginx Backend enrollment token successfully${NC}"
      fi
    fi
  else
    echo -e "${YELLOW}Warning: Agent policies directory not found! Using default tokens.${NC}"
  fi
  
  # Use example tokens if we couldn't generate real ones
  if [[ -z "$mysql_token" ]]; then
    echo -e "${YELLOW}Using example MySQL enrollment token due to failure in token generation${NC}"
    mysql_token="EXAMPLE_MYSQL_TOKEN_PLACEHOLDER"
  fi
  
  if [[ -z "$nginx_frontend_token" ]]; then
    echo -e "${YELLOW}Using example Nginx Frontend enrollment token due to failure in token generation${NC}"
    nginx_frontend_token="EXAMPLE_NGINX_FRONTEND_TOKEN_PLACEHOLDER"
  fi
  
  if [[ -z "$nginx_backend_token" ]]; then
    echo -e "${YELLOW}Using example Nginx Backend enrollment token due to failure in token generation${NC}"
    nginx_backend_token="EXAMPLE_NGINX_BACKEND_TOKEN_PLACEHOLDER"
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
