# 📡 API Documentation & Use Cases

## API Endpoints Overview

| Method | Endpoint | Authentication | Purpose |
|--------|----------|----------------|---------|
| `POST` | `/progress` | ✅ Required (JWT) | Create/Update progress |
| `GET` | `/progress` | ❌ Public | View progress data |
| `GET` | `/health` | ❌ Public | Health check |

---

## 🔄 Use Case Diagrams

### Main Flow Diagram
```mermaid
graph TD
    A[File Processing Client] --> B{Authentication Required?}
    B -->|Yes - POST| C[JWT Token Required]
    B -->|No - GET| D[Public Access]
    
    C --> E[Create/Update Progress]
    E --> F{Progress Exists?}
    F -->|No| G[Create New Progress]
    F -->|Yes| H{Same User?}
    H -->|Yes| I{Completed?}
    H -->|No| J[403 Forbidden]
    I -->|No| K[Update Progress]
    I -->|Yes| L[400 Bad Request]
    
    D --> M[View Progress]
    M --> N{Query Type?}
    N -->|No params| O[All Progress Records]
    N -->|filename param| P[Specific File Progress]
    N -->|email param| Q[User's All Progress]
    
    G --> R[Return Created Response]
    K --> S[Return Updated Response]
    O --> T[Return All Records]
    P --> U[Return File Progress]
    Q --> V[Return User Progress]
```

### File Processing Workflow
```mermaid
sequenceDiagram
    participant C as Client App
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Provider
    
    Note over C,D: File Processing Workflow
    
    C->>J: Get JWT Token
    J-->>C: Return JWT
    
    C->>A: POST /progress (with JWT)<br/>Initial batch progress
    A->>A: Validate JWT & params
    A->>D: Create progress document
    D-->>A: Document created
    A-->>C: 200 Created response
    
    loop Processing batches
        C->>A: POST /progress (with JWT)<br/>Update with new counts
        A->>A: Validate JWT & authorization
        A->>D: Update progress (add counts)
        D-->>A: Document updated
        A-->>C: 200 Updated response
    end
    
    C->>A: POST /progress (with JWT)<br/>Final batch (isLast: true)
    A->>A: Validate JWT & authorization
    A->>D: Update progress (mark completed)
    D-->>A: Document updated
    A-->>C: 200 Updated response (completed)
    
    Note over C,D: Public Progress Viewing
    
    C->>A: GET /progress?filename=data.csv
    A->>D: Find progress by filename
    D-->>A: Return progress document
    A-->>C: 200 Progress data
```

---

## 📝 API Request/Response Examples

### 1. CREATE New Progress (POST /progress)

#### Request
```http
POST /progress
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
    {
      "line": 15,
      "message": "Invalid email format in customer record"
    },
    {
      "line": 42,
      "message": "Missing required field: phone_number"
    }
  ],
  "warnings": [
    {
      "line": 8,
      "message": "Deprecated field 'fax' still in use"
    }
  ]
}
```

#### Response (201 Created)
```json
{
  "result": "created",
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
      "line": 15,
      "message": "Invalid email format in customer record"
    },
    {
      "line": 42,
      "message": "Missing required field: phone_number"
    }
  ],
  "warnings": [
    {
      "line": 8,
      "message": "Deprecated field 'fax' still in use"
    }
  ]
}
```

---

### 2. UPDATE Existing Progress (POST /progress)

#### Request
```http
POST /progress
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
    {
      "line": 156,
      "message": "Duplicate customer ID detected"
    }
  ],
  "warnings": [
    {
      "line": 134,
      "message": "Address format differs from standard"
    }
  ]
}
```

#### Response (200 Updated)
```json
{
  "result": "updated",
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
      "line": 15,
      "message": "Invalid email format in customer record"
    },
    {
      "line": 42,
      "message": "Missing required field: phone_number"
    },
    {
      "line": 156,
      "message": "Duplicate customer ID detected"
    }
  ],
  "warnings": [
    {
      "line": 8,
      "message": "Deprecated field 'fax' still in use"
    },
    {
      "line": 134,
      "message": "Address format differs from standard"
    }
  ]
}
```

