# Pebbles API Usage Examples

## Prerequisites
- Pebbles service running on `http://localhost:8081`
- Valid Google JWT token
- Client KRN (e.g., `krn:clnt:my-company`)

## Example: Processing a CSV File

### 1. Start processing - First update with total
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 100,
      "warn": 0,
      "failed": 0
    },
    "total": 10000
  }'
```

Response:
```json
{
  "result": "created",
  "clientKrn": "krn:clnt:my-company",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 100,
    "warn": 0,
    "failed": 0
  },
  "total": 10000,
  "isCompleted": false
}
```

### 2. Update progress - Incremental updates
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 500,
      "warn": 10,
      "failed": 2
    }
  }'
```

Response:
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:my-company",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 600,
    "warn": 10,
    "failed": 2
  },
  "total": 10000,
  "isCompleted": false,
  "errors": [],
  "warnings": []
}
```

### 2b. Update progress with errors and warnings
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 1000,
      "warn": 5,
      "failed": 1
    },
    "errors": [
      {
        "line": 150,
        "message": "Invalid date format: 2024-13-01"
      },
      {
        "line": 205,
        "message": "Missing required field: customer_id"
      }
    ],
    "warnings": [
      {
        "line": 88,
        "message": "Product code deprecated: PROD-OLD-123"
      }
    ]
  }'
```

Response:
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:my-company",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 1600,
    "warn": 15,
    "failed": 3
  },
  "total": 10000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid date format: {DATE}",
      "lines": [
        {
          "line": 150,
          "values": ["2024-13-01"]
        }
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {
          "line": 205,
          "values": ["customer_id"]
        }
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Product code deprecated: {CODE}",
      "lines": [
        {
          "line": 88,
          "values": ["PROD-OLD-123"]
        }
      ]
    }
  ]
}
```

### 3. Complete processing
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 8388,
      "warn": 5,
      "failed": 0
    },
    "isLast": true
  }'
```

Response:
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:my-company",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 9988,
    "warn": 10,
    "failed": 2
  },
  "total": 10000,
  "isCompleted": true
}
```

### 4. Get progress for specific file within client
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company?filename=sales_data_2024.csv"
```

Response:
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientKrn": "krn:clnt:my-company",
  "filename": "sales_data_2024.csv",
  "email": "user@example.com",
  "counts": {
    "done": 9988,
    "warn": 10,
    "failed": 2
  },
  "total": 10000,
  "isCompleted": true,
  "createdAt": "2024-01-01T10:00:00Z",
  "updatedAt": "2024-01-01T10:30:00Z",
  "errors": [
    {
      "pattern": "Invalid date format: {DATE}",
      "lines": [
        {
          "line": 150,
          "values": ["2024-13-01"]
        }
      ]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [
        {
          "line": 205,
          "values": ["customer_id"]
        }
      ]
    },
    {
      "pattern": "Duplicate order ID: {ID}",
      "lines": [
        {
          "line": 312,
          "values": ["ORD-12345"]
        }
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Product code deprecated: {CODE}",
      "lines": [
        {
          "line": 88,
          "values": ["PROD-OLD-123"]
        }
      ]
    },
    {
      "pattern": "Price exceeds normal range: {AMOUNT}",
      "lines": [
        {
          "line": 195,
          "values": ["$10,000"]
        }
      ]
    }
  ]
}
```

### 5. Get all progress for specific user within client
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company?email=user@example.com"
```

Response:
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "sales_data_2024.csv",
    "email": "user@example.com",
    "counts": {
      "done": 9988,
      "warn": 10,
      "failed": 2
    },
    "total": 10000,
    "isCompleted": true,
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:30:00Z",
    "errors": [...],
    "warnings": [...]
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "clientKrn": "krn:clnt:my-company",
    "filename": "customer_data_2024.csv",
    "email": "user@example.com",
    "counts": {
      "done": 2500,
      "warn": 50,
      "failed": 25
    },
    "total": 3000,
    "isCompleted": false,
    "createdAt": "2024-01-01T11:00:00Z",
    "updatedAt": "2024-01-01T11:15:00Z",
    "errors": [...],
    "warnings": [...]
  }
]
```

### 6. Get all progress for client
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company"
```

