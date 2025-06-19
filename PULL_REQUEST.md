# Pull Request: Initial Pebbles Implementation

## Summary
Initial implementation of Pebbles - a file processing progress tracking service built with Clojure and MongoDB.

## Description
Pebbles is a REST API service that tracks the progress of file processing operations with the following features:

### Core Features
- ✅ **Progress Tracking**: Track done, warn, and failed counts for file processing
- ✅ **Error/Warning Details**: Store line numbers and messages for each error/warning
- ✅ **JWT Authentication**: Secure access using Google JWT tokens
- ✅ **User Isolation**: Each user can only see and update their own progress
- ✅ **Incremental Updates**: Counts and errors/warnings are accumulated, not replaced
- ✅ **Completion Locking**: Once marked complete with `isLast: true`, no further updates allowed

### API Endpoints
- `POST /progress` - Create or update file processing progress
- `GET /progress` - Retrieve progress (all or by filename)
- `GET /health` - Health check endpoint

### Technical Stack
- **Language**: Clojure with functional programming principles
- **Web Framework**: Pedestal
- **Database**: MongoDB with Monger client
- **Authentication**: Google JWT via clj-jwt
- **Testing**: Comprehensive test suite with testcontainers
- **Containerization**: Docker with docker-compose

### Project Structure
```
pebbles/
├── src/pebbles/        # Source code
├── test/pebbles/       # Test files
├── deps.edn            # Dependencies
├── Dockerfile          # Container configuration
├── docker-compose.yml  # Local development setup
└── CI/CD configs       # CircleCI, GitHub Actions, GitLab CI
```

## Testing
All components have been tested:
- ✅ Validation specs for request parameters
- ✅ HTTP response handlers
- ✅ Business logic (count/error accumulation)
- ✅ Database operations
- ✅ JWT authentication

## CI/CD
Includes configurations for:
- CircleCI (`.circleci/config.yml`)
- GitHub Actions (`.github/workflows/ci.yml`)
- GitLab CI (`.gitlab-ci.yml`)

## Example Usage
```bash
# Update progress with errors
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data.csv",
    "counts": {"done": 100, "warn": 2, "failed": 3},
    "errors": [
      {"line": 15, "message": "Invalid date format"},
      {"line": 27, "message": "Missing required field"}
    ],
    "warnings": [
      {"line": 10, "message": "Deprecated field used"}
    ]
  }'
```

## Checklist
- [x] Code implementation complete
- [x] Tests written and passing
- [x] Documentation updated
- [x] CI/CD configurations added
- [x] Docker setup included
- [x] API examples provided

## Type of Change
- [x] New feature (non-breaking change which adds functionality)
- [ ] Bug fix
- [ ] Breaking change
- [ ] Documentation update

## How to Test
1. Clone and checkout this branch
2. Run tests: `clojure -M:test`
3. Run locally: `docker-compose up`
4. Test endpoints using examples in `examples/api-usage.md`

## Additional Notes
- Based on the score-me structure as requested
- Follows functional programming principles with immutability
- Comprehensive error handling and validation
- Production-ready with security considerations