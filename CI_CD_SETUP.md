# CI/CD Setup Guide for Pebbles

This project includes CI/CD configurations for three popular platforms:
- CircleCI
- GitHub Actions
- GitLab CI

## CircleCI Setup

### Prerequisites
1. Connect your repository to CircleCI
2. Create a context called `docker-hub-creds` with:
   - `DOCKER_USER`: Your Docker Hub username
   - `DOCKER_PASS`: Your Docker Hub password

### Configuration Features
- **Test Job**: Runs Clojure tests with MongoDB via testcontainers
- **Build Job**: Builds and tags Docker images
- **Deploy Job**: Pushes images to Docker Hub (main branch only)
- **Caching**: Dependencies are cached for faster builds

### File Location
`.circleci/config.yml`

## GitHub Actions Setup

### Prerequisites
1. Add the following secrets to your repository:
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

### File Location
`.github/workflows/ci.yml`

## GitLab CI Setup

### Prerequisites
1. GitLab CI is automatically enabled for GitLab repositories
2. Docker Registry is available by default

### Configuration Features
- **Test Stage**: Runs tests and linting in parallel
- **Build Stage**: Builds Docker images and pushes to GitLab Registry
- **Deploy Stage**: Manual deployment to staging/production
- **Security Stage**: Container scanning with Trivy

### Environment Variables
The following are automatically available:
- `CI_REGISTRY_USER`: GitLab registry username
- `CI_REGISTRY_PASSWORD`: GitLab registry password
- `CI_REGISTRY_IMAGE`: Full image path in GitLab registry

### File Location
`.gitlab-ci.yml`

## Common Tasks

### Running Tests Locally
```bash
# With local MongoDB
MONGO_URI=mongodb://localhost:27017/pebbles-test clojure -M:test

# With Docker Compose
docker-compose up -d mongodb
clojure -M:test
```

### Building Docker Image Locally
```bash
docker build -t pebbles:local .
```

### Testing the Docker Image
```bash
docker-compose up
```

## CI/CD Best Practices

1. **Branch Protection**: Enable branch protection rules for `main`
2. **Required Checks**: Make tests required before merging
3. **Semantic Versioning**: Tag releases with semantic versions
4. **Environment Variables**: Use CI platform secrets for sensitive data
5. **Caching**: Cache dependencies to speed up builds

## Troubleshooting

### CircleCI
- If testcontainers fail, ensure `TESTCONTAINERS_RYUK_DISABLED: true` is set
- Check Docker layer caching is enabled in your CircleCI plan

### GitHub Actions
- MongoDB service uses `localhost` in tests, not `mongodb`
- Use `actions/cache` for dependency caching

### GitLab CI
- Use `mongodb` as the hostname when using service containers
- GitLab Registry requires no additional authentication setup

## Security Considerations

1. **Never commit secrets** to the repository
2. **Use CI platform secret management** for credentials
3. **Scan images** for vulnerabilities before deployment
4. **Keep base images updated** in Dockerfile
5. **Review dependencies** regularly for security updates

## Monitoring CI/CD

- Set up notifications for failed builds
- Monitor build times and optimize if needed
- Review test coverage reports
- Track deployment frequency and success rate