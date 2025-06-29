# Pebbles

A multitenant file processing progress tracking service built with Clojure and MongoDB that provides real-time monitoring and secure update capabilities. Now with a modern React-based UI for visualizing progress data.

## Overview

Pebbles is a REST API service that tracks the progress of file processing operations across multiple tenants. It provides endpoints to:
- **Update progress** for a file within a client tenant (counts of done, warnings, and failures) - **Authenticated**
- **Retrieve progress** for specific files, users, or all files within a client tenant - **Requires clientKrn**
- **Monitor processing** in real-time with detailed error/warning tracking
- **Enforce authorization** - only file creators can update their files within the same tenant
- **Lock completion** - once marked complete, no further updates allowed

## Key Features

### Multitenancy & Data Isolation
- **Client KRN Support**: Every request requires a client KRN (e.g., `krn:clnt:some-opaque-string`)
- **Complete Data Isolation**: Each client can only access their own progress data
- **Tenant-Scoped Operations**: All operations are scoped within the specified client tenant

### Security & Authorization
- **JWT Authentication**: Uses Google JWT tokens for update operations
- **Creator Authorization**: Only the user who started processing a file can update it (within the same tenant)
- **Public Monitoring**: Anyone can view progress with a valid clientKrn (no authentication required)
- **Completion Locking**: Once marked complete (`isLast: true`), progress becomes immutable

### Progress Tracking
- **Incremental Updates**: Counts accumulate across multiple updates (supports distributed processing)
- **Error/Warning Consolidation**: Duplicate error/warning messages are automatically consolidated with combined line numbers
- **Total Discovery**: Total can be set initially or discovered during processing
- **Real-time Status**: Updated timestamps and completion status

### Data Management
- **Client-User-File Isolation**: Each client-user-file combination creates a unique progress record
- **MongoDB Storage**: Persistent storage with optimized indexes for multitenant access
- **Functional Design**: Immutable data structures and pure functions

## Prerequisites

- Java 11+
- Clojure CLI tools
- MongoDB (or Docker for tests)

## Quick Start

### 1. Start MongoDB
Using Docker (recommended for development):
```bash
# Start MongoDB using docker-compose
docker-compose up -d
```

Or install MongoDB locally and ensure it's running on `localhost:27017`.

### 2. Run the Service
```bash
# Start the pebbles service
clj -M -m pebbles.system
```

The service will start on `http://localhost:8081`.

### 3. Test the API
```bash
# Health check
curl http://localhost:8081/health

# View API documentation
open http://localhost:8081/api-docs
```

### Environment Variables
- `MONGO_URI`: MongoDB connection string (default: `mongodb://localhost:27017/pebbles`)
- `PORT`: Service port (default: `8081`)

## API Endpoints

