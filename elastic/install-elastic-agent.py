#!/usr/bin/env python3

import os
import sys
import requests
import json
import glob
import time
import logging
import subprocess
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

# Read environment variables
KIBANA_URL = os.environ.get('KIBANA_URL')
ELASTICSEARCH_USER = os.environ.get('ELASTICSEARCH_USER')
ELASTICSEARCH_PASSWORD = os.environ.get('ELASTICSEARCH_PASSWORD')
FLEET_URL = os.environ.get('FLEET_URL')  # Use KIBANA_URL as fallback
VERIFY_SSL = os.environ.get('VERIFY_SSL', 'false').lower() == 'true'
MAX_RETRIES = int(os.environ.get('MAX_RETRIES', '5'))
RETRY_DELAY = int(os.environ.get('RETRY_DELAY', '10'))
CLIENT_TYPE = os.environ.get('CLIENT_TYPE', 'unknown')  # mysql, nginx-frontend, or nginx-backend

# Check if required environment variables are set
if not KIBANA_URL:
    logger.error("KIBANA_URL environment variable is not set")
    sys.exit(1)
if not ELASTICSEARCH_USER:
    logger.error("ELASTICSEARCH_USER environment variable is not set")
    sys.exit(1)
if not ELASTICSEARCH_PASSWORD:
    logger.error("ELASTICSEARCH_PASSWORD environment variable is not set")
    sys.exit(1)
if CLIENT_TYPE == 'unknown':
    logger.error("CLIENT_TYPE environment variable is not set or is unknown")
    sys.exit(1)

HEADERS = {
    'Content-Type': 'application/json',
    'kbn-xsrf': 'true'
}

def wait_for_kibana():
    """Wait for Kibana to be available."""
    for attempt in range(MAX_RETRIES):
        try:
            logger.info(f"Checking if Kibana is available at {KIBANA_URL} (attempt {attempt+1}/{MAX_RETRIES})")
            response = requests.get(
                f"{KIBANA_URL}/api/status",
                headers=HEADERS,
                auth=(ELASTICSEARCH_USER, ELASTICSEARCH_PASSWORD),
                verify=VERIFY_SSL,
                timeout=10
            )
            if response.status_code == 200:
                logger.info("Kibana is available")
                return True
            logger.warning(f"Kibana is not available yet: {response.status_code}")
        except Exception as e:
            logger.warning(f"Error checking Kibana availability: {str(e)}")
        
        logger.info(f"Waiting {RETRY_DELAY} seconds before retrying...")
        time.sleep(RETRY_DELAY)
    
    logger.error(f"Kibana did not become available after {MAX_RETRIES} attempts")
    return False

