#!/bin/bash

# Default values
DOCKER_HUB_USERNAME="djhope99"
IMAGE_NAME="log-generator"
IMAGE_TAG="latest"
PUSH_TO_DOCKER_HUB=true

# Parse command line arguments
for arg in "$@"
do
    case $arg in
        --username=*)
        DOCKER_HUB_USERNAME="${arg#*=}"
        shift
        ;;
        --image-name=*)
        IMAGE_NAME="${arg#*=}"
        shift
        ;;
        --tag=*)
        IMAGE_TAG="${arg#*=}"
        shift
        ;;
        --push)
        PUSH_TO_DOCKER_HUB=true
        shift
        ;;
        --help)
        echo "Usage: $0 [options]"
        echo "Options:"
        echo "  --username=<username>    Docker Hub username (required for push)"
        echo "  --image-name=<name>      Docker image name (default: log-generator)"
        echo "  --tag=<tag>              Docker image tag (default: latest)"
        echo "  --push                   Push the image to Docker Hub"
        echo "  --help                   Show this help message"
        exit 0
        ;;
    esac
done

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Maven is not installed. Please install Maven first."
    exit 1
fi

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "Docker is not installed. Please install Docker first."
    exit 1
fi

# Build the Java application
echo "Building Java application with Maven..."
mvn clean package

# Check if Maven build was successful
if [ $? -ne 0 ]; then
    echo "Maven build failed. Please fix the issues and try again."
    exit 1
fi

# Build the Docker image
echo "Building Docker image: $IMAGE_NAME:$IMAGE_TAG"
docker build -t $IMAGE_NAME:$IMAGE_TAG .

# Check if Docker build was successful
if [ $? -ne 0 ]; then
    echo "Docker build failed. Please fix the issues and try again."
    exit 1
fi

echo "Docker image built successfully: $IMAGE_NAME:$IMAGE_TAG"

# Push to Docker Hub if requested
if [ "$PUSH_TO_DOCKER_HUB" = true ]; then
    # Check if username is provided
    if [ -z "$DOCKER_HUB_USERNAME" ]; then
        echo "Docker Hub username is required for pushing. Use --username=<username>"
        exit 1
    fi
    
    # Tag the image for Docker Hub
    DOCKER_HUB_IMAGE="$DOCKER_HUB_USERNAME/$IMAGE_NAME:$IMAGE_TAG"
    echo "Tagging image for Docker Hub: $DOCKER_HUB_IMAGE"
    docker tag $IMAGE_NAME:$IMAGE_TAG $DOCKER_HUB_IMAGE
    
    # Login to Docker Hub
    echo "Logging in to Docker Hub..."
    docker login -u $DOCKER_HUB_USERNAME
    
    # Push the image
    echo "Pushing image to Docker Hub: $DOCKER_HUB_IMAGE"
    docker push $DOCKER_HUB_IMAGE
    
    echo "Image pushed successfully to Docker Hub: $DOCKER_HUB_IMAGE"
else
    echo ""
    echo "To push this image to Docker Hub, run this script with the following options:"
    echo "$0 --username=<your-dockerhub-username> --push"
    echo ""
    echo "Or manually push with these commands:"
    echo "docker tag $IMAGE_NAME:$IMAGE_TAG <your-dockerhub-username>/$IMAGE_NAME:$IMAGE_TAG"
    echo "docker login -u <your-dockerhub-username>"
    echo "docker push <your-dockerhub-username>/$IMAGE_NAME:$IMAGE_TAG"
fi

# Print instructions for Kubernetes
echo ""
echo "To use this image in your Kubernetes manifests:"
if [ "$PUSH_TO_DOCKER_HUB" = true ]; then
    echo "1. Update the image field in your Kubernetes YAML files to: $DOCKER_HUB_IMAGE"
else
    echo "1. Update the image field in your Kubernetes YAML files to: <your-dockerhub-username>/$IMAGE_NAME:$IMAGE_TAG"
fi
echo "2. Apply the Kubernetes manifests with: kubectl apply -f kubernetes/"
echo "" 