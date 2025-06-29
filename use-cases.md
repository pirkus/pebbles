# 📡 API Documentation & Use Cases

## 🔍 Important: Request vs Response Formats

**CLIENT REQUESTS** (POST): Send individual error/warning messages:
```json
{
  "errors": [
    {"line": 45, "message": "Invalid email format: john@invalid.com"},
    {"line": 67, "message": "Missing required field: phone_number"}
  ]
}
```

**API RESPONSES** (All endpoints): Return pattern-consolidated format:
```json
{
  "errors": [
    {
      "pattern": "Invalid email format: {EMAIL}",
      "lines": [{"line": 45, "values": ["john@invalid.com"]}]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [{"line": 67, "values": ["phone_number"]}]
    }
  ]
}
```

---

## API Endpoints & SQS Overview

### HTTP API Endpoints
| Method | Endpoint | Authentication | Client KRN Required | Purpose |
|--------|----------|----------------|---------------------|---------|
| `POST` | `/progress/:clientKrn` | ✅ Required (JWT) | ✅ Yes (path param) | Create/Update progress within client |
| `GET` | `/progress/:clientKrn` | ❌ Public | ✅ Yes (path param) | View progress data within client |
| `GET` | `/health` | ❌ Public | ❌ No | Health check |

### SQS Integration
| Input Method | Authentication | Client KRN Required | Purpose |
|--------------|----------------|---------------------|---------|
| **SQS Messages** | ❌ Not Required | ✅ Yes (in message payload) | Asynchronous progress updates via message queue |

**Benefits of SQS Integration:**
- 🚀 **Asynchronous Processing**: No need to wait for HTTP responses
- 🛡️ **Reliability**: Built-in retry mechanism and dead letter queues
- 📈 **Scalability**: Handle high-volume progress updates without blocking

---

## 🔄 Multitenant Workflow

### HTTP API Authentication & Data Flow
```mermaid
sequenceDiagram
    participant C as Client App
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Provider
    
    Note over C,D: HTTP API Multitenant Workflow
    
    C->>J: Get JWT Token
    J-->>C: Return JWT
    
    C->>A: POST /progress/krn:clnt:client1 (with JWT)<br/>Initial batch progress
    A->>C: 200 OK - Progress created
    C->>A: GET /progress/krn:clnt:client1?filename=data.csv
    A->>C: 200 OK - Progress data
    C->>A: POST /progress/krn:clnt:client1 (with JWT)<br/>Update with new counts
    A->>C: 200 OK - Progress updated
    C->>A: POST /progress/krn:clnt:client1 (with JWT)<br/>Final batch (isLast: true)
    A->>C: 200 OK - Progress completed
    
    Note over C,A: Client 2 - Different tenant
    C->>A: GET /progress/krn:clnt:client2?filename=data.csv
    A->>C: 404 Not Found (different client)
```

### SQS Asynchronous Processing Flow
```mermaid
sequenceDiagram
    participant P as Processing Service
    participant Q as SQS Queue
    participant A as API Server
    participant D as MongoDB
    participant U as UI/Client
    
    Note over P,U: SQS Asynchronous Multitenant Workflow
    
    P->>Q: Send Progress Message<br/>{"clientKrn": "krn:clnt:client1", "filename": "data.csv", ...}
    Q->>A: SQS Consumer polls message
    A->>D: Store/Update progress data
    A->>Q: Delete message (success)
    
    P->>Q: Send Progress Update<br/>{"clientKrn": "krn:clnt:client1", "counts": {...}, ...}
    Q->>A: SQS Consumer polls message
    A->>D: Update progress data
    A->>Q: Delete message (success)
    
    P->>Q: Send Final Progress<br/>{"clientKrn": "krn:clnt:client1", "isLast": true, ...}
    Q->>A: SQS Consumer polls message
    A->>D: Mark progress completed
    A->>Q: Delete message (success)
    
    Note over U,D: Meanwhile, UI can query progress
    U->>A: GET /progress/krn:clnt:client1?filename=data.csv
    A->>D: Query progress data
    D-->>A: Return progress data
    A-->>U: 200 OK - Progress data
```

---

## 📝 Core Use Cases

### 1. CREATE New Progress (POST /progress/:clientKrn)

#### Request
```http
POST /progress/krn:clnt:my-company
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6...
Content-Type: application/json

{
  "filename": "customer-data.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  },
  "total": 1000,
  "errors": [
    {"line": 15, "message": "Invalid email format in customer record: john@invalid.com"},
    {"line": 42, "message": "Missing required field: phone_number"}
  ],
  "warnings": [
    {"line": 8, "message": "Deprecated field 'fax' still in use"}
  ]
}
```

#### Response (200 OK)
```json
{
  "result": "created",
  "clientKrn": "krn:clnt:my-company",
  "filename": "customer-data.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  },
  "total": 1000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid email format in customer record: {EMAIL}",
      "lines": [
        {"line": 15, "values": ["john@invalid.com"]}
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {"line": 42, "values": ["phone_number"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field {QUOTED} still in use",
      "lines": [
        {"line": 8, "values": ["'fax'"]}
      ]
    }
  ]
}
```

### 2. Pattern Consolidation Example

**Multiple Updates with Similar Errors:**

First batch:
```json
{
  "errors": [
    {"line": 15, "message": "Invalid email format: john@invalid.com"},
    {"line": 42, "message": "Missing phone number"},
    {"line": 78, "message": "Invalid email format: mary@test"}
  ]
}
```

Second batch:
```json
{
  "errors": [
    {"line": 156, "message": "Invalid email format: bob@"},
    {"line": 203, "message": "Missing phone number"}
  ]
}
```