---

### 3. COMPLETE Processing (POST /progress)

#### Request
```http
POST /progress
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6...
Content-Type: application/json

{
  "filename": "customer-data.csv",
  "counts": {
    "done": 695,
    "warn": 2,
    "failed": 5
  },
  "isLast": true
}
```

#### Response (200 Completed)
```json
{
  "result": "updated",
  "filename": "customer-data.csv",
  "counts": {
    "done": 995,
    "warn": 10,
    "failed": 8
  },
  "total": 1000,
  "isCompleted": true,
  "errors": [...],
  "warnings": [...]
}
```

---

### 4. GET Specific File Progress (GET /progress)

#### Request
```http
GET /progress?filename=customer-data.csv
```

#### Response (200 OK)
```json
{
  "id": "507f1f77bcf86cd799439011",
  "filename": "customer-data.csv",
  "email": "data-team@company.com",
  "counts": {
    "done": 995,
    "warn": 10,
    "failed": 8
  },
  "total": 1000,
  "isCompleted": true,
  "createdAt": "2024-12-19T14:00:00.000Z",
  "updatedAt": "2024-12-19T17:45:30.789Z",
  "errors": [
    {
      "line": 15,
      "message": "Invalid email format in customer record"
    }
  ],
  "warnings": [
    {
      "line": 8,
      "message": "Deprecated field 'fax' still in use"
    }
  ]
}
```

---

### 5. GET All Progress for User (GET /progress)

#### Request
```http
GET /progress?email=data-team@company.com
```

#### Response (200 OK)
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "filename": "customer-data.csv",
    "email": "data-team@company.com",
    "counts": {"done": 995, "warn": 10, "failed": 8},
    "total": 1000,
    "isCompleted": true,
    "createdAt": "2024-12-19T14:00:00.000Z",
    "updatedAt": "2024-12-19T17:45:30.789Z"
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "filename": "inventory-update.csv",
    "email": "data-team@company.com",
    "counts": {"done": 450, "warn": 2, "failed": 1},
    "total": 500,
    "isCompleted": false,
    "createdAt": "2024-12-19T16:30:00.000Z",
    "updatedAt": "2024-12-19T17:00:15.456Z"
  }
]
```

---

### 6. GET All Progress (No params) (GET /progress)

#### Request
```http
GET /progress
```

#### Response (200 OK)
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "filename": "customer-data.csv",
    "email": "data-team@company.com",
    "counts": {"done": 995, "warn": 10, "failed": 8},
    "isCompleted": true,
    "createdAt": "2024-12-19T14:00:00.000Z",
    "updatedAt": "2024-12-19T17:45:30.789Z"
  },
  {
    "id": "507f1f77bcf86cd799439013",
    "filename": "sales-report.csv",
    "email": "analyst@company.com",
    "counts": {"done": 1200, "warn": 15, "failed": 3},
    "isCompleted": false,
    "createdAt": "2024-12-19T15:00:00.000Z",
    "updatedAt": "2024-12-19T17:30:22.123Z"
  }
]
```

---

## ⚠️ Error Responses

### Authentication Required (401)
```json
{
  "error": "Missing Authorization header"
}
```

### Authorization Failed (403)
```json
{
  "error": "Only the original creator can update this file's progress"
}
```

### Validation Error (400)
```json
{
  "error": "Invalid parameters: counts.done must be >= 0"
}
```

### File Not Found (404)
```json
{
  "error": "Progress not found for this file"
}
```

### Already Completed (400)
```json
{
  "error": "This file processing has already been completed"
}
```

---

## 🎯 Common Use Case Scenarios

```mermaid
graph LR
    A[Data Processing App] --> B[Batch 1<br/>100 records]
    B --> C[POST /progress<br/>counts: done=95, warn=3, failed=2]
    C --> D[Batch 2<br/>200 records]
    D --> E[POST /progress<br/>counts: done=185, warn=10, failed=5]
    E --> F[Batch 3<br/>150 records - Final]
    F --> G[POST /progress<br/>counts: done=145, warn=2, failed=3<br/>isLast: true]
    
    H[Dashboard App] --> I[GET /progress<br/>All files overview]
    I --> J[Display Progress Grid]
    
    K[Monitoring Tool] --> L[GET /progress?filename=data.csv<br/>Specific file status]
    L --> M[Real-time Progress Display]
    
    N[Report Generator] --> O[GET /progress?email=user\@company.com<br/>User's file history]
    O --> P[Generate User Report]
```

