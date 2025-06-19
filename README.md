# Pebbles

A file processing progress tracking service built with Clojure and MongoDB that provides real-time monitoring and secure update capabilities.

## Overview

Pebbles is a REST API service that tracks the progress of file processing operations. It provides endpoints to:
- **Update progress** for a file (counts of done, warnings, and failures) - **Authenticated**
- **Retrieve progress** for specific files, users, or all files - **Public access**
- **Monitor processing** in real-time with detailed error/warning tracking
- **Enforce authorization** - only file creators can update their files
- **Lock completion** - once marked complete, no further updates allowed

## Key Features

### Security & Authorization
- **JWT Authentication**: Uses Google JWT tokens for update operations
- **Creator Authorization**: Only the user who started processing a file can update it
- **Public Monitoring**: Anyone can view progress without authentication
- **Completion Locking**: Once marked complete (`isLast: true`), progress becomes immutable

### Progress Tracking
- **Incremental Updates**: Counts accumulate across multiple updates (supports distributed processing)
- **Error/Warning Accumulation**: Detailed tracking with line numbers and messages
- **Total Discovery**: Total can be set initially or discovered during processing
- **Real-time Status**: Updated timestamps and completion status

### Data Management
- **User Isolation**: Each user-file combination creates a unique progress record
- **MongoDB Storage**: Persistent storage with optimized indexes
- **Functional Design**: Immutable data structures and pure functions

## Prerequisites

- Java 11+
- Clojure CLI tools
- MongoDB (or Docker for tests)

## API Endpoints

### POST /progress - Update Progress (Authenticated)
Create or update progress for a file. Only authenticated users can update, and only the original creator can modify their file's progress.

**Headers:**
- `Authorization: Bearer <JWT_TOKEN>` (Required)

**Request Body:**
```json
{
  "filename": "sales-data.csv",
  "counts": {
    "done": 150,
    "warn": 5,
    "failed": 2
  },
  "total": 2000,        // Optional, can be set/updated on any request
  "isLast": false,      // Optional, set to true to mark as complete
  "errors": [           // Optional, accumulated across all updates
    {
      "line": 45,
      "message": "Invalid date format: '13/45/2024'"
    },
    {
      "line": 67,
      "message": "Missing required field: customer_email"
    }
  ],
  "warnings": [         // Optional, accumulated across all updates
    {
      "line": 23,
      "message": "Deprecated field 'phone_number' used"
    }
  ]
}
```

**Response (Success):**
```json
{
  "result": "created",           // or "updated"
  "filename": "sales-data.csv",
  "counts": {
    "done": 150,
    "warn": 5,
    "failed": 2
  },
  "total": 2000,
  "isCompleted": false,
  "errors": [
    {
      "line": 45,
      "message": "Invalid date format: '13/45/2024'"
    },
    {
      "line": 67,
      "message": "Missing required field: customer_email"
    }
  ],
  "warnings": [
    {
      "line": 23,
      "message": "Deprecated field 'phone_number' used"
    }
  ]
}
```

**Authorization Behaviors:**
- **403 Forbidden**: If trying to update another user's file
- **400 Bad Request**: If file is already completed
- **400 Bad Request**: If request validation fails

### GET /progress - Retrieve Progress (Public)
Retrieve progress information. No authentication required - supports public monitoring.

**Query Parameters:**
- `filename` (optional): Get progress for a specific file (any user's)
- `email` (optional): Get all progress for a specific user

**Access Patterns:**

#### Get All Progress (Public Dashboard)
```
GET /progress
```
Returns progress from all users, sorted by most recent updates.

#### Get Specific File Progress
```
GET /progress?filename=sales-data.csv
```
Returns progress for the specified file (regardless of which user is processing it).

#### Get User's Progress
```
GET /progress?email=user@example.com
```
Returns all files being processed by the specified user.

**Response Examples:**

**Single File Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "filename": "sales-data.csv",
  "email": "alice@company.com",
  "counts": {
    "done": 1800,
    "warn": 25,
    "failed": 8
  },
  "total": 2000,
  "isCompleted": false,
  "createdAt": "2024-01-15T09:00:00Z",
  "updatedAt": "2024-01-15T09:15:00Z",
  "errors": [
    {
      "line": 45,
      "message": "Invalid date format"
    }
  ],
  "warnings": [
    {
      "line": 23,
      "message": "Deprecated field used"
    }
  ]
}
```

**Multiple Files Response:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "filename": "sales-data.csv",
    "email": "alice@company.com",
    "counts": { "done": 1800, "warn": 25, "failed": 8 },
    "total": 2000,
    "isCompleted": false,
    "createdAt": "2024-01-15T09:00:00Z",
    "updatedAt": "2024-01-15T09:15:00Z",
    "errors": [...],
    "warnings": [...]
  },
  {
    "id": "507f1f77bcf86cd799439012", 
    "filename": "customer-import.csv",
    "email": "bob@company.com",
    "counts": { "done": 300, "warn": 5, "failed": 0 },
    "total": 500,
    "isCompleted": true,
    "createdAt": "2024-01-15T08:30:00Z",
    "updatedAt": "2024-01-15T08:45:00Z",
    "errors": [],
    "warnings": [...]
  }
]
```

