# Pebbles

A multi-tenant file processing progress tracking service built with Clojure and MongoDB that provides real-time monitoring, secure update capabilities, and multiple integration options.

## Overview

Pebbles is a REST API service that tracks the progress of file processing operations in a multi-tenant environment. It provides endpoints to:
- **Update progress** for a file (counts of done, warnings, and failures) - **Authenticated**
- **Retrieve progress** for specific files, users, or all files within a client - **Public access**
- **Monitor processing** in real-time with detailed error/warning tracking
- **Enforce authorization** - only file creators can update their files
- **Lock completion** - once marked complete, no further updates allowed
- **Multi-tenant support** - isolate data by client using clientKrn
- **Consume updates** from SQS queues and Kafka topics

## Key Features

### Multi-Tenancy
- **Client Isolation**: Each client's data is isolated using clientKrn (Client KRN)
- **Hierarchical URLs**: RESTful API structure `/clients/{clientKrn}/progress`
- **Client-scoped Operations**: All operations are scoped to the specific client

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
- **User Isolation**: Each client-user-file combination creates a unique progress record
- **MongoDB Storage**: Persistent storage with optimized indexes
- **Functional Design**: Immutable data structures and pure functions

### Integration Options
- **REST API**: Direct HTTP endpoint for real-time updates
- **SQS Consumer**: Process updates from AWS SQS queues
- **Kafka Consumer**: Process updates from Kafka topics

## Prerequisites

- Java 11+
- Clojure CLI tools
- MongoDB (or Docker for tests)
- AWS credentials (for SQS integration)
- Kafka cluster (for Kafka integration)

## API Endpoints

### POST /clients/{clientKrn}/progress - Update Progress (Authenticated)
Create or update progress for a file within a client context. Only authenticated users can update, and only the original creator can modify their file's progress.

**Parameters:**
- `clientKrn` (path parameter): Client identifier

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
  "clientKrn": "krn:clnt:this-is-opaque-to-us",
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

### GET /clients/{clientKrn}/progress - Retrieve Progress (Public)
Retrieve progress information for a specific client. No authentication required - supports public monitoring.

**Parameters:**
- `clientKrn` (path parameter): Client identifier
- `filename` (query parameter, optional): Get progress for a specific file
- `email` (query parameter, optional): Get all progress for a specific user

**Access Patterns:**

#### Get All Progress for Client
```
GET /clients/krn:clnt:this-is-opaque-to-us/progress
```
Returns all progress records for the specified client, sorted by most recent updates.

#### Get Specific File Progress
```
GET /clients/krn:clnt:this-is-opaque-to-us/progress?filename=sales-data.csv
```
Returns progress for the specified file within the client.

#### Get User's Progress within Client
```
GET /clients/krn:clnt:this-is-opaque-to-us/progress?email=user@example.com
```
Returns all files being processed by the specified user within the client.

**Response Examples:**

**Single File Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientKrn": "krn:clnt:this-is-opaque-to-us",
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
    "clientKrn": "krn:clnt:this-is-opaque-to-us",
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
    "clientKrn": "krn:clnt:this-is-opaque-to-us",
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

## Message Queue Integration

### SQS Consumer
The service can consume progress updates from an AWS SQS queue.

**Message Format:**
```json
{
  "clientKrn": "krn:clnt:this-is-opaque-to-us",
  "email": "user@example.com",
  "filename": "data.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  },
  "total": 1000,
  "isLast": false,
  "errors": [...],
  "warnings": [...]
}
```

**Configuration:**
- `SQS_ENABLED=true`: Enable SQS consumer
- `SQS_QUEUE_URL`: Full URL of the SQS queue
- `AWS_REGION`: AWS region (default: us-east-1)

### Kafka Consumer
The service can consume progress updates from a Kafka topic.

**Message Format:** Same as SQS (JSON)

**Configuration:**
- `KAFKA_ENABLED=true`: Enable Kafka consumer
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker addresses
- `KAFKA_TOPIC_NAME`: Topic to consume from
- `KAFKA_GROUP_ID`: Consumer group ID (default: pebbles-consumer)

## Common Use Cases

See [use-cases.md](use-cases.md) for detailed examples including:

1. **Multi-tenant File Processing**: Different clients processing files in isolation
2. **Distributed Processing with SQS**: Workers sending updates via SQS
3. **Streaming Updates via Kafka**: Real-time progress streaming
4. **Cross-client Monitoring**: Viewing progress across multiple clients

## Configuration

**Environment Variables:**
- `MONGO_URI`: MongoDB connection string (default: `mongodb://localhost:27017/pebbles`)
- `PORT`: HTTP server port (default: `8081`)
- `SQS_ENABLED`: Enable SQS consumer (true/false)
- `SQS_QUEUE_URL`: SQS queue URL
- `AWS_REGION`: AWS region for SQS
- `KAFKA_ENABLED`: Enable Kafka consumer (true/false)
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka brokers
- `KAFKA_TOPIC_NAME`: Kafka topic name
- `KAFKA_GROUP_ID`: Kafka consumer group ID

**Database:**
- Collection: `progress`
- Indexes: 
  - Compound unique on `clientKrn + filename + email`
  - Compound on `clientKrn + email`
  - Single on `clientKrn`

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

### With SQS Consumer
```bash
SQS_ENABLED=true SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/123456789/my-queue clj -M -m pebbles.system
```

### With Kafka Consumer
```bash
KAFKA_ENABLED=true KAFKA_BOOTSTRAP_SERVERS=localhost:9092 KAFKA_TOPIC_NAME=progress-updates clj -M -m pebbles.system
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
│       ├── system.clj    # Main system components and handlers
│       ├── sqs_consumer.clj   # SQS consumer component
│       └── kafka_consumer.clj # Kafka consumer component
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
| **Multi-tenancy** | All operations are scoped to clientKrn |
| **Progress Updates** | Counts are accumulated (added), not replaced |
| **File Ownership** | First user to create progress owns the file within a client |
| **Authorization** | Only file owner can update; anyone can read |
| **Completion** | Once `isLast: true`, progress becomes immutable |
| **Error Tracking** | Errors/warnings accumulate across all updates |
| **Total Discovery** | Total can be set/updated on any request |
| **Data Security** | Server generates all timestamps; client values ignored |
| **Message Processing** | SQS/Kafka messages follow same validation rules as HTTP |

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

1. **Multi-tenant Architecture**: Complete data isolation between clients
2. **Functional Style**: Immutable data structures and pure functions where possible
3. **Security by Design**: Authentication for updates, authorization for ownership
4. **Public Transparency**: Anyone can monitor progress without credentials
5. **Incremental Processing**: Support for distributed/parallel processing workers
6. **Error Visibility**: Comprehensive error and warning tracking with context
7. **Completion Integrity**: Immutable state once processing is marked complete
8. **Integration Flexibility**: Multiple ways to send updates (HTTP, SQS, Kafka)

## CI/CD

This project includes CI/CD configurations for multiple platforms:

- **CircleCI**: `.circleci/config.yml`
- **GitHub Actions**: `.github/workflows/ci.yml`  
- **GitLab CI**: `.gitlab-ci.yml`

See [CI_CD_SETUP.md](CI_CD_SETUP.md) for detailed setup instructions.