Response:
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "sales_data_2024.csv",
    "email": "user@example.com",
    "counts": {
      "done": 9988,
      "warn": 10,
      "failed": 2
    },
    "total": 10000,
    "isCompleted": true,
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:30:00Z",
    "errors": [...],
    "warnings": [...]
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "clientKrn": "krn:clnt:my-company",
    "filename": "customer_data_2024.csv",
    "email": "user@example.com",
    "counts": {
      "done": 2500,
      "warn": 50,
      "failed": 25
    },
    "total": 3000,
    "isCompleted": false,
    "createdAt": "2024-01-01T11:00:00Z",
    "updatedAt": "2024-01-01T11:15:00Z",
    "errors": [...],
    "warnings": [...]
  },
  {
    "id": "507f1f77bcf86cd799439013",
    "clientKrn": "krn:clnt:my-company",
    "filename": "inventory_data_2024.csv",
    "email": "admin@example.com",
    "counts": {
      "done": 5000,
      "warn": 0,
      "failed": 0
    },
    "total": 5000,
    "isCompleted": true,
    "createdAt": "2024-01-01T09:00:00Z",
    "updatedAt": "2024-01-01T09:45:00Z",
    "errors": [],
    "warnings": []
  }
]
```

## Error and Warning Pattern Consolidation Example

### Processing a file with similar validation errors
```bash
# First batch with validation errors
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "user_registrations.csv",
    "counts": {
      "done": 1000,
      "warn": 3,
      "failed": 2
    },
    "total": 5000,
    "errors": [
      {"line": 10, "message": "Invalid email format: john@"},
      {"line": 25, "message": "Missing required field 'phone'"}
    ],
    "warnings": [
      {"line": 5, "message": "Account 12345 will expire in 30 days"},
      {"line": 15, "message": "Account 67890 will expire in 30 days"},
      {"line": 30, "message": "Transaction amount $1,500.00 exceeds limit"}
    ]
  }'
```

```bash
# Second batch with more similar errors
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "user_registrations.csv",
    "counts": {
      "done": 1500,
      "warn": 2,
      "failed": 3
    },
    "errors": [
      {"line": 45, "message": "Invalid email format: @example.com"},
      {"line": 60, "message": "Invalid email format: jane@test"},
      {"line": 75, "message": "Missing required field 'email'"}
    ],
    "warnings": [
      {"line": 40, "message": "Account 99999 will expire in 15 days"},
      {"line": 55, "message": "Transaction amount $2,300.00 exceeds limit"}
    ]
  }'
```

Response (showing pattern-based consolidation):
```json
{
  "result": "updated",
  "clientKrn": "krn:clnt:my-company",
  "filename": "user_registrations.csv",
  "counts": {
    "done": 2500,
    "warn": 5,
    "failed": 5
  },
  "total": 5000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid email format: {EMAIL}",
      "lines": [
        {"line": 10, "values": ["john@"]},
        {"line": 45, "values": ["@example.com"]},
        {"line": 60, "values": ["jane@test"]}
      ]
    },
    {
      "pattern": "Missing required field {QUOTED}",
      "lines": [
        {"line": 25, "values": ["'phone'"]},
        {"line": 75, "values": ["'email'"]}
      ]
    }
  ],
  "warnings": [
    {
      "pattern": "Account {NUMBER} will expire in {NUMBER} days",
      "lines": [
        {"line": 5, "values": ["12345", "30"]},
        {"line": 15, "values": ["67890", "30"]},
        {"line": 40, "values": ["99999", "15"]}
      ]
    },
    {
      "pattern": "Transaction amount {AMOUNT} exceeds limit",
      "lines": [
        {"line": 30, "values": ["$1,500.00"]},
        {"line": 55, "values": ["$2,300.00"]}
      ]
    }
  ]
}
```

**Notice how:**
- Similar "Invalid email format" errors are grouped under one pattern with {EMAIL} placeholder
- "Missing required field" errors are grouped with {QUOTED} placeholder
- Account expiry warnings are grouped with {NUMBER} placeholders
- Transaction warnings are grouped with {AMOUNT} placeholder
- Each line number is preserved with its specific extracted values
- The system automatically detects patterns without manual configuration

## Pattern Types Recognized

The statistical pattern matching automatically recognizes and groups:

- **Numbers**: `123`, `456789` → `{NUMBER}`
- **Currency**: `$1,234.56` → `{AMOUNT}`
- **Percentages**: `95%` → `{PERCENT}`
- **Email addresses**: `user@example.com` → `{EMAIL}`
- **File paths**: `/var/log/app.log` → `{PATH}`
- **File names**: `document.pdf` → `{FILENAME}`
- **Quoted strings**: `'value'`, `"value"` → `{QUOTED}`
- **Durations**: `30s`, `5m`, `2h` → `{DURATION}`

## Error Examples

### 1. Missing clientKrn in POST request
```bash
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 100,
      "warn": 0,
      "failed": 0
    }
  }'
```

Response (400 Bad Request):
```json
{
  "error": "clientKrn path parameter is required"
}
```

### 2. Missing clientKrn in GET request
```bash
curl -X GET "http://localhost:8081/progress?filename=sales_data_2024.csv"
```

Response (400 Bad Request):
```json
{
  "error": "clientKrn path parameter is required"
}
```

### 3. Trying to access progress with wrong client KRN
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:different-company?filename=sales_data_2024.csv"
```

Response (404 Not Found):
```json
{
  "error": "Progress not found for this file"
}
```

## Multi-Client Example

### Client A operations
```bash
# Client A creates progress
curl -X POST http://localhost:8081/progress/krn:clnt:company-a \
  -H "Authorization: Bearer JWT_TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data.csv",
    "counts": {"done": 100, "warn": 0, "failed": 0}
  }'

# Client A views their progress
curl -X GET "http://localhost:8081/progress/krn:clnt:company-a?filename=data.csv"
```

