#!/usr/bin/env python3

import os
import sys
import requests
import json
import glob
import time
import logging
import argparse
import subprocess
import base64
import yaml
from pathlib import Path
from urllib3.exceptions import InsecureRequestWarning

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)

# Suppress only the insecure request warning
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

# ANSI color codes for better output
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
NC = '\033[0m'  # No Color

# Parse command-line arguments
def parse_arguments():
    parser = argparse.ArgumentParser(description='Install Elastic Agent policies and integrations in Kubernetes')
    
    # Elasticsearch/Kibana connection settings
    parser.add_argument('--kibana-url', dest='kibana_url', help='Kibana URL')
    parser.add_argument('--elasticsearch-url', dest='elasticsearch_url', help='Elasticsearch URL')
    parser.add_argument('--fleet-url', dest='fleet_url', help='Fleet URL')
    parser.add_argument('--username', dest='username', help='Elasticsearch username')
    parser.add_argument('--password', dest='password', help='Elasticsearch password')
    
    # Kubernetes settings
    parser.add_argument('--namespace', dest='namespace', default='default', help='Kubernetes namespace')
    parser.add_argument('--image-tag', dest='image_tag', default='latest', help='Docker image tag')
    
    # Operation modes
    parser.add_argument('--client-type', dest='client_type', help='Client type: mysql, nginx-frontend, or nginx-backend')
    parser.add_argument('--verify-ssl', dest='verify_ssl', action='store_true', help='Verify SSL certificates')
    parser.add_argument('--output-token', dest='output_token', action='store_true', help='Only output the enrollment token')
    parser.add_argument('--skip-token-generation', dest='skip_token_generation', action='store_true', help='Skip Elastic enrollment token generation')
    parser.add_argument('--force-skip-token', dest='force_skip_token', action='store_true', help='Force skip token generation on any error')
    parser.add_argument('--debug', dest='debug', action='store_true', help='Enable debug output')
    
    # Paths
    parser.add_argument('--base-dir', dest='base_dir', help='Base directory for config files')
    
    return parser.parse_args()

# Read configuration from environment or command-line arguments
def get_config():
    args = parse_arguments()
    
    config = {
        'kibana_url': args.kibana_url or os.environ.get('KIBANA_URL'),
        'elasticsearch_url': args.elasticsearch_url or os.environ.get('ELASTICSEARCH_URL'),
        'elasticsearch_user': args.username or os.environ.get('ELASTICSEARCH_USER'),
        'elasticsearch_password': args.password or os.environ.get('ELASTICSEARCH_PASSWORD'),
        'fleet_url': args.fleet_url or os.environ.get('FLEET_URL') or args.kibana_url or os.environ.get('KIBANA_URL'),
        'verify_ssl': args.verify_ssl or (os.environ.get('VERIFY_SSL', 'false').lower() == 'true'),
        'max_retries': int(os.environ.get('MAX_RETRIES', '5')),
        'retry_delay': int(os.environ.get('RETRY_DELAY', '10')),
        'client_type': args.client_type or os.environ.get('CLIENT_TYPE', 'all'),
        'namespace': args.namespace or os.environ.get('NAMESPACE', 'default'),
        'output_token': args.output_token,
        'base_dir': args.base_dir or os.path.dirname(os.path.abspath(__file__)),
        'skip_token_generation': args.skip_token_generation,
        'force_skip_token': args.force_skip_token,
        'debug': args.debug,
        'image_tag': args.image_tag or 'latest'
    }
    
    # Check if required configuration is set when generating tokens
    if not config['skip_token_generation']:
        if not config['kibana_url']:
            logger.error("Kibana URL is not set")
            sys.exit(1)
        if not config['elasticsearch_user']:
            logger.error("Elasticsearch username is not set")
            sys.exit(1)
        if not config['elasticsearch_password']:
            logger.error("Elasticsearch password is not set")
            sys.exit(1)
    
    return config

# Global configuration
config = get_config()

