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
        "lines": [150],
        "message": "Invalid date format: 2024-13-01"
      },
      {
        "lines": [205],
        "message": "Missing required field: customer_id"
      }
    ],
    "warnings": [
      {
        "lines": [88],
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
      "lines": [150],
      "message": "Invalid date format: 2024-13-01"
    },
    {
      "lines": [205],
      "message": "Missing required field: customer_id"
    }
  ],
  "warnings": [
    {
      "lines": [88],
      "message": "Product code deprecated: PROD-OLD-123"
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
      "lines": [150],
      "message": "Invalid date format: 2024-13-01"
    },
    {
      "lines": [205],
      "message": "Missing required field: customer_id"
    },
    {
      "lines": [312],
      "message": "Duplicate order ID: ORD-12345"
    }
  ],
  "warnings": [
    {
      "lines": [88],
      "message": "Product code deprecated: PROD-OLD-123"
    },
    {
      "lines": [195],
      "message": "Price exceeds normal range: $10,000"
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

## Error and Warning Consolidation Example

### Processing a file with duplicate validation errors
```bash
# First batch with some duplicate errors
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
      {"line": 10, "message": "Invalid email format"},
      {"line": 25, "message": "Missing required field: phone"}
    ],
    "warnings": [
      {"line": 5, "message": "Deprecated field: fax_number"},
      {"line": 15, "message": "Deprecated field: fax_number"},
      {"line": 30, "message": "Large age value: 150"}
    ]
  }'
```

```bash
# Second batch with more duplicate errors
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
      {"line": 45, "message": "Invalid email format"},
      {"line": 60, "message": "Invalid email format"},
      {"line": 75, "message": "Missing required field: phone"}
    ],
    "warnings": [
      {"line": 40, "message": "Deprecated field: fax_number"},
      {"line": 55, "message": "Large age value: 150"}
    ]
  }'
```

Response (showing consolidated errors and warnings):
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
      "lines": [10, 45, 60],
      "message": "Invalid email format"
    },
    {
      "lines": [25, 75],
      "message": "Missing required field: phone"
    }
  ],
  "warnings": [
    {
      "lines": [5, 15, 40],
      "message": "Deprecated field: fax_number"
    },
    {
      "lines": [30, 55],
      "message": "Large age value: 150"
    }
  ]
}
```

**Notice how:**
- 3 "Invalid email format" errors (lines 10, 45, 60) are consolidated into one entry
- 2 "Missing required field: phone" errors (lines 25, 75) are consolidated  
- 3 "Deprecated field: fax_number" warnings (lines 5, 15, 40) are consolidated
- 2 "Large age value: 150" warnings (lines 30, 55) are consolidated
- All line numbers are preserved in the `lines` arrays

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