### Client B operations (same filename, different client)
```bash
# Client B creates progress for same filename (isolated)
curl -X POST http://localhost:8081/progress/krn:clnt:company-b \
  -H "Authorization: Bearer JWT_TOKEN_B" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data.csv",
    "counts": {"done": 50, "warn": 5, "failed": 1}
  }'

# Client B views their progress (completely separate from Client A)
curl -X GET "http://localhost:8081/progress/krn:clnt:company-b?filename=data.csv"
```

Both clients can have files with the same name but they are completely isolated from each other!

## Utility Endpoints

### Health Check
```bash
curl -X GET http://localhost:8081/health
```

Response:
```
OK
```

### OpenAPI Documentation
```bash
# Get the complete OpenAPI 3.0 specification
curl -X GET http://localhost:8081/openapi.json

# Access interactive Swagger UI (open in browser)
open http://localhost:8081/api-docs
```

### All Available Endpoints Summary

| Method | Endpoint | Purpose | Authentication |
|--------|----------|---------|---------------|
| `POST` | `/progress/:clientKrn` | Create/update progress | ✅ JWT Required |
| `GET` | `/progress/:clientKrn` | Retrieve progress | ❌ Public |
| `GET` | `/health` | Health check | ❌ Public |
| `GET` | `/openapi.json` | OpenAPI specification | ❌ Public |
| `GET` | `/api-docs` | Interactive API docs | ❌ Public |

## 🚀 ETag Support for Efficient Polling

All GET endpoints support **ETag-based conditional requests** to significantly reduce bandwidth and improve performance when polling frequently. This is especially beneficial for UI applications that poll progress data every 1-5 seconds.

### How ETags Work

1. **First Request**: Server returns data with an `ETag` header containing a hash of the response data
2. **Subsequent Requests**: Client sends `If-None-Match` header with the stored ETag value
3. **Data Unchanged**: Server returns `304 Not Modified` with empty body (saves bandwidth)
4. **Data Changed**: Server returns `200 OK` with new data + new ETag

### Example Usage

#### First Request (Gets Initial Data + ETag)
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company" \
  -H "Accept: application/json"
```

**Response:**
```
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
ETag: "a1b2c3d4e5f6789012345678901234567"
Cache-Control: no-cache
Content-Length: 1250

[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "sales-data.csv",
    ...
  }
]
```

#### Subsequent Request (Using ETag)
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company" \
  -H "Accept: application/json" \
  -H "If-None-Match: \"a1b2c3d4e5f6789012345678901234567\""
```

**Response (Data Unchanged):**
```
HTTP/1.1 304 Not Modified
ETag: "a1b2c3d4e5f6789012345678901234567"
Cache-Control: no-cache
Content-Length: 0

(empty body - saves bandwidth!)
```

**Response (Data Changed):**
```
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
ETag: "x9y8z7w6v5u4321098765432109876543"
Cache-Control: no-cache
Content-Length: 1350

[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:clnt:my-company",
    "filename": "sales-data.csv",
    "counts": { "done": 2000, "warn": 0, "failed": 0 }, // <- Updated!
    ...
  }
]
```

### JavaScript/Frontend Implementation

```javascript
class ProgressPoller {
  constructor(clientKrn) {
    this.clientKrn = clientKrn;
    this.etag = null;
    this.data = null;
  }

  async fetchProgress() {
    const headers = { 'Accept': 'application/json' };
    if (this.etag) {
      headers['If-None-Match'] = this.etag;
    }

    const response = await fetch(`/progress/${this.clientKrn}`, { headers });

    if (response.status === 304) {
      // Data unchanged, use cached data
      console.log('Data unchanged, using cache');
      return this.data;
    }

    if (response.status === 200) {
      // Data changed or first request
      this.etag = response.headers.get('ETag');
      this.data = await response.json();
      console.log('Data updated, new ETag:', this.etag);
      return this.data;
    }

    throw new Error(`Unexpected response: ${response.status}`);
  }

  startPolling(intervalMs = 1000) {
    return setInterval(() => {
      this.fetchProgress().catch(console.error);
    }, intervalMs);
  }
}

// Usage
const poller = new ProgressPoller('krn:clnt:my-company');
const intervalId = poller.startPolling(1000); // Poll every second efficiently!
```

### Benefits

- **Bandwidth Savings**: 304 responses have no body, saving significant bandwidth
- **Reduced Server Load**: Server can quickly return 304 without re-serializing data
- **Better Performance**: Clients avoid unnecessary JSON parsing when data is unchanged
- **Perfect for Polling**: Ideal for UI dashboards that update frequently

### Supported Endpoints

All GET endpoints support ETags:
- `GET /progress/:clientKrn` - All progress for client
- `GET /progress/:clientKrn?filename=X` - Specific file progress
- `GET /progress/:clientKrn?email=X` - User's progress within client