**Consolidated Response:**
```json
{
  "errors": [
    {
      "pattern": "Invalid email format: {EMAIL}",
      "lines": [
        {"line": 15, "values": ["john@invalid.com"]},
        {"line": 78, "values": ["mary@test"]},
        {"line": 156, "values": ["bob@"]}
      ]
    },
    {
      "pattern": "Missing phone number", 
      "lines": [
        {"line": 42},
        {"line": 203}
      ]
    }
  ]
}
```

### 3. Multi-Client Data Isolation

Clients with the same filename are completely isolated:

```bash
# Client A creates progress
curl -X POST http://localhost:8081/progress/krn:clnt:company-a \
  -H "Authorization: Bearer JWT_TOKEN_A" \
  -d '{"filename": "data.csv", "counts": {"done": 100, "warn": 0, "failed": 0}}'

# Client B creates progress for same filename (isolated)  
curl -X POST http://localhost:8081/progress/krn:clnt:company-b \
  -H "Authorization: Bearer JWT_TOKEN_B" \
  -d '{"filename": "data.csv", "counts": {"done": 50, "warn": 5, "failed": 1}}'

# Client A can only see their own data
curl -X GET "http://localhost:8081/progress/krn:clnt:company-a?filename=data.csv"
# Returns Client A's data

# Client B trying to access Client A's data gets 404
curl -X GET "http://localhost:8081/progress/krn:clnt:company-a?filename=data.csv"
# Returns 404 Not Found (different client)
```

---

## 🚨 Error Scenarios

### Missing Client KRN
```bash
# POST without clientKrn
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT" \
  -d '{"filename": "test.csv", "counts": {"done": 1, "warn": 0, "failed": 0}}'
# Response: 400 Bad Request

# GET without clientKrn  
curl -X GET "http://localhost:8081/progress?filename=test.csv"
# Response: 400 Bad Request
```

### Authorization Violations within Client
```bash
# User tries to update another user's file within same client
curl -X POST http://localhost:8081/progress/krn:clnt:company-a \
  -H "Authorization: Bearer WRONG_USER_JWT" \
  -d '{"filename": "alice_file.csv", "counts": {"done": 100, "warn": 0, "failed": 0}}'
# Response: 403 Forbidden
```

---

## 📨 SQS Use Cases

### 1. CREATE New Progress via SQS Message

#### SQS Message Payload
```json
{
  "clientKrn": "krn:clnt:my-company",
  "email": "processing-service@my-company.com",
  "filename": "customer-data.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  },
  "total": 1000,
  "errors": [
    {"line": 15, "message": "Invalid email format in customer record: john@invalid.com"},
    {"line": 42, "message": "Missing required field: phone_number"}
  ],
  "warnings": [
    {"line": 8, "message": "Deprecated field 'fax' still in use"}
  ]
}
```

#### Processing Result (Same as HTTP API)
The SQS consumer processes this message and creates the same progress data as the HTTP API would, with pattern consolidation applied to errors and warnings.

### 2. UPDATE Progress via SQS Message

#### SQS Message Payload (Cumulative Updates)
```json
{
  "clientKrn": "krn:clnt:my-company",
  "email": "processing-service@my-company.com", 
  "filename": "customer-data.csv",
  "counts": {
    "done": 500,
    "warn": 3,
    "failed": 1
  },
  "errors": [
    {"line": 234, "message": "Invalid email format in customer record: mary@invalid.domain"},
    {"line": 456, "message": "Missing required field: address"}
  ]
}
```

**Note**: Counts are **cumulative additions** - the API will add these counts to existing totals, just like the HTTP API.

### 3. COMPLETE Processing via SQS Message

#### Final SQS Message
```json
{
  "clientKrn": "krn:clnt:my-company",
  "email": "processing-service@my-company.com",
  "filename": "customer-data.csv",
  "counts": {
    "done": 400,
    "warn": 2,
    "failed": 0
  },
  "isLast": true
}
```

This marks the processing as completed (`isCompleted: true` in the stored data).

### 4. SQS vs HTTP API Comparison

| Feature | HTTP API | SQS Messages |
|---------|----------|--------------|
| **Authentication** | JWT Required | Not Required |
| **Response** | Immediate HTTP response | Fire-and-forget |
| **Reliability** | Client must handle retries | Built-in SQS retry logic |
| **Throughput** | Limited by HTTP connection pool | High throughput, async processing |
| **Error Handling** | Immediate error response | Failed messages go to DLQ |
| **Use Case** | Interactive UI updates | Batch processing, microservices |

### 5. Multi-Service Architecture with SQS

```mermaid
graph TB
    subgraph "Processing Services"
        PS1[Data Processor 1]
        PS2[ETL Service]
        PS3[Validation Service]
    end
    
    subgraph "AWS SQS"
        Q[Progress Queue]
        DLQ[Dead Letter Queue]
    end
    
    subgraph "Pebbles API"
        SC[SQS Consumer]
        API[HTTP API]
        DB[(MongoDB)]
    end
    
    subgraph "Client Applications"
        UI[Web Dashboard]
        MOB[Mobile App]
    end
    
    PS1 --> Q
    PS2 --> Q
    PS3 --> Q
    Q --> SC
    SC --> DB
    SC -.-> DLQ
    
    UI --> API
    MOB --> API
    API --> DB
    DB --> API
    API --> UI
    API --> MOB
```

**Benefits:**
- **Decoupled Architecture**: Processing services don't need direct API access
- **Fault Tolerance**: Failed messages are retried automatically
- **Scalability**: Handle thousands of progress updates without blocking
