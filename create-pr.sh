#!/bin/bash

echo "=== Pebbles Pull Request Creation Script ==="
echo ""
echo "This script will help you create a pull request for the Pebbles implementation."
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Prerequisites:${NC}"
echo "1. Fork https://github.com/pirkus/pebbles (if not your repo)"
echo "2. Set up SSH keys for GitHub"
echo "3. Have GitHub CLI installed (optional but recommended)"
echo ""

# Check current branch
CURRENT_BRANCH=$(git branch --show-current)
echo -e "${GREEN}Current branch:${NC} $CURRENT_BRANCH"

# Push the branch
echo ""
echo -e "${YELLOW}Step 1: Push the feature branch${NC}"
echo "Run: git push -u origin $CURRENT_BRANCH"
echo ""

echo -e "${YELLOW}Step 2: Create Pull Request${NC}"
echo ""
echo "Option A - Using GitHub CLI (if installed):"
echo "gh pr create --title \"Initial Pebbles Implementation\" --body-file PULL_REQUEST.md"
echo ""
echo "Option B - Using GitHub Web:"
echo "1. Go to https://github.com/pirkus/pebbles"
echo "2. Click 'Compare & pull request' button that appears after pushing"
echo "3. Copy the content from PULL_REQUEST.md into the PR description"
echo ""

echo -e "${GREEN}Branch ready for PR:${NC} $CURRENT_BRANCH"
echo -e "${GREEN}PR description:${NC} PULL_REQUEST.md"
echo ""
echo "The PR includes:"
echo "- Complete Pebbles implementation"
echo "- Comprehensive test suite"
echo "- Docker configuration"
echo "- CI/CD for CircleCI, GitHub Actions, and GitLab"
echo "- Full documentation"