### GET /health - Health Check
Simple health check endpoint.

**Response:**
```
OK
```

## Common Use Cases

See [use-cases.md](use-cases.md) for detailed examples including:

1. **Clean Processing with Known Total**: Simple workflow with predetermined record count
2. **Complex Processing with Errors**: Mid-process total discovery with error accumulation  
3. **Streaming Process**: Dynamic total discovery during processing
4. **Parallel Processing & Authorization**: Multiple users processing different files

## Configuration

**Environment Variables:**
- `MONGO_URI`: MongoDB connection string (default: `mongodb://localhost:27017/pebbles`)
- `PORT`: HTTP server port (default: `8081`)

**Database:**
- Collection: `progress`
- Indexes: Compound unique on `filename + email`, single on `email`

## Running the Application

### Development
```bash
clj -M:dev -m pebbles.system
```

### Production
```bash
clj -M -m pebbles.system
```

### Docker
```bash
docker-compose up
```

## Running Tests

```bash
clj -M:test
```

Tests use testcontainers to automatically spin up MongoDB instances.

## Project Structure

```
pebbles/
├── deps.edn              # Dependencies and aliases
├── src/
│   └── pebbles/
│       ├── db.clj        # Database operations
│       ├── http_resp.clj # HTTP response utilities  
│       ├── jwt.clj       # JWT authentication
│       ├── specs.clj     # Request/response validation
│       └── system.clj    # Main system components and handlers
├── test/
│   └── pebbles/
│       ├── db_test.clj
│       ├── http_resp_test.clj
│       ├── jwt_test.clj
│       ├── progress_handler_test.clj
│       ├── system_test.clj
│       ├── test_utils.clj
│       └── validation_test.clj
├── examples/
│   └── api-usage.md      # API usage examples
├── use-cases.md          # Detailed use cases and workflows
└── resources/
    └── simplelogger.properties
```

## Key Behaviors

| Feature | Behavior |
|---------|----------|
| **Progress Updates** | Counts are accumulated (added), not replaced |
| **File Ownership** | First user to create progress owns the file |
| **Authorization** | Only file owner can update; anyone can read |
| **Completion** | Once `isLast: true`, progress becomes immutable |
| **Error Tracking** | Errors/warnings accumulate across all updates |
| **Total Discovery** | Total can be set/updated on any request |
| **Data Security** | Server generates all timestamps; client values ignored |

## Response Codes

| Code | Meaning | When |
|------|---------|------|
| **200** | Success | Successful GET or POST operation |
| **400** | Bad Request | Validation failed or file already completed |
| **401** | Unauthorized | Missing or invalid JWT token (POST only) |
| **403** | Forbidden | Trying to update another user's file |
| **404** | Not Found | Requested progress record doesn't exist |
| **500** | Server Error | Database or internal server error |

## Design Principles

1. **Functional Style**: Immutable data structures and pure functions where possible
2. **Security by Design**: Authentication for updates, authorization for ownership
3. **Public Transparency**: Anyone can monitor progress without credentials
4. **Incremental Processing**: Support for distributed/parallel processing workers
5. **Error Visibility**: Comprehensive error and warning tracking with context
6. **Completion Integrity**: Immutable state once processing is marked complete

## CI/CD

This project includes CI/CD configurations for multiple platforms:

- **CircleCI**: `.circleci/config.yml`
- **GitHub Actions**: `.github/workflows/ci.yml`  
- **GitLab CI**: `.gitlab-ci.yml`

See [CI_CD_SETUP.md](CI_CD_SETUP.md) for detailed setup instructions.