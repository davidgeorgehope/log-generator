#!/bin/bash

# Log Generator with Elastic Agent Integration - Kubernetes Installation Script
# ------------------------------------------------------------------
# This script is a wrapper that calls the Python installer script

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
FORCE_SKIP_TOKEN=false

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
  echo -e "  --force-skip-token           Force skip token generation on any error (default: false)"
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
    --force-skip-token)
      FORCE_SKIP_TOKEN=true
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

# Function to check if a command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Check for Python
echo -e "\n${BLUE}Checking for Python...${NC}"
if ! command_exists python3; then
  echo -e "${RED}Error: Python 3 is required but not installed. Please install Python 3 and try again.${NC}"
  exit 1
fi

# Make sure the Python script is executable
SCRIPT_PATH="./install-elastic-agent.py"
if [[ ! -x "$SCRIPT_PATH" ]]; then
  echo -e "${YELLOW}Making Python script executable...${NC}"
  chmod +x "$SCRIPT_PATH"
fi

# Build Python arguments
PYTHON_ARGS=(
  "--kibana-url" "$KIBANA_URL"
  "--elasticsearch-url" "$ELASTICSEARCH_URL"
  "--fleet-url" "$FLEET_URL"
  "--username" "$ELASTICSEARCH_USER"
  "--password" "$ELASTICSEARCH_PASSWORD"
  "--namespace" "$NAMESPACE"
  "--image-tag" "$IMAGE_TAG"
)

if [[ "$SKIP_TOKEN_GENERATION" == "true" ]]; then
  PYTHON_ARGS+=("--skip-token-generation")
fi

if [[ "$FORCE_SKIP_TOKEN" == "true" ]]; then
  PYTHON_ARGS+=("--force-skip-token")
fi

if [[ "$DEBUG" == "true" ]]; then
  PYTHON_ARGS+=("--debug")
fi

# Execute the Python script with all arguments
echo -e "\n${BLUE}Executing Python installer script...${NC}"
python3 "$SCRIPT_PATH" "${PYTHON_ARGS[@]}"

# Check script exit status
if [[ $? -eq 0 ]]; then
  echo -e "\n${GREEN}Installation completed successfully!${NC}"
else
  echo -e "\n${RED}Installation failed!${NC}"
  exit 1
fi 