HEADERS = {
    'Content-Type': 'application/json',
    'kbn-xsrf': 'true'
}

def debug_log(message):
    """Print debug message if debug mode is enabled."""
    if config['debug']:
        logger.info(f"{YELLOW}[DEBUG] {message}{NC}")

def command_exists(command):
    """Check if a command exists on the system."""
    try:
        subprocess.run(['which', command], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        return True
    except subprocess.CalledProcessError:
        return False

def check_prerequisites():
    """Check if required tools are installed."""
    print(f"\n{BLUE}Checking prerequisites...{NC}")
    
    # Check for kubectl
    if not command_exists('kubectl'):
        print(f"{RED}Error: kubectl is required but not installed. Please install kubectl and try again.{NC}")
        sys.exit(1)
    
    # Check for curl
    if not command_exists('curl'):
        print(f"{RED}Error: curl is required but not installed. Please install curl and try again.{NC}")
        sys.exit(1)
    
    # Check for jq and install if missing
    if not command_exists('jq'):
        print(f"{YELLOW}Warning: jq is not installed. Installing it for JSON processing...{NC}")
        try:
            if command_exists('apt-get'):
                subprocess.run(['sudo', 'apt-get', 'update'], check=True)
                subprocess.run(['sudo', 'apt-get', 'install', '-y', 'jq'], check=True)
            elif command_exists('brew'):
                subprocess.run(['brew', 'install', 'jq'], check=True)
            elif command_exists('yum'):
                subprocess.run(['sudo', 'yum', 'install', '-y', 'jq'], check=True)
            else:
                print(f"{RED}Error: Could not install jq. Please install it manually and try again.{NC}")
                sys.exit(1)
        except subprocess.CalledProcessError as e:
            print(f"{RED}Error installing jq: {str(e)}{NC}")
            sys.exit(1)
    
    # Verify connection to Kubernetes cluster
    print(f"{BLUE}Verifying Kubernetes cluster connection...{NC}")
    try:
        subprocess.run(['kubectl', 'cluster-info'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    except subprocess.CalledProcessError:
        print(f"{RED}Error: Unable to connect to Kubernetes cluster. Please check your kubeconfig.{NC}")
        sys.exit(1)
    
    # Check that deployment file exists
    deployment_file = os.path.join(config['base_dir'], 'kubernetes', 'elastic-agents.yaml')
    if not os.path.exists(deployment_file):
        print(f"{RED}Error: Deployment file not found at {deployment_file}{NC}")
        sys.exit(1)
    
    return True

def create_namespace():
    """Create Kubernetes namespace if it doesn't exist."""
    if config['namespace'] != 'default':
        print(f"\n{BLUE}Creating namespace {config['namespace']} if it doesn't exist...{NC}")
        try:
            subprocess.run([
                'kubectl', 'create', 'namespace', config['namespace'],
                '--dry-run=client', '-o', 'yaml'
            ], stdout=subprocess.PIPE, check=True, text=True).stdout | \
            subprocess.run(['kubectl', 'apply', '-f', '-'], input=_, check=True, text=True)
            print(f"{GREEN}Namespace created or already exists.{NC}")
        except subprocess.CalledProcessError as e:
            print(f"{RED}Error creating namespace: {str(e)}{NC}")
            sys.exit(1)

def create_elasticsearch_secret():
    """Create or update Elasticsearch credentials secret."""
    print(f"\n{BLUE}Checking for existing Elasticsearch credentials secret...{NC}")
    
    # Check if secret exists and delete it if found
    try:
        result = subprocess.run([
            'kubectl', 'get', 'secret', 'elasticsearch-credentials',
            '-n', config['namespace']
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        if result.returncode == 0:
            print(f"{YELLOW}Found existing secret 'elasticsearch-credentials'. Deleting it...{NC}")
            subprocess.run([
                'kubectl', 'delete', 'secret', 'elasticsearch-credentials',
                '-n', config['namespace']
            ], check=True)
            print(f"{GREEN}Existing secret deleted successfully.{NC}")
        else:
            print(f"{BLUE}No existing secret found. Creating new secret...{NC}")
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error checking for existing secret: {str(e)}{NC}")
    
    # Create new secret
    print(f"\n{BLUE}Creating Elasticsearch credentials secret...{NC}")
    try:
        subprocess.run([
            'kubectl', 'create', 'secret', 'generic', 'elasticsearch-credentials',
            '--namespace', config['namespace'],
            '--from-literal=ELASTICSEARCH_USER=' + config['elasticsearch_user'],
            '--from-literal=ELASTICSEARCH_PASSWORD=' + config['elasticsearch_password'],
            '--from-literal=KIBANA_URL=' + config['kibana_url'],
            '--from-literal=ELASTICSEARCH_URL=' + config['elasticsearch_url'],
            '--from-literal=FLEET_URL=' + config['fleet_url'],
            '--dry-run=client', '-o', 'yaml'
        ], stdout=subprocess.PIPE, check=True, text=True).stdout | \
        subprocess.run(['kubectl', 'apply', '-f', '-'], input=_, check=True, text=True)
        
        print(f"{GREEN}Secret created successfully.{NC}")
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error creating secret: {str(e)}{NC}")
        sys.exit(1)

def wait_for_kibana():
    """Wait for Kibana to be available."""
    if config['skip_token_generation']:
        return True
        
    for attempt in range(config['max_retries']):
        try:
            logger.info(f"Checking if Kibana is available at {config['kibana_url']} (attempt {attempt+1}/{config['max_retries']})")
            response = requests.get(
                f"{config['kibana_url']}/api/status",
                headers=HEADERS,
                auth=(config['elasticsearch_user'], config['elasticsearch_password']),
                verify=config['verify_ssl'],
                timeout=10
            )
            if response.status_code == 200:
                logger.info("Kibana is available")
                return True
            logger.warning(f"Kibana is not available yet: {response.status_code}")
        except Exception as e:
            logger.warning(f"Error checking Kibana availability: {str(e)}")
        
        logger.info(f"Waiting {config['retry_delay']} seconds before retrying...")
        time.sleep(config['retry_delay'])
    
    logger.error(f"Kibana did not become available after {config['max_retries']} attempts")
    return False

def get_agent_policy_id(policy_name):
    """Retrieve the agent policy ID by name."""
    url = f"{config['kibana_url']}/api/fleet/agent_policies"
    params = {'kuery': f'name:"{policy_name}"'}
    
    try:
        response = requests.get(
            url,
            headers=HEADERS,
            auth=(config['elasticsearch_user'], config['elasticsearch_password']),
            params=params,
            verify=config['verify_ssl'],
            timeout=10
        )

        if response.status_code == 200:
            data = response.json()
            items = data.get('items', [])
            for item in items:
                if item.get('name') == policy_name:
                    policy_id = item.get('id')
                    logger.info(f"Found agent policy '{policy_name}' with ID: {policy_id}")
                    return policy_id
            logger.info(f"No agent policy found with name '{policy_name}'")
            return None
        else:
            logger.error(f"Failed to retrieve agent policies: {response.status_code} {response.text}")
            return None
    except Exception as e:
        logger.error(f"Error retrieving agent policy: {str(e)}")
        return None

def get_policy_by_client_type(client_type):
    """Get the appropriate policy name based on client type."""
    if client_type == 'mysql':
        return "MySQL Monitoring Policy"
    elif client_type == 'nginx-frontend':
        return "Nginx Frontend Monitoring Policy"
    elif client_type == 'nginx-backend':
        return "Nginx Backend Monitoring Policy"
    else:
        logger.error(f"Unknown client type: {client_type}")
        return None

def create_agent_policies(agent_policies_dir, client_type):
    """Create agent policies from JSON files in the specified directory."""
    logger.info(f"Creating agent policy for {client_type}")
    
    # Determine the agent policy file based on client type
    if client_type == 'mysql':
        agent_policy_file = f"{agent_policies_dir}/mysql-agent-policy.json"
    elif client_type == 'nginx-frontend':
        agent_policy_file = f"{agent_policies_dir}/nginx-frontend-agent-policy.json"
    elif client_type == 'nginx-backend':
        agent_policy_file = f"{agent_policies_dir}/nginx-backend-agent-policy.json"
    else:
        logger.error(f"Unknown client type: {client_type}")
        return
    
    # Ensure the file exists
    if not os.path.exists(agent_policy_file):
        logger.error(f"Agent policy file does not exist: {agent_policy_file}")
        return
    
    try:
        with open(agent_policy_file, 'r') as file:
            agent_policy_config = json.load(file)

        agent_policy_name = agent_policy_config.get('name')
        if not agent_policy_name:
            logger.warning(f"No 'name' field in {agent_policy_file}")
            return

        agent_policy_id = get_agent_policy_id(agent_policy_name)
        if agent_policy_id:
            logger.info(f"Agent policy '{agent_policy_name}' already exists with ID: {agent_policy_id}")
            return

        # Create a new agent policy
        agent_policy_url = f"{config['kibana_url']}/api/fleet/agent_policies?sys_monitoring=true"
        response = requests.post(
            agent_policy_url,
            headers=HEADERS,
            auth=(config['elasticsearch_user'], config['elasticsearch_password']),
            json=agent_policy_config,
            verify=config['verify_ssl'],
            timeout=10
        )

        if response.status_code != 200:
            logger.error(f"Failed to create agent policy: {response.status_code} - {response.text}")
            return

        agent_policy_id = response.json()['item']['id']
        logger.info(f"Created agent policy '{agent_policy_name}' with ID: {agent_policy_id}")
    except Exception as e:
        logger.error(f"Error processing agent policy file {agent_policy_file}: {str(e)}")

def install_integration(integrations_dir, client_type):
    """Install the integration for the specific client type."""
    logger.info(f"Installing integration for client type: {client_type}")
    
    # Map client type to integration file
    if client_type == 'mysql':
        integration_file = f"{integrations_dir}/mysql.json"
    elif client_type == 'nginx-frontend':
        integration_file = f"{integrations_dir}/nginx-frontend.json"
    elif client_type == 'nginx-backend':
        integration_file = f"{integrations_dir}/nginx-backend.json"
    else:
        logger.error(f"Unknown client type: {client_type}")
        return
    
    # Ensure the file exists
    if not os.path.exists(integration_file):
        logger.error(f"Integration file does not exist: {integration_file}")
        return
    
    try:
        # Load the package policy configuration from the JSON file
        with open(integration_file, 'r') as config_file:
            integration_config = json.load(config_file)

        # Get agent policy name from the config
        agent_policy_name = integration_config.get('agent_policy_name')
        if not agent_policy_name:
            logger.warning(f"No 'agent_policy_name' specified in {integration_file}")
            return

        # Retrieve the agent policy ID
        agent_policy_id = get_agent_policy_id(agent_policy_name)
        if not agent_policy_id:
            logger.warning(f"Agent policy '{agent_policy_name}' not found for {integration_file}")
            return

        # Create package policy
        package_policy = integration_config.get('package_policy')
        if not package_policy:
            logger.warning(f"No 'package_policy' specified in {integration_file}")
            return

        package_policy_payload = package_policy.copy()
        package_policy_payload['policy_id'] = agent_policy_id  # Assign the agent policy ID
        package_policy_url = f"{config['kibana_url']}/api/fleet/package_policies"

        response = requests.post(
            package_policy_url,
            headers=HEADERS,
            auth=(config['elasticsearch_user'], config['elasticsearch_password']),
            json=package_policy_payload,
            verify=config['verify_ssl'],
            timeout=10
        )

        if response.status_code != 200:
            logger.error(f"Failed to create package policy: {response.status_code} - {response.text}")
            return

        logger.info(f"Integration from {integration_file} installed successfully.")
    except Exception as e:
        logger.error(f"Error processing integration file {integration_file}: {str(e)}")

def generate_enrollment_token(client_type):
    """Generate an enrollment token for the agent."""
    logger.info(f"Generating enrollment token for {client_type}")
    
    # Get the agent policy name for this client
    agent_policy_name = get_policy_by_client_type(client_type)
    if not agent_policy_name:
        return None
    
    # Get the agent policy ID
    agent_policy_id = get_agent_policy_id(agent_policy_name)
    if not agent_policy_id:
        logger.error(f"Failed to get agent policy ID for '{agent_policy_name}'")
        return None
    
    try:
        # Generate enrollment token
        enrollment_url = f"{config['kibana_url']}/api/fleet/enrollment-api-keys"
        response = requests.get(
            enrollment_url,
            headers=HEADERS,
            auth=(config['elasticsearch_user'], config['elasticsearch_password']),
            verify=config['verify_ssl'],
            timeout=10
        )
        
        if response.status_code != 200:
            logger.error(f"Failed to get enrollment API keys: {response.status_code} - {response.text}")
            return None
        
        # Find the enrollment key for our policy
        enrollment_keys = response.json().get('items', [])
        enrollment_key = None
        for key in enrollment_keys:
            if key.get('policy_id') == agent_policy_id:
                enrollment_key = key.get('api_key')
                break
        
        if not enrollment_key:
            logger.error(f"No enrollment key found for policy ID {agent_policy_id}")
            return None
            
        logger.info(f"Successfully generated enrollment token for {client_type}")
        return enrollment_key
    except Exception as e:
        logger.error(f"Error generating enrollment token: {str(e)}")
        return None

def create_enrollment_tokens_configmap():
    """Generate tokens for each client type and store in a ConfigMap."""
    if config['skip_token_generation']:
        print(f"{YELLOW}Skipping enrollment token generation as requested.{NC}")
        return True
    
    print(f"\n{BLUE}Setting up Elastic Agent policies and generating enrollment tokens...{NC}")
    
    # Initialize token variables with placeholders
    mysql_token = "EXAMPLE_MYSQL_TOKEN_PLACEHOLDER"
    nginx_frontend_token = "EXAMPLE_NGINX_FRONTEND_TOKEN_PLACEHOLDER"
    nginx_backend_token = "EXAMPLE_NGINX_BACKEND_TOKEN_PLACEHOLDER"
    
    # Generate tokens if Kibana is available
    if wait_for_kibana():
        # Set paths
        agent_policies_dir = os.path.join(config['base_dir'], 'elastic', 'agent_policies')
        integrations_dir = os.path.join(config['base_dir'], 'elastic', 'integrations')
        
        # Create MySQL policy and token
        client_type = 'mysql'
        create_agent_policies(agent_policies_dir, client_type)
        install_integration(integrations_dir, client_type)
        token = generate_enrollment_token(client_type)
        if token:
            mysql_token = token
            print(f"{GREEN}Successfully generated MySQL enrollment token{NC}")
        else:
            print(f"{RED}Failed to generate MySQL enrollment token{NC}")
            if config['force_skip_token']:
                print(f"{YELLOW}Force skip token enabled. Using placeholder token.{NC}")
            else:
                return False
        
        # Create Nginx Frontend policy and token
        client_type = 'nginx-frontend'
        create_agent_policies(agent_policies_dir, client_type)
        install_integration(integrations_dir, client_type)
        token = generate_enrollment_token(client_type)
        if token:
            nginx_frontend_token = token
            print(f"{GREEN}Successfully generated Nginx Frontend enrollment token{NC}")
        else:
            print(f"{RED}Failed to generate Nginx Frontend enrollment token{NC}")
            if config['force_skip_token']:
                print(f"{YELLOW}Force skip token enabled. Using placeholder token.{NC}")
            else:
                return False
        
        # Create Nginx Backend policy and token
        client_type = 'nginx-backend'
        create_agent_policies(agent_policies_dir, client_type)
        install_integration(integrations_dir, client_type)
        token = generate_enrollment_token(client_type)
        if token:
            nginx_backend_token = token
            print(f"{GREEN}Successfully generated Nginx Backend enrollment token{NC}")
        else:
            print(f"{RED}Failed to generate Nginx Backend enrollment token{NC}")
            if config['force_skip_token']:
                print(f"{YELLOW}Force skip token enabled. Using placeholder token.{NC}")
            else:
                return False
    else:
        if config['force_skip_token']:
            print(f"{YELLOW}Force skip token enabled. Using placeholder tokens.{NC}")
        else:
            return False
    
    # Check if enrollment tokens ConfigMap exists and delete it
    print(f"\n{BLUE}Checking for existing enrollment tokens ConfigMap...{NC}")
    try:
        result = subprocess.run([
            'kubectl', 'get', 'configmap', 'enrollment-tokens',
            '-n', config['namespace']
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        if result.returncode == 0:
            print(f"{YELLOW}Found existing ConfigMap 'enrollment-tokens'. Deleting it...{NC}")
            subprocess.run([
                'kubectl', 'delete', 'configmap', 'enrollment-tokens',
                '-n', config['namespace']
            ], check=True)
            print(f"{GREEN}Existing ConfigMap deleted successfully.{NC}")
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error checking for existing ConfigMap: {str(e)}{NC}")
    
    # Create ConfigMap with enrollment tokens
    print(f"\n{BLUE}Creating enrollment tokens ConfigMap...{NC}")
    try:
        subprocess.run([
            'kubectl', 'create', 'configmap', 'enrollment-tokens',
            '--namespace', config['namespace'],
            '--from-literal=mysql-enrollment-token=' + mysql_token,
            '--from-literal=nginx-frontend-enrollment-token=' + nginx_frontend_token,
            '--from-literal=nginx-backend-enrollment-token=' + nginx_backend_token,
            '--dry-run=client', '-o', 'yaml'
        ], stdout=subprocess.PIPE, check=True, text=True).stdout | \
        subprocess.run(['kubectl', 'apply', '-f', '-'], input=_, check=True, text=True)
        
        print(f"{GREEN}Enrollment tokens ConfigMap created successfully.{NC}")
        return True
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error creating ConfigMap: {str(e)}{NC}")
        sys.exit(1)

def deploy_log_clients():
    """Deploy log clients from Kubernetes YAML files."""
    print(f"\n{BLUE}Deploying log clients...{NC}")
    
    # Get deployment file
    deployment_file = os.path.join(config['base_dir'], 'kubernetes', 'elastic-agents.yaml')
    
    # Update image tag if needed
    if config['image_tag'] != 'latest':
        print(f"\n{BLUE}Updating image tag to {config['image_tag']} in deployment...{NC}")
        try:
            # Read the deployment file
            with open(deployment_file, 'r') as f:
                deployment_yaml = f.read()
            
            # Replace the image tag
            updated_yaml = deployment_yaml.replace('djhope99/log-generator:latest', f"djhope99/log-generator:{config['image_tag']}")
            
            # Create temporary file for the updated YAML
            temp_file = os.path.join(os.path.dirname(deployment_file), 'temp_deployment.yaml')
            with open(temp_file, 'w') as f:
                f.write(updated_yaml)
            
            deployment_file = temp_file
        except Exception as e:
            print(f"{RED}Error updating image tag: {str(e)}{NC}")
            sys.exit(1)
    
    # Apply the deployment
    try:
        subprocess.run([
            'kubectl', 'apply', '-f', deployment_file,
            '--namespace', config['namespace']
        ], check=True)
        print(f"{GREEN}Log clients deployed successfully.{NC}")
        
        # Clean up temporary file if created
        if config['image_tag'] != 'latest':
            os.remove(deployment_file)
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error deploying log clients: {str(e)}{NC}")
        sys.exit(1)

def verify_deployment():
    """Verify the deployment status."""
    print(f"\n{BLUE}Verifying deployment...{NC}")
    try:
        subprocess.run([
            'kubectl', 'get', 'deployments',
            '--namespace', config['namespace'],
            '-l', 'app in (mysql-log-client,nginx-backend-log-client,nginx-frontend-log-client)'
        ], check=True)
    except subprocess.CalledProcessError as e:
        print(f"{RED}Error verifying deployment: {str(e)}{NC}")
    
    # Wait for pods to be ready
    print(f"\n{BLUE}Waiting for pods to be ready...{NC}")
    try:
        subprocess.run([
            'kubectl', 'wait', '--for=condition=ready', 'pod',
            '--selector=app in (mysql-log-client,nginx-backend-log-client,nginx-frontend-log-client)',
            '--timeout=300s',
            '--namespace', config['namespace']
        ], check=True)
        print(f"{GREEN}All pods are ready!{NC}")
    except subprocess.CalledProcessError:
        print(f"{YELLOW}Warning: Not all pods are ready after 5 minutes.{NC}")
        print(f"{YELLOW}Check the status of your pods with:{NC}")
        print(f"{YELLOW}kubectl get pods -n {config['namespace']}{NC}")

def display_completion_message():
    """Display a completion message with next steps."""
    print(f"\n{GREEN}Installation complete!{NC}")
    print(f"\n{BLUE}To check the logs of your pods, use these commands:{NC}")
    print(f"kubectl logs -n {config['namespace']} -l app=mysql-log-client -c mysql-log-generator")
    print(f"kubectl logs -n {config['namespace']} -l app=nginx-backend-log-client -c nginx-backend-log-generator")
    print(f"kubectl logs -n {config['namespace']} -l app=nginx-frontend-log-client -c nginx-frontend-log-generator")

    print(f"\n{BLUE}To check the Elastic Agent logs:{NC}")
    print(f"kubectl logs -n {config['namespace']} -l app=mysql-log-client -c elastic-agent")
    print(f"kubectl logs -n {config['namespace']} -l app=nginx-backend-log-client -c elastic-agent")
    print(f"kubectl logs -n {config['namespace']} -l app=nginx-frontend-log-client -c elastic-agent")

    print(f"\n{BLUE}To access your Elastic stack:{NC}")
    print(f"Kibana URL: {config['kibana_url']}")
    print(f"Elasticsearch URL: {config['elasticsearch_url']}")
    print(f"Username: {config['elasticsearch_user']}")
    print(f"\n{GREEN}Thank you for using the Log Generator with Elastic Agent Integration!{NC}")

def main():
    """Main function to execute the Kubernetes installation process."""
    # Check for required tools and connections
    check_prerequisites()
    
    # Create namespace if needed
    create_namespace()
    
    # Create Elasticsearch credentials secret
    create_elasticsearch_secret()
    
    # Generate enrollment tokens and create ConfigMap
    create_enrollment_tokens_configmap()
    
    # Deploy log clients
    deploy_log_clients()
    
    # Verify deployment
    verify_deployment()
    
    # Display completion message
    display_completion_message()

if __name__ == "__main__":
    # If only outputting a token, handle that special case
    if config['output_token']:
        if not config['client_type'] or config['client_type'] == 'all':
            print(f"{RED}Error: --client-type must be specified when using --output-token{NC}")
            sys.exit(1)
        
        # Wait for Kibana to be available
        if not wait_for_kibana():
            sys.exit(1)
        
        # Generate enrollment token
        enrollment_token = generate_enrollment_token(config['client_type'])
        if enrollment_token:
            print(enrollment_token)  # Print only the token for scripting
            sys.exit(0)
        else:
            logger.error(f"Failed to generate enrollment token for {config['client_type']}")
            sys.exit(1)
    else:
        # Run the full installation
        main() 