## 🔧 Key API Behaviors

| Behavior | Description | Example |
|----------|-------------|---------|
| **Cumulative Counts** | Progress counts are **added** to existing values | `done: 100` + `done: 50` = `done: 150` |
| **Append Errors/Warnings** | New errors/warnings are **appended** to existing arrays | Array grows with each update |
| **Creator Authorization** | Only the original creator can update a file's progress | Based on JWT email |
| **Completion Lock** | Once `isLast: true`, no further updates allowed | Prevents accidental overwrites |
| **Public Read Access** | Anyone can view progress without authentication | Transparency for monitoring |
| **Server Timestamps** | `createdAt`/`updatedAt` always set by server | Client cannot override |

## 📊 Response Codes Summary

| Code | Scenario | When |
|------|----------|------|
| **200** | Success (Update/Get) | Progress updated or retrieved |
| **400** | Bad Request | Invalid data, already completed, etc. |
| **401** | Unauthorized | Missing/invalid JWT token |
| **403** | Forbidden | Wrong user trying to update |
| **404** | Not Found | Progress doesn't exist |
| **500** | Server Error | Database or internal errors |

---

## 📋 Detailed Sequence Diagrams

### Scenario 1: Clean Processing with Total
```mermaid
sequenceDiagram
    participant U1 as Data Processor<br/>(user@company.com)
    participant U2 as Monitor<br/>(public viewer)
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Service
    
    Note over U1,D: Scenario 1: Clean Processing with Total
    
    U1->>J: Request JWT Token
    J-->>U1: eyJhbGciOiJSUzI1NiI...
    
    U1->>A: POST /progress<br/>{filename: "sales-data.csv",<br/>counts: {done: 500, warn: 0, failed: 0},<br/>total: 2000}
    A->>A: Validate JWT & params
    A->>D: Create progress document
    D-->>A: Document created with _id
    A-->>U1: 200 OK<br/>{result: "created", filename: "sales-data.csv",<br/>counts: {done: 500, warn: 0, failed: 0},<br/>total: 2000, isCompleted: false}
    
    Note over U1,D: Processing continues...
    
    U1->>A: POST /progress<br/>{filename: "sales-data.csv",<br/>counts: {done: 750, warn: 2, failed: 1}}
    A->>A: Validate JWT & authorization
    A->>D: Update progress (add counts)
    D-->>A: Document updated
    A-->>U1: 200 OK<br/>{result: "updated", filename: "sales-data.csv",<br/>counts: {done: 1250, warn: 2, failed: 1},<br/>total: 2000, isCompleted: false}
    
    U1->>A: POST /progress<br/>{filename: "sales-data.csv",<br/>counts: {done: 748, warn: 1, failed: 0},<br/>isLast: true}
    A->>A: Validate JWT & authorization
    A->>D: Update progress (mark completed)
    D-->>A: Document updated
    A-->>U1: 200 OK<br/>{result: "updated", filename: "sales-data.csv",<br/>counts: {done: 1998, warn: 3, failed: 1},<br/>total: 2000, isCompleted: true}
    
    Note over U1,D: Monitor checks progress
    
    U2->>A: GET /progress?filename=sales-data.csv
    A->>D: Find progress by filename
    D-->>A: Return complete progress document
    A-->>U2: 200 OK<br/>{id: "...", filename: "sales-data.csv",<br/>email: "user@company.com",<br/>counts: {done: 1998, warn: 3, failed: 1},<br/>total: 2000, isCompleted: true,<br/>createdAt: "2024-12-19T14:00:00Z",<br/>updatedAt: "2024-12-19T14:45:30Z"}
```

