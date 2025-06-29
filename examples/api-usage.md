# Pebbles API Usage Examples

## Prerequisites
- Pebbles service running on `http://localhost:8081`
- Valid JWT token for POST operations

## 1. Basic Progress Tracking

### Create Initial Progress
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data-processing.csv",
    "counts": {
      "done": 1000,
      "warn": 5,
      "failed": 2
    },
    "total": 10000,
    "errors": [
      {"line": 45, "message": "Invalid email format: user@invalid"},
      {"line": 67, "message": "Missing required field: phone"}
    ],
    "warnings": [
      {"line": 12, "message": "Deprecated field usage: fax_number"}
    ]
  }'
```

**Response:**
```json
{
  "result": "created",
  "clientKrn": "krn:clnt:my-company",
  "filename": "data-processing.csv",
  "counts": {
    "done": 1000,
    "warn": 5,
    "failed": 2
  },
  "total": 10000,
  "isCompleted": false,
  "errors": [
    {
      "pattern": "Invalid email format: {EMAIL}",
      "lines": [{"line": 45, "values": ["user@invalid"]}]
    },
    {
      "pattern": "Missing required field: {IDENTIFIER}",
      "lines": [{"line": 67, "values": ["phone"]}]
    }
  ],
  "warnings": [
    {
      "pattern": "Deprecated field usage: {IDENTIFIER}",
      "lines": [{"line": 12, "values": ["fax_number"]}]
    }
  ]
}
```

### Update Progress
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data-processing.csv",
    "counts": {
      "done": 1500,
      "warn": 2,
      "failed": 1
    },
    "errors": [
      {"line": 89, "message": "Invalid email format: another@bad"}
    ],
    "warnings": [
      {"line": 34, "message": "Large data value: 99999"}
    ]
  }'
```

## 2. Retrieving Progress Data

### Get Specific File Progress
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company?filename=data-processing.csv"
```

### Get All Progress for Client
```bash  
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company"
```

### Get User-Specific Progress
```bash
curl -X GET "http://localhost:8081/progress/krn:clnt:my-company?email=user@example.com"
```

## 3. Advanced Features

### Streaming Processing (Unknown Total)
```bash
# Start without knowing total count
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "stream-data.csv",
    "counts": {"done": 500, "warn": 0, "failed": 0}
  }'

# Update with discovered total
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "stream-data.csv", 
    "counts": {"done": 1500, "warn": 3, "failed": 1},
    "total": 5000
  }'
```

### Complete Processing
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "data-processing.csv",
    "counts": {"done": 10000, "warn": 15, "failed": 5},
    "isLast": true
  }'
```

## 4. Pattern Matching Examples

### Multiple Similar Errors
When you send similar errors with different values, Pebbles automatically groups them:

**Input:**
```json
{
  "errors": [
    {"line": 10, "message": "Invalid email: john@"},
    {"line": 25, "message": "Invalid email: mary@test"},
    {"line": 40, "message": "Invalid email: @domain.com"}
  ]
}
```

**Consolidated Output:**
```json
{
  "errors": [
    {
      "pattern": "Invalid email: {EMAIL}",
      "lines": [
        {"line": 10, "values": ["john@"]},
        {"line": 25, "values": ["mary@test"]},
        {"line": 40, "values": ["@domain.com"]}
      ]
    }
  ]
}
```

## 5. Multi-Client Isolation

```bash
# Client A creates progress
curl -X POST http://localhost:8081/progress/krn:clnt:company-a \
  -H "Authorization: Bearer JWT_TOKEN_A" \
  -d '{"filename": "data.csv", "counts": {"done": 100, "warn": 0, "failed": 0}}'

# Client B creates progress for same filename (isolated)
curl -X POST http://localhost:8081/progress/krn:clnt:company-b \
  -H "Authorization: Bearer JWT_TOKEN_B" \
  -d '{"filename": "data.csv", "counts": {"done": 50, "warn": 5, "failed": 1}}'

# Client A views their progress (completely separate from Client B)
curl -X GET "http://localhost:8081/progress/krn:clnt:company-a?filename=data.csv"
```

## 6. Utility Endpoints

### Health Check
```bash
curl -X GET http://localhost:8081/health
```

### OpenAPI Documentation
```bash
# Get OpenAPI specification
curl -X GET http://localhost:8081/openapi.json

# Access interactive Swagger UI
open http://localhost:8081/api-docs
```

## 7. Error Scenarios

### Missing Client KRN
```bash
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer JWT_TOKEN" \
  -d '{"filename": "test.csv", "counts": {"done": 1, "warn": 0, "failed": 0}}'
# Response: 400 Bad Request - "clientKrn path parameter is required"
```

### Authorization Failure
```bash
curl -X POST http://localhost:8081/progress/krn:clnt:my-company \
  -H "Authorization: Bearer WRONG_USER_JWT" \
  -d '{"filename": "other_user_file.csv", "counts": {"done": 100, "warn": 0, "failed": 0}}'
# Response: 403 Forbidden
```

## 🚀 ETag Support for Efficient Polling

All GET endpoints support **ETag-based conditional requests** to significantly reduce bandwidth and improve performance when polling frequently. This is especially beneficial for UI applications that poll progress data every 5 seconds.

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

  startPolling(intervalMs = 5000) {
    return setInterval(() => {
      this.fetchProgress().catch(console.error);
    }, intervalMs);
  }
}

// Usage
const poller = new ProgressPoller('krn:clnt:my-company');
const intervalId = poller.startPolling(5000); // Poll every 5 seconds efficiently!
```

### Supported Endpoints

All GET endpoints support ETags:
- `GET /progress/:clientKrn` - All progress for client
- `GET /progress/:clientKrn?filename=X` - Specific file progress
- `GET /progress/:clientKrn?email=X` - User's progress within client