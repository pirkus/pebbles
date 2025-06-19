# Pebbles

A simple file processing progress tracking service built with Clojure and MongoDB.

## Overview

Pebbles is a REST API service that tracks the progress of file processing operations. It provides endpoints to:
- Update progress for a file (counts of done, warnings, and failures)
- Retrieve progress for specific files or all files for a user
- Secure access using JWT authentication

## Features

- **JWT Authentication**: Uses Google JWT tokens for authentication
- **Progress Tracking**: Track done, warn, and failed counts for file processing
- **User Isolation**: Each user can only see and update their own file progress
- **Completion Locking**: Once a file is marked as complete, no further updates are allowed
- **MongoDB Storage**: Persistent storage of progress data

## Prerequisites

- Java 11+
- Clojure CLI tools
- MongoDB (or Docker for running tests)

## API Endpoints

### POST /progress
Update or create progress for a file.

**Headers:**
- `Authorization: Bearer <JWT_TOKEN>`

**Request Body:**
```json
{
  "filename": "data.csv",
  "counts": {
    "done": 10,
    "warn": 2,
    "failed": 1
  },
  "total": 100,      // optional, can be set on first request
  "isLast": false,   // optional, set to true to mark as complete
  "errors": [        // optional, list of errors encountered
    {
      "line": 15,
      "message": "Invalid date format"
    },
    {
      "line": 27,
      "message": "Missing required field: email"
    }
  ],
  "warnings": [      // optional, list of warnings encountered
    {
      "line": 10,
      "message": "Deprecated field 'phone' used"
    }
  ]
}
```

**Response:**
```json
{
  "result": "created" | "updated",
  "filename": "data.csv",
  "counts": {
    "done": 10,
    "warn": 2,
    "failed": 1
  },
  "total": 100,
  "isCompleted": false,
  "errors": [...],    // accumulated list of all errors
  "warnings": [...]   // accumulated list of all warnings
}
```

### GET /progress
Retrieve progress for files.

**Headers:**
- `Authorization: Bearer <JWT_TOKEN>`

**Query Parameters:**
- `filename` (optional): Get progress for a specific file

**Response (specific file):**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "filename": "data.csv",
  "email": "user@example.com",
  "counts": {
    "done": 50,
    "warn": 5,
    "failed": 2
  },
  "total": 100,
  "isCompleted": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:10:00Z",
  "errors": [
    {
      "line": 15,
      "message": "Invalid date format"
    },
    {
      "line": 27,
      "message": "Missing required field"
    }
  ],
  "warnings": [
    {
      "line": 10,
      "message": "Deprecated field used"
    }
  ]
}
```

**Response (all files):**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "filename": "data1.csv",
    "errors": [...],
    "warnings": [...],
    ...
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "filename": "data2.csv",
    "errors": [...],
    "warnings": [...],
    ...
  }
]
```

### GET /health
Health check endpoint.

**Response:**
```
OK
```

## Configuration

Environment variables:
- `MONGO_URI`: MongoDB connection string (default: `mongodb://localhost:27017/pebbles`)
- `PORT`: HTTP server port (default: `8081`)

## Running the Application

### Development
```bash
cd pebbles
clj -M:dev -m pebbles.system
```

### Production
```bash
cd pebbles
clj -M -m pebbles.system
```

## Running Tests

```bash
cd pebbles
clj -M:test
```

Tests use testcontainers to spin up a MongoDB instance automatically.

## CI/CD

This project includes CI/CD configurations for multiple platforms:

- **CircleCI**: `.circleci/config.yml`
- **GitHub Actions**: `.github/workflows/ci.yml`  
- **GitLab CI**: `.gitlab-ci.yml`

See [CI_CD_SETUP.md](CI_CD_SETUP.md) for detailed setup instructions.

## Project Structure

```
pebbles/
├── deps.edn              # Dependencies and aliases
├── src/
│   └── pebbles/
│       ├── db.clj        # Database operations
│       ├── http_resp.clj # HTTP response utilities
│       ├── jwt.clj       # JWT authentication
│       ├── specs.clj     # Validation specs
│       └── system.clj    # Main system components and handlers
├── test/
│   └── pebbles/
│       ├── db_test.clj
│       ├── http_resp_test.clj
│       ├── jwt_test.clj
│       ├── progress_handler_test.clj
│       ├── test_utils.clj
│       └── validation_test.clj
└── resources/
    └── simplelogger.properties
```

## Design Decisions

1. **Incremental Updates**: Progress counts are added to existing values rather than replaced, allowing for distributed processing where multiple workers can update progress.

2. **Unique per User/File**: The combination of email (from JWT) and filename creates a unique constraint, ensuring one progress record per file per user.

3. **Immutable Completion**: Once `isLast` is set to true, the progress becomes immutable to prevent accidental updates after completion.

4. **Functional Style**: The codebase follows functional programming principles with immutable data structures and pure functions where possible.

5. **Error/Warning Tracking**: Detailed error and warning information is accumulated across all updates, providing a complete audit trail of issues encountered during processing.