### Scenario 2: Complex Processing with Errors & Warnings
```mermaid
sequenceDiagram
    participant U1 as Data Processor<br/>(analyst@company.com)
    participant U2 as Team Lead<br/>(public viewer)
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Service
    
    Note over U1,D: Scenario 2: Complex Processing with Errors & Warnings
    
    U1->>J: Request JWT Token
    J-->>U1: eyJhbGciOiJSUzI1NiI...
    
    U1->>A: POST /progress<br/>{filename: "customer-import.csv",<br/>counts: {done: 150, warn: 5, failed: 3},<br/>errors: [{line: 15, message: "Invalid email format"},<br/>{line: 42, message: "Missing phone number"}],<br/>warnings: [{line: 8, message: "Deprecated field used"}]}
    A->>A: Validate JWT & params
    A->>D: Create progress document with errors/warnings
    D-->>A: Document created
    A-->>U1: 200 OK<br/>{result: "created", filename: "customer-import.csv",<br/>counts: {done: 150, warn: 5, failed: 3},<br/>isCompleted: false,<br/>errors: [2 items], warnings: [1 item]}
    
    Note over U1,D: Second batch with more issues
    
    U1->>A: POST /progress<br/>{filename: "customer-import.csv",<br/>counts: {done: 200, warn: 8, failed: 7},<br/>total: 500,<br/>errors: [{line: 156, message: "Duplicate customer ID"},<br/>{line: 203, message: "Invalid date format"}],<br/>warnings: [{line: 178, message: "Address incomplete"}]}
    A->>A: Validate & authorize
    A->>D: Update progress (append errors/warnings)
    D-->>A: Document updated
    A-->>U1: 200 OK<br/>{result: "updated", filename: "customer-import.csv",<br/>counts: {done: 350, warn: 13, failed: 10},<br/>total: 500, isCompleted: false,<br/>errors: [4 items], warnings: [2 items]}
    
    Note over U1,D: Team Lead monitors progress
    
    U2->>A: GET /progress?filename=customer-import.csv
    A->>D: Find progress by filename
    D-->>A: Return current progress
    A-->>U2: 200 OK<br/>{filename: "customer-import.csv",<br/>counts: {done: 350, warn: 13, failed: 10},<br/>total: 500, isCompleted: false,<br/>errors: [4 detailed errors],<br/>warnings: [2 detailed warnings]}
    
    Note over U1,D: Final batch completion
    
    U1->>A: POST /progress<br/>{filename: "customer-import.csv",<br/>counts: {done: 125, warn: 2, failed: 5},<br/>isLast: true,<br/>errors: [{line: 445, message: "Validation failed"}],<br/>warnings: [{line: 467, message: "Data quality concern"}]}
    A->>A: Validate & authorize
    A->>D: Update progress (final completion)
    D-->>A: Document updated & completed
    A-->>U1: 200 OK<br/>{result: "updated", filename: "customer-import.csv",<br/>counts: {done: 475, warn: 15, failed: 15},<br/>total: 500, isCompleted: true,<br/>errors: [5 total errors], warnings: [3 total warnings]}
    
    Note over U1,D: Final status check
    
    U2->>A: GET /progress?email=analyst@company.com
    A->>D: Find all progress for user
    D-->>A: Return user's completed files
    A-->>U2: 200 OK<br/>[{filename: "customer-import.csv",<br/>email: "analyst@company.com",<br/>counts: {done: 475, warn: 15, failed: 15},<br/>total: 500, isCompleted: true,<br/>createdAt: "2024-12-19T15:00:00Z",<br/>updatedAt: "2024-12-19T15:35:45Z"}]
```

