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

## API Endpoints Overview

| Method | Endpoint | Authentication | Client KRN Required | Purpose |
|--------|----------|----------------|---------------------|---------|
| `POST` | `/progress/:clientKrn` | ✅ Required (JWT) | ✅ Yes (path param) | Create/Update progress within client |
| `GET` | `/progress/:clientKrn` | ❌ Public | ✅ Yes (path param) | View progress data within client |
| `GET` | `/health` | ❌ Public | ❌ No | Health check |

---

## 🔄 Multitenant Workflow

### Authentication & Data Flow
```mermaid
sequenceDiagram
    participant C as Client App
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Provider
    
    Note over C,D: Multitenant File Processing Workflow
    
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

#### Response (201 Created)
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

This multitenant architecture ensures complete data isolation while maintaining familiar API patterns, making it easy for existing clients to adopt the new client KRN requirement.
