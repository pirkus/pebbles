# CI/CD Setup Guide for Pebbles

This project includes CI/CD configurations for three popular platforms:
- CircleCI
- GitHub Actions
- GitLab CI

## CircleCI Setup

### Prerequisites
- Connect your repository to CircleCI
- Create a context called `docker-hub-creds` with:
  - `DOCKER_USER`: Your Docker Hub username
  - `DOCKER_PASS`: Your Docker Hub password

### Configuration Features
- **Test Job**: Runs Clojure tests with MongoDB via testcontainers
- **Build Job**: Builds and tags Docker images
- **Deploy Job**: Pushes images to Docker Hub (main branch only)
- **Caching**: Dependencies are cached for faster builds

**File Location:** `.circleci/config.yml`

## GitHub Actions Setup

### Prerequisites
Add the following secrets to your repository:
- `DOCKER_USERNAME`: Your Docker Hub username
- `DOCKER_PASSWORD`: Your Docker Hub password

### Configuration Features
- **Test Job**: Runs tests with a real MongoDB service
- **Lint Job**: Uses clj-kondo for code linting
- **Build Job**: Builds and pushes Docker images with smart tagging
- **Security Scan**: Uses Trivy to scan for vulnerabilities

### Workflow Triggers
- Push to `main` or `develop` branches
- Pull requests to `main` branch

**File Location:** `.github/workflows/ci.yml`

## GitLab CI Setup

### Prerequisites
GitLab CI is automatically enabled for GitLab repositories with Docker Registry available by default.

### Configuration Features
- **Test Stage**: Runs tests and linting in parallel
- **Build Stage**: Builds Docker images and pushes to GitLab Registry
- **Deploy Stage**: Manual deployment to staging/production
- **Security Stage**: Container scanning with Trivy

### Auto-Available Variables
- `CI_REGISTRY_USER`: GitLab registry username
- `CI_REGISTRY_PASSWORD`: GitLab registry password
- `CI_REGISTRY_IMAGE`: Full image path in GitLab registry

**File Location:** `.gitlab-ci.yml`

## Project-Specific Configuration

### MongoDB Testing Strategy
All CI configurations use the automatic MongoDB detection strategy:
- Local connection attempt first
- Testcontainers fallback for CI environments
- Environment variables for customization:
  - `TESTCONTAINERS_RYUK_DISABLED=true` for CI
  - `TESTCONTAINERS_CHECKS_DISABLE=true` for CI
  - `USE_EXISTING_MONGO=true` to force local connection

### Docker Configuration
All platforms build from the same `Dockerfile` with:
- Multi-stage build for optimization
- MongoDB health checks for docker-compose
- Port 8081 exposure for the Pebbles service

### Test Requirements
```bash
# Specific test command for CI
clojure -M:test

# With custom MongoDB URI if needed
MONGO_URI=mongodb://localhost:27017/pebbles-test clojure -M:test
```