### Scenario 3: Streaming Process (No Total Initially)
```mermaid
sequenceDiagram
    participant U1 as ETL Process<br/>(etl@company.com)
    participant U2 as Dashboard<br/>(public monitor)
    participant A as API Server
    participant D as MongoDB
    participant J as JWT Service
    
    Note over U1,D: Scenario 3: Streaming Process (No Total Initially)
    
    U1->>J: Get JWT Token
    J-->>U1: Bearer token received
    
    U1->>A: POST /progress<br/>{filename: "streaming-data.csv",<br/>counts: {done: 1000, warn: 0, failed: 0}}
    A->>A: Validate JWT
    A->>D: Create progress (no total yet)
    D-->>A: Document created
    A-->>U1: 200 OK<br/>{result: "created",<br/>counts: {done: 1000, warn: 0, failed: 0},<br/>isCompleted: false,<br/>errors: [], warnings: []}
    
    Note over U1,D: Dashboard checks early progress
    
    U2->>A: GET /progress
    A->>D: Get all progress
    D-->>A: Return all progress documents
    A-->>U2: 200 OK<br/>[{filename: "streaming-data.csv",<br/>counts: {done: 1000, warn: 0, failed: 0},<br/>total: null, isCompleted: false}, ...]
    
    Note over U1,D: Continue processing with total discovered
    
    U1->>A: POST /progress<br/>{filename: "streaming-data.csv",<br/>counts: {done: 2500, warn: 3, failed: 2},<br/>total: 5000,<br/>warnings: [{line: 1250, message: "Encoding issue detected"}]}
    A->>A: Validate & authorize
    A->>D: Update with total added
    D-->>A: Document updated
    A-->>U1: 200 OK<br/>{result: "updated",<br/>counts: {done: 3500, warn: 3, failed: 2},<br/>total: 5000, isCompleted: false,<br/>warnings: [1 item]}
    
    Note over U1,D: Error occurs in processing
    
    U1->>A: POST /progress<br/>{filename: "streaming-data.csv",<br/>counts: {done: 800, warn: 12, failed: 8},<br/>errors: [{line: 4200, message: "Connection timeout"},<br/>{line: 4201, message: "Data corruption detected"}],<br/>warnings: [{line: 4150, message: "Performance degraded"}]}
    A->>A: Validate & authorize
    A->>D: Update with errors
    D-->>A: Document updated
    A-->>U1: 200 OK<br/>{result: "updated",<br/>counts: {done: 4300, warn: 15, failed: 10},<br/>total: 5000, isCompleted: false,<br/>errors: [2 items], warnings: [2 items]}
    
    Note over U1,D: Complete processing
    
    U1->>A: POST /progress<br/>{filename: "streaming-data.csv",<br/>counts: {done: 690, warn: 0, failed: 0},<br/>isLast: true}
    A->>A: Validate & authorize
    A->>D: Mark as completed
    D-->>A: Document completed
    A-->>U1: 200 OK<br/>{result: "updated",<br/>counts: {done: 4990, warn: 15, failed: 10},<br/>total: 5000, isCompleted: true,<br/>errors: [2 items], warnings: [2 items]}
    
    Note over U1,D: Final monitoring check
    
    U2->>A: GET /progress?filename=streaming-data.csv
    A->>D: Get final status
    D-->>A: Return completed document
    A-->>U2: 200 OK<br/>{id: "...", filename: "streaming-data.csv",<br/>email: "etl@company.com",<br/>counts: {done: 4990, warn: 15, failed: 10},<br/>total: 5000, isCompleted: true,<br/>createdAt: "2024-12-19T16:00:00Z",<br/>updatedAt: "2024-12-19T16:25:15Z",<br/>errors: [detailed error objects],<br/>warnings: [detailed warning objects]}
```

