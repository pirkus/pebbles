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

## 🔄 Use Case Diagrams

### Multitenant Flow Diagram
```mermaid
graph TD
    A[File Processing Client] --> B{Client KRN Provided?}
    B -->|No| Z[400 Bad Request]
    B -->|Yes| C{Authentication Required?}
    C -->|Yes - POST| D[JWT Token Required]
    C -->|No - GET| E[Public Access with clientKrn]
    
    D --> F[Create/Update Progress]
    F --> G{Progress Exists in Client?}
    G -->|No| H[Create New Progress]
    G -->|Yes| I{Same User & Client?}
    I -->|No| J[403 Forbidden]
    I -->|Yes| K{Completed?}
    K -->|No| L[Update Progress]
    K -->|Yes| M[400 Bad Request]
    
    E --> N[View Progress within Client]
    N --> O{Query Type?}
    O -->|No params| P[All Progress Records for Client]
    O -->|filename param| Q[Specific File Progress in Client]
    O -->|email param| R[User's All Progress in Client]
    
    H --> S[Return Created Response]
    L --> T[Return Updated Response]
    P --> U[Return Client Records]
    Q --> V[Return File Progress]
    R --> W[Return User Progress]
```

### Multitenant File Processing Workflow
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

### Multi-Client Data Isolation
```mermaid
graph LR
    subgraph "Client A (krn:clnt:company-a)"
        A1[file1.csv - userA]
        A2[file2.csv - userB]
        A3[data.csv - userA]
    end
    
    subgraph "Client B (krn:clnt:company-b)"
        B1[file1.csv - userC]
        B2[file3.csv - userD]
        B3[data.csv - userC]
    end
    
    subgraph "Client C (krn:clnt:company-c)"
        C1[file4.csv - userE]
        C2[data.csv - userF]
    end
    
    A1 -.->|Complete Isolation| B1
    A3 -.->|Same filename, different client| B3
    A3 -.->|Same filename, different client| C2
    
    style A1 fill:#e1f5fe
    style A2 fill:#e1f5fe
    style A3 fill:#e1f5fe
    style B1 fill:#f3e5f5
    style B2 fill:#f3e5f5
    style B3 fill:#f3e5f5
    style C1 fill:#e8f5e8
    style C2 fill:#e8f5e8
```

---

## 📝 API Request/Response Examples

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

---

### 2. UPDATE Existing Progress (POST /progress/:clientKrn)

#### Request
```http
POST /progress/krn:clnt:my-company
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6...
Content-Type: application/json

{
  "filename": "customer-data.csv",
  "counts": {
    "done": 200,
    "warn": 3,
    "failed": 1
  },
  "errors": [
    {"line": 156, "message": "Duplicate customer ID detected: CUST-12345"}
  ],
  "warnings": [
    {"line": 134, "message": "Address format differs from standard"}
  ]
}
```

#### Response (200 Updated)
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:my-company",
  "filename": "customer-data.csv",
  "counts": {
    "done": 300,
    "warn": 8,
    "failed": 3
  },
  "total": 1000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid email format in customer record: {EMAIL}",
      "lines": [
        {"line": 15, "values": ["john@invalid"]}
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {"line": 42, "values": ["phone_number"]}
      ]
    },
    {
      "pattern": "Duplicate customer ID detected: {ID}",
      "lines": [
        {"line": 156, "values": ["CUST-12345"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field {QUOTED} still in use",
      "lines": [
        {"line": 8, "values": ["'fax'"]}
      ]
    },
    {
      "pattern": "Address format differs from standard",
      "lines": [
        {"line": 134, "values": []}
      ]
    }
  ]
}
```

---

### 3. GET Progress by Client + Filename
```http
GET /progress/krn:clnt:my-company?filename=customer-data.csv
```

#### Response (200 OK)
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientKrn": "krn:clnt:my-company",
  "filename": "customer-data.csv",
  "email": "user@company.com",
  "counts": {
    "done": 300,
    "warn": 8,
    "failed": 3
  },
  "total": 1000,
  "isCompleted": false,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:15:00Z",
  "errors": [
    {
      "pattern": "Invalid email format in customer record: {EMAIL}",
      "lines": [
        {"line": 15, "values": ["john@invalid"]}
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {"line": 42, "values": ["phone_number"]}
      ]
    },
    {
      "pattern": "Duplicate customer ID detected: {ID}",
      "lines": [
        {"line": 156, "values": ["CUST-12345"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field {QUOTED} still in use",
      "lines": [
        {"line": 8, "values": ["'fax'"]}
      ]
    },
    {
      "pattern": "Address format differs from standard",
      "lines": [
        {"line": 134, "values": []}
      ]
    }
  ]
}
```

---

### 4. GET All Progress for Client
```http
GET /progress/krn:clnt:my-company
```

#### Response (200 OK)
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "customer-data.csv",
    "email": "user@company.com",
    "counts": { "done": 300, "warn": 8, "failed": 3 },
    "total": 1000,
    "isCompleted": false,
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:15:00Z",
    "errors": [...],
    "warnings": [...]
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "clientKrn": "krn:clnt:my-company",
    "filename": "sales-data.csv",
    "email": "admin@company.com",
    "counts": { "done": 5000, "warn": 0, "failed": 0 },
    "total": 5000,
    "isCompleted": true,
    "createdAt": "2024-01-15T09:00:00Z",
    "updatedAt": "2024-01-15T09:30:00Z",
    "errors": [],
    "warnings": []
  }
]
```

---

### 5. GET User Progress within Client
```http
GET /progress/krn:clnt:my-company?email=user@company.com
```

#### Response (200 OK)
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "customer-data.csv",
    "email": "user@company.com",
    "counts": { "done": 300, "warn": 8, "failed": 3 },
    "total": 1000,
    "isCompleted": false,
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-15T10:15:00Z",
    "errors": [...],
    "warnings": [...]
  }
]
```

