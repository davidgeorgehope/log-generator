# Log Generator with Elastic Agent Integration

This project generates realistic logs for various application types and integrates with the Elastic Stack for monitoring.

## Overview

The Log Generator creates realistic logs for:
- MySQL database
- Nginx frontend web server
- Nginx backend API server

These logs are then monitored by Elastic Agents that are automatically installed and configured to send data to an Elastic Stack deployment.

## Architecture

The system consists of the following components:

1. **Log Generator Application**: A Spring Boot application that generates realistic logs for different services.
2. **Elastic Agent Integration**: Python script that installs and configures Elastic Agents.
3. **Kubernetes Deployment**: Configurations for running the system in a Kubernetes environment.

## Setup

### Prerequisites

- Kubernetes cluster
- Elastic Stack deployment (Elasticsearch, Kibana, Fleet Server)
- Docker
- kubectl

### Configuration Files

The system uses several configuration files:

1. **Agent Policies**: Located in `/elastic/agent_policies/`
   - `mysql-agent-policy.json`
   - `nginx-frontend-agent-policy.json`
   - `nginx-backend-agent-policy.json`

2. **Integration Configurations**: Located in `/elastic/integrations/`
   - `mysql.json`
   - `nginx-frontend.json`
   - `nginx-backend.json`

3. **Kubernetes Configurations**: Located in `/kubernetes/`
   - `elastic-agent-configmap.yaml`: Contains all agent policies and integration configurations
   - `elastic-agents.yaml`: Deployment configuration for log clients
   - `elasticsearch-secret.yaml`: Secret for Elasticsearch credentials

### Deployment Steps

1. Create a Kubernetes secret for Elasticsearch credentials:

```bash
kubectl create secret generic elasticsearch-credentials \
  --from-literal=ELASTICSEARCH_USER=elastic \
  --from-literal=ELASTICSEARCH_PASSWORD=yourpassword \
  --from-literal=KIBANA_URL=https://yourkibanaurl \
  --from-literal=ELASTICSEARCH_URL=https://yourelasticsearchurl
```

2. Apply the ConfigMap:

```bash
kubectl apply -f kubernetes/elastic-agent-configmap.yaml
```

3. Deploy the log clients:

```bash
kubectl apply -f kubernetes/elastic-agents.yaml
```

## How It Works

1. Each deployment includes:
   - An init container that runs the Elastic Agent installer script
   - A main container that runs the log generator application

2. The init container:
   - Waits for Kibana to be available
   - Creates the necessary agent policy in Kibana
   - Installs the integration
   - Installs the Elastic Agent

3. The main container:
   - Runs the Java application with the appropriate profile
   - Generates logs in the shared volume

4. The Elastic Agent monitors the logs and sends data to Elasticsearch

## Log Generator Application

The Spring Boot application generates different types of logs based on the active profile:

- `mysql`: Generates MySQL error and slow query logs
- `nginx-frontend`: Generates Nginx access and error logs for a frontend web server
- `nginx-backend`: Generates Nginx access and error logs for a backend API server

### Environment Variables

The log generator accepts the following environment variables:

- `LOG_DIRECTORY`: Directory where logs will be written
- `LOG_LEVEL`: Log level (info, debug, warn, error)
- `LOG_RATE`: Number of log entries to generate per second

## Customization

### Adding New Log Types

1. Create a new profile in the Spring Boot application
2. Create corresponding agent policy and integration configuration files
3. Update the Kubernetes deployment to include the new log type

### Modifying Log Formats

Edit the log generator application code to change the format of generated logs.

## Troubleshooting

### Common Issues

1. **Init Container Fails**: Check if Kibana is accessible and credentials are correct
2. **No Logs in Elasticsearch**: Verify that the log generator is running and check agent status
3. **Agent Not Sending Data**: Check for connectivity issues between the agent and Elasticsearch

### Viewing Logs

```bash
# View init container logs
kubectl logs <pod-name> -c elastic-agent-installer

# View log generator logs
kubectl logs <pod-name> -c mysql-log-generator
kubectl logs <pod-name> -c nginx-frontend-log-generator
kubectl logs <pod-name> -c nginx-backend-log-generator
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. 