### Scenario 4: Parallel Processing & Authorization
```mermaid
sequenceDiagram
    participant U1 as User A<br/>(team1@company.com)
    participant U2 as User B<br/>(team2@company.com)
    participant M as Monitor
    participant A as API Server
    participant D as MongoDB
    
    Note over U1,D: Parallel Processing Scenario
    
    par User A processes File 1
        U1->>A: POST /progress<br/>{filename: "batch-1.csv",<br/>counts: {done: 100, warn: 0, failed: 0},<br/>total: 300}
        A->>D: Create progress for batch-1.csv
        A-->>U1: 200 Created
        
        U1->>A: POST /progress<br/>{filename: "batch-1.csv",<br/>counts: {done: 150, warn: 2, failed: 1}}
        A->>D: Update batch-1.csv progress
        A-->>U1: 200 Updated<br/>{counts: {done: 250, warn: 2, failed: 1}}
        
        U1->>A: POST /progress<br/>{filename: "batch-1.csv",<br/>counts: {done: 47, warn: 0, failed: 2},<br/>isLast: true}
        A->>D: Complete batch-1.csv
        A-->>U1: 200 Completed<br/>{counts: {done: 297, warn: 2, failed: 3}}
    
    and User B processes File 2
        U2->>A: POST /progress<br/>{filename: "import-2.csv",<br/>counts: {done: 500, warn: 5, failed: 0}}
        A->>D: Create progress for import-2.csv
        A-->>U2: 200 Created
        
        U2->>A: POST /progress<br/>{filename: "import-2.csv",<br/>counts: {done: 800, warn: 3, failed: 2},<br/>total: 1500}
        A->>D: Update import-2.csv progress
        A-->>U2: 200 Updated<br/>{counts: {done: 1300, warn: 8, failed: 2}}
        
        U2->>A: POST /progress<br/>{filename: "import-2.csv",<br/>counts: {done: 190, warn: 0, failed: 0},<br/>isLast: true}
        A->>D: Complete import-2.csv
        A-->>U2: 200 Completed<br/>{counts: {done: 1490, warn: 8, failed: 2}}
    end
    
    Note over U1,D: Monitor checks all progress
    
    M->>A: GET /progress
    A->>D: Get all progress documents
    D-->>A: Return all completed files
    A-->>M: 200 OK<br/>[<br/>{filename: "batch-1.csv", email: "team1@company.com",<br/>counts: {done: 297, warn: 2, failed: 3}, isCompleted: true},<br/>{filename: "import-2.csv", email: "team2@company.com",<br/>counts: {done: 1490, warn: 8, failed: 2}, isCompleted: true}<br/>]
    
    Note over U1,D: Authorization check - User A tries to modify User B's file
    
    U1->>A: POST /progress<br/>{filename: "import-2.csv",<br/>counts: {done: 10, warn: 0, failed: 0}}
    A->>A: Check authorization (team1 ≠ team2)
    A-->>U1: 403 Forbidden<br/>{error: "Only the original creator can update this file's progress"}
```

## 📊 Sequence Diagram Scenarios Summary

### **Scenario 1: Clean Processing with Total** 🟢
- **File**: `sales-data.csv` (2000 records)
- **Pattern**: Simple, clean processing with known total upfront
- **Outcome**: 1998 successful, 3 warnings, 1 failure
- **Features**: Shows basic create → update → complete workflow

### **Scenario 2: Complex Processing with Errors & Warnings** 🟡
- **File**: `customer-import.csv` (500 records)  
- **Pattern**: Discovers total mid-process, accumulates errors/warnings
- **Outcome**: 475 successful, 15 warnings, 15 failures
- **Features**: Shows error/warning accumulation and public monitoring

### **Scenario 3: Streaming Process** 🔵
- **File**: `streaming-data.csv` (5000 records)
- **Pattern**: No initial total, discovers size during processing
- **Outcome**: 4990 successful, 15 warnings, 10 failures
- **Features**: Shows total addition mid-stream and error handling

### **Scenario 4: Parallel Processing & Authorization** 🟣
- **Files**: `batch-1.csv` & `import-2.csv` (parallel processing)
- **Pattern**: Multiple users processing different files simultaneously
- **Outcome**: Both complete successfully
- **Features**: Shows authorization enforcement and concurrent processing

## 🔑 Key API Behaviors Demonstrated

| Behavior | Shown In | Description |
|----------|----------|-------------|
| **Cumulative Counts** | All scenarios | Counts are added, not replaced |
| **Error/Warning Accumulation** | Scenarios 2 & 3 | Arrays append new items |
| **Total Discovery** | Scenarios 2 & 3 | Can add total after creation |
| **Completion Lock** | All scenarios | `isLast: true` prevents further updates |
| **Public Monitoring** | All scenarios | GET requests work without auth |
| **Authorization** | Scenario 4 | Only creators can update their files |
| **Concurrent Processing** | Scenario 4 | Multiple files processed simultaneously |
