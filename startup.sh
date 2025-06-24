#!/bin/bash

# Background Agent Startup Script
# This script should be run when the background agent starts

echo "🚀 Starting Pebbles Project Environment Setup..."

# Change to the script's directory (project root)
cd "$(dirname "$0")"

# Run the main setup script
if [ -f "./setup-environment.sh" ]; then
    ./setup-environment.sh
    
    # Source environment variables for current session
    if [ -f ".env.test" ]; then
        export $(cat .env.test | grep -v '^#' | xargs)
        echo "✓ Environment variables loaded"
    fi
else
    echo "❌ Error: setup-environment.sh not found!"
    echo "Please ensure you're in the project root directory."
    exit 1
fi

# Optional: Start the application if requested
if [ "$1" = "--start-app" ]; then
    echo "🏃 Starting Pebbles application..."
    clojure -M:run
fi

# Show helpful information
echo ""
echo "📋 Quick Reference:"
echo "  - Run tests: make test"
echo "  - Start app: clojure -M:run"
echo "  - MongoDB: mongodb://localhost:27017"
echo ""
echo "✅ Environment ready for development!"