---

## 🎯 Detailed Use Cases

### Use Case 1: Simple Multitenant Processing

**Scenario**: Company A processes a sales file while Company B processes a different file

**Company A Workflow:**
```bash
# Company A starts processing
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_A" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "sales_q1_2024.csv",
    "counts": {"done": 1000, "warn": 0, "failed": 0},
    "total": 10000
  }'

# Company A updates progress
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_A" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "sales_q1_2024.csv",
    "counts": {"done": 2000, "warn": 5, "failed": 2}
  }'

# Company A checks their progress
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-a&filename=sales_q1_2024.csv"
```

**Company B Workflow (Parallel, Isolated):**
```bash
# Company B starts processing (same filename, different client)
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_B" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-b",
    "filename": "sales_q1_2024.csv",
    "counts": {"done": 500, "warn": 1, "failed": 0},
    "total": 5000
  }'

# Company B checks their progress (completely separate from Company A)
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-b&filename=sales_q1_2024.csv"
```

**Key Points:**
- Same filename, different clients = completely isolated
- Each company only sees their own data
- No cross-contamination between tenants

---

### Use Case 2: Error Handling and Authorization

**Scenario**: Demonstrating access control within and across clients

```bash
# User Alice in Company A creates progress
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer ALICE_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "confidential_data.csv",
    "counts": {"done": 100, "warn": 0, "failed": 0}
  }'

# User Bob in Company A tries to update Alice's file (FORBIDDEN)
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer BOB_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "confidential_data.csv",
    "counts": {"done": 50, "warn": 0, "failed": 0}
  }'
# Response: 403 Forbidden

# User Carol in Company B tries to access Company A's file (NOT FOUND)
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-b&filename=confidential_data.csv"
# Response: 404 Not Found

# Missing clientKrn (BAD REQUEST)
curl -X GET "http://localhost:8081/progress?filename=confidential_data.csv"
# Response: 400 Bad Request
```

---

### Use Case 3: Streaming Processing with Multiple Clients

**Scenario**: Two companies process large files with unknown totals

**Company A - Discovers total during processing:**
```bash
# Start without total
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_A" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "large_dataset_a.csv",
    "counts": {"done": 1000, "warn": 10, "failed": 5}
  }'

# Add total when discovered
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_A" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "large_dataset_a.csv",
    "counts": {"done": 2500, "warn": 15, "failed": 8},
    "total": 50000
  }'
```

**Company B - Parallel processing:**
```bash
# Different client, different approach
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_B" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-b",
    "filename": "large_dataset_b.csv",
    "counts": {"done": 5000, "warn": 0, "failed": 0},
    "total": 100000
  }'
```

---

### Use Case 4: Multi-User Team Processing within Client

**Scenario**: Team at Company A collaborates on different files

```bash
# Team Lead starts file A
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer LEAD_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "project_alpha.csv",
    "counts": {"done": 1000, "warn": 0, "failed": 0}
  }'

# Developer 1 starts file B
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer DEV1_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "project_beta.csv",
    "counts": {"done": 500, "warn": 2, "failed": 1}
  }'

# Manager views all team progress within their client
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-a"

# Manager views specific team member's work
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-a&email=dev1@company-a.com"
```

---

## 🔄 Error and Warning Consolidation

### Use Case 5: Automatic Message Consolidation

**Scenario**: Processing a file with many duplicate validation errors

Pebbles automatically consolidates duplicate error and warning messages, grouping them by message content and combining their line numbers into arrays. This reduces storage overhead and improves error analysis.

**Example Processing Workflow:**
```bash
# First batch - initial duplicate errors
curl -X POST http://localhost:8081/progress/krn:clnt:data-validator \
  -H "Authorization: Bearer JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "customer_records.csv",
    "counts": {"done": 2000, "warn": 4, "failed": 3},
    "total": 10000,
    "errors": [
      {"line": 15, "message": "Invalid email format"},
      {"line": 42, "message": "Missing phone number"},
      {"line": 78, "message": "Invalid email format"}
    ],
    "warnings": [
      {"line": 8, "message": "Deprecated field: fax"},
      {"line": 23, "message": "Deprecated field: fax"},
      {"line": 67, "message": "Large age value"},
      {"line": 89, "message": "Deprecated field: fax"}
    ]
  }'
```