### POST /progress/:clientKrn - Update Progress (Authenticated)
Create or update progress for a file within a client tenant. Only authenticated users can update, and only the original creator can modify their file's progress within the same tenant.

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
  "errors": [           // Optional, send individual error messages
    {
      "line": 45,
      "message": "Invalid date format: '13/45/2024'"
    },
    {
      "line": 67,
      "message": "Missing required field: customer_email"
    }
  ],
  "warnings": [         // Optional, send individual warning messages
    {
      "line": 23,
      "message": "Deprecated field 'phone_number' used"
    }
  ]
}
```

**Important:** Clients should send individual error/warning messages with specific `message` text. The backend automatically groups similar messages using statistical pattern matching and generates consolidated patterns.

**Response (Success):**
```json
{
  "result": "created",           // or "updated"
  "clientKrn": "krn:clnt:my-company",
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
      "pattern": "Invalid date format: {QUOTED}",
      "lines": [
        {"line": 45, "values": ["'13/45/2024'"]}
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {"line": 67, "values": ["customer_email"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field {QUOTED} used",
      "lines": [
        {"line": 23, "values": ["'fax'"]}
      ]
    }
  ]
}
```

**Authorization Behaviors:**
- **403 Forbidden**: If trying to update another user's file (within the same tenant)
- **400 Bad Request**: If file is already completed
- **400 Bad Request**: If request validation fails or clientKrn is missing

### GET /progress/:clientKrn - Retrieve Progress (Requires clientKrn)
Retrieve progress information for a specific client. No authentication required, but clientKrn is mandatory for data isolation.

**Query Parameters:**
- `filename` (optional): Get progress for a specific file within the client
- `email` (optional): Get all progress for a specific user within the client

**Access Patterns:**

#### Get All Progress for Client
```
GET /progress/krn:clnt:my-company
```
Returns all progress records for the specified client, sorted by most recent updates.

#### Get Specific File Progress within Client
```
GET /progress/krn:clnt:my-company?filename=sales-data.csv
```
Returns progress for the specified file within the client tenant.

#### Get User's Progress within Client
```
GET /progress/krn:clnt:my-company?email=user@example.com
```
Returns all files being processed by the specified user within the client tenant.

**Response Examples:**

**Single File Response:**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientKrn": "krn:clnt:my-company",
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
      "pattern": "Invalid date format: {DATE}",
      "lines": [
        {"line": 45, "values": ["2024-13-01"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field {QUOTED} used",
      "lines": [
        {"line": 23, "values": ["'fax'"]}
      ]
    }
  ]
}
```

**Multiple Files Response:**
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
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
    "clientKrn": "krn:clnt:my-company", 
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

## API Documentation (OpenAPI/Swagger)

Pebbles provides interactive API documentation via OpenAPI 3.0 specification and Swagger UI.

### Available Endpoints

- **`/openapi.json`** - Returns the complete OpenAPI 3.0 specification in JSON format
- **`/api-docs`** - Interactive Swagger UI for exploring and testing the API

### Features

- **Auto-generated Documentation**: OpenAPI schemas are automatically generated from Clojure specs
- **Interactive Testing**: Use Swagger UI to test endpoints directly from your browser
- **Type-safe Schemas**: Request/response schemas are derived from the same specs used for validation
- **Always Up-to-date**: Documentation stays in sync with the actual API implementation
- **Separate Request/Response Validation**: Different specs for requests vs responses ensure clients send only what they should

### How It Works

The OpenAPI integration uses a custom metadata-based approach:
1. Handlers are decorated with OpenAPI metadata describing endpoints
2. Clojure specs are automatically converted to OpenAPI schemas
3. The system dynamically generates the OpenAPI specification from routes

**Request vs Response Validation:**
- **Request specs** (`::error-detail-request`) validate client input - only `message` and optional `line` fields allowed
- **Response specs** (`::error-detail-response`) describe API output - includes pattern, lines with values
- **OpenAPI schemas** use response specs to show complete API capabilities
- **Validation** uses request specs to reject invalid client data

This ensures a single source of truth - the same specs used for validation are used for documentation.

### Example Usage

```bash
# Get the OpenAPI specification
curl http://localhost:8081/openapi.json

# Access Swagger UI in your browser
open http://localhost:8081/api-docs
```

For implementation details, see [OPENAPI_INTEGRATION.md](OPENAPI_INTEGRATION.md).

## Error and Warning Consolidation

Pebbles automatically consolidates duplicate error and warning messages to optimize storage and improve readability. Clients should send individual error/warning messages with the `message` field - the backend handles all pattern detection and grouping automatically.

**Client Responsibility:** Send individual messages with the `message` field, like `{"line": 45, "message": "Invalid date format: '13/45/2024'"}`.
**Backend Processing:** Automatically groups similar messages and generates patterns like `"Invalid date format: {QUOTED}"`

### Statistical Pattern Matching

Pebbles now includes intelligent pattern matching that groups similar messages even when they differ in data values. For example:
- `"Invalid account number 123456"` and `"Invalid account number 789012"` → `"Invalid account number {NUMBER}"`
- `"Missing field 'username'"` and `"Missing field 'email'"` → `"Missing field {QUOTED}"`

See [STATISTICAL_PATTERN_MATCHING.md](STATISTICAL_PATTERN_MATCHING.md) for detailed documentation.

**Example Client Input (multiple updates with individual messages):**
```json
// First update - client sends individual messages
{
  "errors": [
    {"line": 10, "message": "Missing required field"},
    {"line": 25, "message": "Invalid format"}
  ]
}

// Second update - client sends more individual messages
{
  "errors": [
    {"line": 30, "message": "Missing required field"},
    {"line": 40, "message": "Missing required field"}
  ]
}
```

**Backend Response (consolidated with patterns):**
```json
{
  "errors": [
    {
      "pattern": "Missing required field",
      "lines": [
        {"line": 10, "values": ["email"]},
        {"line": 30, "values": ["phone"]},
        {"line": 40, "values": ["address"]}
      ]
    },
    {
      "pattern": "Invalid format",
      "lines": [
        {"line": 25, "values": ["bad-data"]}
      ]
    }
  ]
}
```

**Benefits:**
- **Reduced Storage**: Eliminates duplicate error/warning messages
- **Better Overview**: Easily see all line numbers where the same issue occurs
- **Maintained Context**: All line numbers are preserved for debugging
- **Pattern Recognition**: Similar messages with different data values are grouped intelligently

## Common Use Cases

See [use-cases.md](use-cases.md) for detailed examples including:

1. **Clean Processing with Known Total**: Simple workflow with predetermined record count within a client
2. **Complex Processing with Errors**: Mid-process total discovery with error accumulation within a client
3. **Streaming Process**: Dynamic total discovery during processing within a client
4. **Parallel Processing & Authorization**: Multiple users processing different files within the same client
5. **Multi-Client Operations**: How different clients operate in complete isolation

## Configuration

**Environment Variables:**
- `MONGO_URI`: MongoDB connection string (default: `mongodb://localhost:27017/pebbles`)
- `PORT`: HTTP server port (default: `8081`)

**Database:**
- Collection: `progress`
- Indexes: Compound unique on `clientKrn + filename + email`, compound on `clientKrn + email`, single on `clientKrn`

## Web UI

Pebbles includes a modern React-based UI for monitoring progress data with real-time updates.

### UI Features
- **Live Progress Updates**: Real-time updates every second for active file processing
- **Dashboard Overview**: Quick statistics and recent activity view
- **Detailed Progress View**: In-depth progress tracking with error and warning details
- **Filterable List**: Search and filter progress by status, filename, or user
- **Google OAuth**: Secure authentication using Google login
- **Responsive Design**: Works seamlessly on desktop and mobile devices

### Running the UI

1. Navigate to the UI directory:
```bash
cd pebbles-ui
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm start
```

The UI will be available at http://localhost:3000

For more details, see [pebbles-ui/README.md](pebbles-ui/README.md).

## Running the Application

### Backend Development
```bash
clj -M:dev -m pebbles.system
```

### Backend Production
```bash
clj -M -m pebbles.system
```

### Full Stack (Backend + UI)
```bash
# Terminal 1 - Start backend
clj -M:dev -m pebbles.system

# Terminal 2 - Start UI
cd pebbles-ui && npm start
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
│       ├── db.clj        # Database operations (multitenant)
│       ├── http_resp.clj # HTTP response utilities  
│       ├── jwt.clj       # JWT authentication
│       ├── openapi.clj   # OpenAPI spec generation
│       ├── openapi_handlers.clj # OpenAPI/Swagger endpoint handlers
│       ├── spec_openapi.clj # Clojure spec to OpenAPI conversion
│       ├── specs.clj     # Request/response validation (with clientKrn)
│       └── system.clj    # Main system components and handlers
├── test/
│   └── pebbles/
│       ├── db_test.clj
│       ├── http_resp_test.clj
│       ├── jwt_test.clj
│       ├── openapi_test.clj
│       ├── progress_handler_test.clj
│       ├── spec_openapi_test.clj
│       ├── specs_test.clj
│       ├── system_test.clj
│       └── test_utils.clj
├── pebbles-ui/           # React-based UI application
│   ├── public/
│   ├── src/
│   │   ├── components/   # React components
│   │   ├── contexts/     # React contexts
│   │   └── App.js        # Main app with routing
│   ├── package.json
│   └── README.md
├── examples/
│   └── api-usage.md      # API usage examples with clientKrn
├── use-cases.md          # Detailed use cases and workflows for multitenancy
├── OPENAPI_INTEGRATION.md # OpenAPI/Swagger integration details
└── resources/
    └── simplelogger.properties
```

## Key Behaviors

| Feature | Behavior |
|---------|----------|
| **Progress Updates** | Counts are accumulated (added), not replaced |
| **File Ownership** | First user to create progress owns the file within the client |
| **Authorization** | Only file owner can update within same client; anyone with clientKrn can read |
| **Completion** | Once `isLast: true`, progress becomes immutable |
| **Error Tracking** | Errors/warnings accumulate across all updates |
| **Total Discovery** | Total can be set/updated on any request |
| **Data Security** | Server generates all timestamps; client values ignored |
| **Multitenancy** | Complete data isolation between clients via clientKrn |

## Response Codes

| Code | Meaning | When |
|------|---------|------|
| **200** | Success | Successful GET or POST operation |
| **400** | Bad Request | Validation failed, file already completed, or missing clientKrn |
| **401** | Unauthorized | Missing or invalid JWT token (POST only) |
| **403** | Forbidden | Trying to update another user's file (within same tenant) |
| **404** | Not Found | Requested progress record doesn't exist within the client |
| **500** | Server Error | Database or internal server error |

## Design Principles

1. **Functional Style**: Immutable data structures and pure functions where possible
2. **Multitenancy**: Complete data isolation between clients with required clientKrn
3. **Security by Design**: Authentication for updates, authorization for ownership within tenants
4. **Public Transparency**: Anyone can monitor progress with valid clientKrn (no auth required)
5. **Incremental Processing**: Support for distributed/parallel processing workers within clients
6. **Error Visibility**: Comprehensive error and warning tracking with context
7. **Completion Integrity**: Immutable state once processing is marked complete

## CI/CD

This project includes CI/CD configurations for multiple platforms:

- **CircleCI**: `.circleci/config.yml`
- **GitHub Actions**: `.github/workflows/ci.yml`  
- **GitLab CI**: `.gitlab-ci.yml`

See [CI_CD_SETUP.md](CI_CD_SETUP.md) for detailed setup instructions.