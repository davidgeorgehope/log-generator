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
NAMESPACE="default"
IMAGE_TAG="latest"

# Function to display script usage
display_usage() {
  echo -e "\n${BLUE}Usage:${NC} $0 [options]"
  echo -e "\n${BLUE}Options:${NC}"
  echo -e "  --es-user <username>         Elasticsearch username (default: elastic)"
  echo -e "  --es-password <password>     Elasticsearch password (default: changeme)"
  echo -e "  --kibana-url <url>           Kibana URL (default: https://kibana.example.com)"
  echo -e "  --es-url <url>               Elasticsearch URL (default: https://elasticsearch.example.com)"
  echo -e "  --namespace <namespace>      Kubernetes namespace (default: default)"
  echo -e "  --image-tag <tag>            Docker image tag (default: latest)"
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
    --namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    --image-tag)
      IMAGE_TAG="$2"
      shift 2
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

# Create secret for Elasticsearch credentials
echo -e "\n${BLUE}Creating Elasticsearch credentials secret...${NC}"
kubectl create secret generic elasticsearch-credentials \
  --namespace "$NAMESPACE" \
  --from-literal=ELASTICSEARCH_USER="$ELASTICSEARCH_USER" \
  --from-literal=ELASTICSEARCH_PASSWORD="$ELASTICSEARCH_PASSWORD" \
  --from-literal=KIBANA_URL="$KIBANA_URL" \
  --from-literal=ELASTICSEARCH_URL="$ELASTICSEARCH_URL" \
  --dry-run=client -o yaml | kubectl apply -f -

echo -e "${GREEN}Secret created successfully.${NC}"

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
  sed "s|davidhope1999/log-generator:latest|davidhope1999/log-generator:$IMAGE_TAG|g" "$DEPLOYMENT_FILE" > "$TMP_FILE"
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

echo -e "\n${BLUE}To check the init container logs:${NC}"
echo -e "kubectl logs -n $NAMESPACE -l app=mysql-log-client -c elastic-agent-installer"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-backend-log-client -c elastic-agent-installer"
echo -e "kubectl logs -n $NAMESPACE -l app=nginx-frontend-log-client -c elastic-agent-installer"

echo -e "\n${BLUE}To access your Elastic stack:${NC}"
echo -e "Kibana URL: $KIBANA_URL"
echo -e "Elasticsearch URL: $ELASTICSEARCH_URL"
echo -e "Username: $ELASTICSEARCH_USER"
echo -e "\n${GREEN}Thank you for using the Log Generator with Elastic Agent Integration!${NC}" 
