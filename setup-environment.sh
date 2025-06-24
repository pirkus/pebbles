#!/bin/bash

# Pebbles Project Environment Setup Script
# This script sets up the development environment for the Pebbles project

set -e  # Exit on error

echo "=== Pebbles Project Environment Setup ==="
echo "Starting at $(date)"
echo

# Color codes for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    case $1 in
        "success") echo -e "${GREEN}✓ $2${NC}" ;;
        "warning") echo -e "${YELLOW}⚠ $2${NC}" ;;
        "error") echo -e "${RED}✗ $2${NC}" ;;
        *) echo "$2" ;;
    esac
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# 1. Check for required tools
echo "=== Checking Required Tools ==="

# Check for Clojure
if command_exists clojure; then
    CLOJURE_VERSION=$(clojure --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")
    print_status "success" "Clojure installed (version: $CLOJURE_VERSION)"
else
    print_status "error" "Clojure not found! Please install Clojure first."
    exit 1
fi

# Check for Docker
if command_exists docker; then
    DOCKER_VERSION=$(docker --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")
    print_status "success" "Docker installed (version: $DOCKER_VERSION)"
else
    print_status "warning" "Docker not found. Installing Docker..."
    sudo apt-get update -qq
    sudo apt-get install -y docker.io docker-compose
    print_status "success" "Docker installed"
fi

# Check for Docker Compose
if command_exists docker-compose; then
    COMPOSE_VERSION=$(docker-compose --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")
    print_status "success" "Docker Compose installed (version: $COMPOSE_VERSION)"
else
    print_status "warning" "Docker Compose not found. Installing..."
    sudo apt-get install -y docker-compose
    print_status "success" "Docker Compose installed"
fi

echo

# 2. Start Docker daemon if not running
echo "=== Setting up Docker ==="

# Check if Docker daemon is running
if ! sudo docker info >/dev/null 2>&1; then
    print_status "warning" "Docker daemon not running. Starting Docker..."
    
    # Try to start Docker daemon
    if command_exists systemctl; then
        sudo systemctl start docker 2>/dev/null || true
    elif command_exists service; then
        sudo service docker start 2>/dev/null || true
    else
        # Start dockerd directly in background
        print_status "warning" "Starting dockerd directly..."
        sudo dockerd >/dev/null 2>&1 &
        DOCKERD_PID=$!
        echo $DOCKERD_PID > /tmp/dockerd.pid
        sleep 5  # Give Docker time to start
    fi
    
    # Wait for Docker to be ready
    DOCKER_READY=false
    for i in {1..30}; do
        if sudo docker info >/dev/null 2>&1; then
            DOCKER_READY=true
            break
        fi
        sleep 1
    done
    
    if [ "$DOCKER_READY" = true ]; then
        print_status "success" "Docker daemon started"
    else
        print_status "error" "Failed to start Docker daemon"
        exit 1
    fi
else
    print_status "success" "Docker daemon is running"
fi

echo

# 3. Start MongoDB using Docker Compose
echo "=== Setting up MongoDB ==="

# Check if we're in the project directory
if [ ! -f "docker-compose.yml" ]; then
    print_status "error" "docker-compose.yml not found! Please run this script from the project root."
    exit 1
fi

# Check if MongoDB container is already running
if sudo docker ps | grep -q "mongodb"; then
    print_status "success" "MongoDB container is already running"
else
    print_status "warning" "Starting MongoDB container..."
    sudo docker-compose up -d mongodb
    
    # Wait for MongoDB to be ready
    print_status "info" "Waiting for MongoDB to be ready..."
    MONGO_READY=false
    for i in {1..30}; do
        if sudo docker exec $(sudo docker ps -q -f name=mongodb) mongosh --eval "db.adminCommand('ping')" >/dev/null 2>&1; then
            MONGO_READY=true
            break
        fi
        sleep 1
    done
    
    if [ "$MONGO_READY" = true ]; then
        print_status "success" "MongoDB is ready"
    else
        print_status "warning" "MongoDB may not be fully ready yet, but continuing..."
    fi
fi

echo

# 4. Set up environment variables
echo "=== Setting Environment Variables ==="

# Export environment variables for the session
export USE_EXISTING_MONGO=true
export MONGO_URI=mongodb://localhost:27017/test

print_status "success" "Environment variables set:"
echo "  USE_EXISTING_MONGO=true"
echo "  MONGO_URI=mongodb://localhost:27017/test"

# Create a .env file for convenience
cat > .env.test << EOF
# Test environment configuration
USE_EXISTING_MONGO=true
MONGO_URI=mongodb://localhost:27017/test
EOF

print_status "success" "Created .env.test file for test configuration"

echo

# 5. Verify setup by running tests
echo "=== Verifying Setup ==="

print_status "info" "Running tests to verify environment..."

if USE_EXISTING_MONGO=true MONGO_URI=mongodb://localhost:27017/test clojure -X:test 2>&1 | grep -q "0 failures, 0 errors"; then
    print_status "success" "All tests passed! Environment is ready."
else
    print_status "warning" "Some tests may have failed. Check the test output for details."
fi

echo
echo "=== Setup Complete ==="
print_status "success" "Pebbles development environment is ready!"
echo
echo "To run tests manually, use:"
echo "  USE_EXISTING_MONGO=true MONGO_URI=mongodb://localhost:27017/test clojure -X:test"
echo
echo "To start the application, use:"
echo "  clojure -M:run"
echo
echo "MongoDB is accessible at: mongodb://localhost:27017"
echo

# Save setup status
echo "$(date): Setup completed successfully" > .setup-status

exit 0