def get_agent_policy_id(policy_name):
    """Retrieve the agent policy ID by name."""
    url = f"{KIBANA_URL}/api/fleet/agent_policies"
    params = {'kuery': f'name:"{policy_name}"'}
    
    try:
        response = requests.get(
            url,
            headers=HEADERS,
            auth=(ELASTICSEARCH_USER, ELASTICSEARCH_PASSWORD),
            params=params,
            verify=VERIFY_SSL,
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

def get_policy_by_client_type():
    """Get the appropriate policy name based on client type."""
    if CLIENT_TYPE == 'mysql':
        return "MySQL Monitoring Policy"
    elif CLIENT_TYPE == 'nginx-frontend':
        return "Nginx Frontend Monitoring Policy"
    elif CLIENT_TYPE == 'nginx-backend':
        return "Nginx Backend Monitoring Policy"
    else:
        logger.error(f"Unknown client type: {CLIENT_TYPE}")
        return None

def create_agent_policies(agent_policies_dir):
    """Create agent policies from JSON files in the specified directory."""
    logger.info(f"Creating agent policy for {CLIENT_TYPE}")
    
    # Determine the agent policy file based on client type
    if CLIENT_TYPE == 'mysql':
        agent_policy_file = f"{agent_policies_dir}/mysql-agent-policy.json"
    elif CLIENT_TYPE == 'nginx-frontend':
        agent_policy_file = f"{agent_policies_dir}/nginx-frontend-agent-policy.json"
    elif CLIENT_TYPE == 'nginx-backend':
        agent_policy_file = f"{agent_policies_dir}/nginx-backend-agent-policy.json"
    else:
        logger.error(f"Unknown client type: {CLIENT_TYPE}")
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
        agent_policy_url = f"{KIBANA_URL}/api/fleet/agent_policies?sys_monitoring=true"
        response = requests.post(
            agent_policy_url,
            headers=HEADERS,
            auth=(ELASTICSEARCH_USER, ELASTICSEARCH_PASSWORD),
            json=agent_policy_config,
            verify=VERIFY_SSL,
            timeout=10
        )

        if response.status_code != 200:
            logger.error(f"Failed to create agent policy: {response.status_code} - {response.text}")
            return

        agent_policy_id = response.json()['item']['id']
        logger.info(f"Created agent policy '{agent_policy_name}' with ID: {agent_policy_id}")
    except Exception as e:
        logger.error(f"Error processing agent policy file {agent_policy_file}: {str(e)}")

def install_integration(integrations_dir):
    """Install the integration for the specific client type."""
    logger.info(f"Installing integration for client type: {CLIENT_TYPE}")
    
    # Map client type to integration file
    if CLIENT_TYPE == 'mysql':
        integration_file = f"{integrations_dir}/mysql.json"
    elif CLIENT_TYPE == 'nginx-frontend':
        integration_file = f"{integrations_dir}/nginx-frontend.json"
    elif CLIENT_TYPE == 'nginx-backend':
        integration_file = f"{integrations_dir}/nginx-backend.json"
    else:
        logger.error(f"Unknown client type: {CLIENT_TYPE}")
        return
    
    # Ensure the file exists
    if not os.path.exists(integration_file):
        logger.error(f"Integration file does not exist: {integration_file}")
        return
    
    try:
        # Load the package policy configuration from the JSON file
        with open(integration_file, 'r') as config_file:
            config = json.load(config_file)

        # Get agent policy name from the config
        agent_policy_name = config.get('agent_policy_name')
        if not agent_policy_name:
            logger.warning(f"No 'agent_policy_name' specified in {integration_file}")
            return

        # Retrieve the agent policy ID
        agent_policy_id = get_agent_policy_id(agent_policy_name)
        if not agent_policy_id:
            logger.warning(f"Agent policy '{agent_policy_name}' not found for {integration_file}")
            return

        # Create package policy
        package_policy = config.get('package_policy')
        if not package_policy:
            logger.warning(f"No 'package_policy' specified in {integration_file}")
            return

        package_policy_payload = package_policy.copy()
        package_policy_payload['policy_id'] = agent_policy_id  # Assign the agent policy ID
        package_policy_url = f"{KIBANA_URL}/api/fleet/package_policies"

        response = requests.post(
            package_policy_url,
            headers=HEADERS,
            auth=(ELASTICSEARCH_USER, ELASTICSEARCH_PASSWORD),
            json=package_policy_payload,
            verify=VERIFY_SSL,
            timeout=10
        )

        if response.status_code != 200:
            logger.error(f"Failed to create package policy: {response.status_code} - {response.text}")
            return

        logger.info(f"Integration from {integration_file} installed successfully.")
    except Exception as e:
        logger.error(f"Error processing integration file {integration_file}: {str(e)}")

def generate_enrollment_token():
    """Generate an enrollment token for the agent."""
    logger.info(f"Generating enrollment token for {CLIENT_TYPE}")
    
    # Get the agent policy name for this client
    agent_policy_name = get_policy_by_client_type()
    if not agent_policy_name:
        return None
    
    # Get the agent policy ID
    agent_policy_id = get_agent_policy_id(agent_policy_name)
    if not agent_policy_id:
        logger.error(f"Failed to get agent policy ID for '{agent_policy_name}'")
        return None
    
    try:
        # Generate enrollment token
        enrollment_url = f"{KIBANA_URL}/api/fleet/enrollment-api-keys"
        response = requests.get(
            enrollment_url,
            headers=HEADERS,
            auth=(ELASTICSEARCH_USER, ELASTICSEARCH_PASSWORD),
            verify=VERIFY_SSL,
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
            
        logger.info(f"Successfully generated enrollment token for {CLIENT_TYPE}")
        return enrollment_key
    except Exception as e:
        logger.error(f"Error generating enrollment token: {str(e)}")
        return None

def setup_logging_directories():
    """Make sure logging directories exist for the client type."""
    if CLIENT_TYPE == 'mysql':
        os.makedirs('/var/log/mysql', exist_ok=True)
    elif CLIENT_TYPE == 'nginx-frontend':
        os.makedirs('/var/log/nginx_frontend', exist_ok=True)
    elif CLIENT_TYPE == 'nginx-backend':
        os.makedirs('/var/log/nginx_backend', exist_ok=True)
    logger.info(f"Created logging directories for {CLIENT_TYPE}")

def setup_config_directories():
    """Make sure the config directories exist."""
    # Create agent_policies directory if it doesn't exist
    os.makedirs('/app/elastic/agent_policies', exist_ok=True)
    
    # Create integrations directory if it doesn't exist
    os.makedirs('/app/elastic/integrations', exist_ok=True)
    
    logger.info("Created configuration directories")

def main():
    """Main function to setup Elastic agent policies and integrations."""
    logger.info(f"Starting Elastic agent policy and integration setup for {CLIENT_TYPE}")
    
    # Setup logging directories
    setup_logging_directories()
    
    # Setup config directories
    setup_config_directories()
    
    # Wait for Kibana to be available
    if not wait_for_kibana():
        sys.exit(1)
    
    # Set paths
    base_dir = os.path.dirname(os.path.abspath(__file__))
    agent_policies_dir = os.path.join(base_dir, 'agent_policies')
    integrations_dir = os.path.join(base_dir, 'integrations')
    
    # Create agent policies
    create_agent_policies(agent_policies_dir)
    
    # Install integration for this client type
    install_integration(integrations_dir)
    
    # Generate enrollment token to output for sidecar usage
    enrollment_token = generate_enrollment_token()
    if enrollment_token:
        logger.info(f"Elastic agent policy and integration setup completed successfully for {CLIENT_TYPE}")
        logger.info(f"Generated enrollment token: {enrollment_token}")
        # Writing the token to a file that can be mounted in the sidecar container
        with open('/app/elastic/enrollment_token.txt', 'w') as f:
            f.write(enrollment_token)
        logger.info("Enrollment token written to /app/elastic/enrollment_token.txt")
    else:
        logger.error(f"Failed to generate enrollment token for {CLIENT_TYPE}")
        sys.exit(1)

if __name__ == "__main__":
    main() 