```bash
# Second batch - more duplicates
curl -X POST http://localhost:8081/progress/krn:clnt:data-validator \
  -H "Authorization: Bearer JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "customer_records.csv",
    "counts": {"done": 1500, "warn": 2, "failed": 2},
    "errors": [
      {"line": 156, "message": "Invalid email format"},
      {"line": 203, "message": "Missing phone number"}
    ],
    "warnings": [
      {"line": 134, "message": "Deprecated field: fax"},
      {"line": 178, "message": "Large age value"}
    ]
  }'
```

**Response (Consolidated):**
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:data-validator",
  "filename": "customer_records.csv",
  "counts": {
    "done": 3500,
    "warn": 6,
    "failed": 5
  },
  "total": 10000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid email format",
      "lines": [
        {"line": 15, "values": ["john@invalid.com"]},
        {"line": 78, "values": ["mary@test"]},
        {"line": 156, "values": ["bob@"]}
      ]
    },
    {
      "pattern": "Missing phone number", 
      "lines": [
        {"line": 42, "values": []},
        {"line": 203, "values": []}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field: fax",
      "lines": [
        {"line": 8, "values": []},
        {"line": 23, "values": []},
        {"line": 89, "values": []},
        {"line": 134, "values": []}
      ]
    },
    {
      "pattern": "Large age value",
      "lines": [
        {"line": 67, "values": ["125"]},
        {"line": 178, "values": ["999"]}
      ]
    }
  ]
}
```

**Consolidation Benefits:**
- **Storage Efficiency**: 7 original messages reduced to 4 consolidated entries
- **Pattern Recognition**: Easily identify the most common validation issues
- **Line Preservation**: All original line numbers maintained for debugging
- **Automatic Processing**: No client-side logic required
- **Better Analytics**: Simplified error reporting and analysis

**Input Summary:**
- 5 total error instances → 2 consolidated error types
- 6 total warning instances → 2 consolidated warning types
- All 11 line numbers preserved in appropriate `lines` arrays

---

## 🚨 Error Scenarios

### Missing Client KRN
```bash
# POST without clientKrn
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT" \
  -H "Content-Type: application/json" \
  -d '{"filename": "test.csv", "counts": {"done": 1, "warn": 0, "failed": 0}}'
# Response: 400 Bad Request

# GET without clientKrn
curl -X GET "http://localhost:8081/progress?filename=test.csv"
# Response: 400 Bad Request
```

### Cross-Client Access Attempts
```bash
# Try to access Company A's data with Company B's clientKrn
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:company-b&filename=company_a_file.csv"
# Response: 404 Not Found (complete isolation)
```

### Authorization Violations within Client
```bash
# User tries to update another user's file within same client
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer WRONG_USER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "clientKrn": "krn:clnt:company-a",
    "filename": "alice_file.csv",
    "counts": {"done": 100, "warn": 0, "failed": 0}
  }'
# Response: 403 Forbidden
```

---

## 🏗️ Database Schema

### Progress Document Structure
```json
{
  "_id": "ObjectId(...)",
  "clientKrn": "krn:clnt:company-a",
  "filename": "sales_data.csv",
  "email": "user@company-a.com",
  "counts": {
    "done": 1500,
    "warn": 25,
    "failed": 8
  },
  "total": 2000,
  "isCompleted": false,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:15:00Z",
  "errors": [
    {
      "pattern": "Invalid format",
      "lines": [
        {"line": 123, "values": ["bad-value"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field", 
      "lines": [
        {"line": 456, "values": ["old_field"]}
      ]
    }
  ]
}
```

### Database Indexes (Multitenant)
```javascript
// Unique constraint: one progress per client-file-user combination
db.progress.createIndex(
  { "clientKrn": 1, "filename": 1, "email": 1 }, 
  { unique: true }
)

// Fast lookups by client + email
db.progress.createIndex(
  { "clientKrn": 1, "email": 1 }, 
  { name: "progress_client_email_idx" }
)

// Fast lookups by client only
db.progress.createIndex(
  { "clientKrn": 1 }, 
  { name: "progress_client_idx" }
)
```

---

## 🎯 Key Benefits of Multitenancy

1. **Complete Data Isolation**: Each client's data is completely separate
2. **Shared Infrastructure**: All clients use the same service instance
3. **Scalable Architecture**: Add new clients without code changes
4. **Familiar Filenames**: Multiple clients can use the same filenames
5. **User Authorization**: Authorization works within client boundaries
6. **Simplified Monitoring**: Each client can monitor only their own data

## 📊 Monitoring & Analytics

### Client-Specific Dashboards
```bash
# Get all active processing for a client
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:my-company"

# Get processing stats for a specific user within client
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:my-company&email=analyst@my-company.com"

# Monitor specific high-priority file within client
curl -X GET "http://localhost:8081/progress?clientKrn=krn:clnt:my-company&filename=critical_data.csv"
```

---

This multitenant architecture ensures complete data isolation while maintaining the familiar API patterns, making it easy for existing clients to adopt the new client KRN requirement.
