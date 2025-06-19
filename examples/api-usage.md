# Pebbles API Usage Examples

## Prerequisites
- Pebbles service running on `http://localhost:8081`
- Valid Google JWT token

## Example: Processing a CSV File

### 1. Start processing - First update with total
```bash
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjFiYjc3NGJkODcyOWVhMzhlOWMyZmUwYzY0ZDJjYTk0OGJmNjZmMGYiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI5NjM2MTIxNjA1Ny1mMmJiZHZtb21vNmhxYnQ1c2VkbWxnYmVldWQ4ZmVnNy5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsImF1ZCI6Ijk2MzYxMjE2MDU3LWYyYmJkdm1vbW82aHFidDVzZWRtbGdiZWV1ZDhmZWc3LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwic3ViIjoiMTE3Njk3MTk1NzkxNDk4MjM3MDEwIiwiZW1haWwiOiJwaXJrdXNAZ21haWwuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5iZiI6MTc1MDM0MTk3NiwibmFtZSI6IkZpbGlwIExvdmljaCIsInBpY3R1cmUiOiJodHRwczovL2xoMy5nb29nbGV1c2VyY29udGVudC5jb20vYS9BQ2c4b2NKZXg1YmpIcTBUN1lfblM1SXJwTEduWXQ1c1ZJNWtJNFlFdmV4RE9fb2FLQ3dZNFlZRD1zOTYtYyIsImdpdmVuX25hbWUiOiJGaWxpcCIsImZhbWlseV9uYW1lIjoiTG92aWNoIiwiaWF0IjoxNzUwMzQyMjc2LCJleHAiOjE3NTAzNDU4NzYsImp0aSI6IjFhNDFjMWM3ZTY4N2FlMTIwZDdlYWVmYTAzZmNhMTY3MjRmMGM1OWMifQ.zLqWXfHz2m4a7wIkwYOmdbdGLaSAJgvDV6d7nuzO8eZju55srqqMrWKjKUdBZAMHdDx9kZSjJMa2bHIvGfOzk0FD_ZLjtuCL1OB5B2kS0Zks8u2KhKcLBxLFAfnb0MelP02xjAg8N1QusirMDftwcWG5Sqrs91a8OioBZGj351qmmDb3qPPEdevSTtldAG5RUsdAZVanEFon3n0sWCMcX0mjG4NFT5V2suVD579y_SKRgtFuESDqV1pVNJj2_1RW7yaH9x-uZ5s-P43_6psxoX0HV6RWn2BtPhFD6lbCB7m-d3PjkYVTEwQPkbsEFDt7Y6kPOXREC1u-fyXoBoCqOQ" \
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
curl -X POST http://localhost:8081/progress \
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
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 300,
      "warn": 5,
      "failed": 3
    },
    "errors": [
      {
        "line": 150,
        "message": "Invalid date format: 2024-13-01"
      },
      {
        "line": 205,
        "message": "Missing required field: customer_id"
      },
      {
        "line": 312,
        "message": "Duplicate order ID: ORD-12345"
      }
    ],
    "warnings": [
      {
        "line": 88,
        "message": "Product code deprecated: PROD-OLD-123"
      },
      {
        "line": 195,
        "message": "Price exceeds normal range: $10,000"
      }
    ]
  }'
```

Response:
```json
{
  "result": "updated",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 900,
    "warn": 15,
    "failed": 5
  },
  "total": 10000,
  "isCompleted": false,
  "errors": [
    {
      "line": 150,
      "message": "Invalid date format: 2024-13-01"
    },
    {
      "line": 205,
      "message": "Missing required field: customer_id"
    },
    {
      "line": 312,
      "message": "Duplicate order ID: ORD-12345"
    }
  ],
  "warnings": [
    {
      "line": 88,
      "message": "Product code deprecated: PROD-OLD-123"
    },
    {
      "line": 195,
      "message": "Price exceeds normal range: $10,000"
    }
  ]
}
```

### 3. Complete processing - Mark as finished
```bash
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 9388,
      "warn": 0,
      "failed": 0
    },
    "isLast": true
  }'
```

Response:
```json
{
  "result": "updated",
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

### 4. Get progress for specific file
```bash
curl -X GET "http://localhost:8081/progress?filename=sales_data_2024.csv" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Response:
```json
{
  "id": "507f1f77bcf86cd799439011",
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
  "updatedAt": "2024-01-01T10:15:00Z",
  "errors": [
    {
      "line": 150,
      "message": "Invalid date format: 2024-13-01"
    },
    {
      "line": 205,
      "message": "Missing required field: customer_id"
    },
    {
      "line": 312,
      "message": "Duplicate order ID: ORD-12345"
    },
    {
      "line": 4521,
      "message": "Invalid product code: NULL"
    },
    {
      "line": 7892,
      "message": "Negative quantity: -5"
    }
  ],
  "warnings": [
    {
      "line": 88,
      "message": "Product code deprecated: PROD-OLD-123"
    },
    {
      "line": 195,
      "message": "Price exceeds normal range: $10,000"
    },
    {
      "line": 3421,
      "message": "Customer address incomplete"
    }
  ]
}
```

### 5. Get all progress for user
```bash
curl -X GET http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Response:
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
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
    "updatedAt": "2024-01-01T10:15:00Z"
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "filename": "inventory_update.csv",
    "email": "user@example.com",
    "counts": {
      "done": 450,
      "warn": 0,
      "failed": 0
    },
    "total": 500,
    "isCompleted": false,
    "createdAt": "2024-01-01T11:00:00Z",
    "updatedAt": "2024-01-01T11:05:00Z"
  }
]
```

## Error Examples

### Attempting to update completed progress
```bash
curl -X POST http://localhost:8081/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 10,
      "warn": 0,
      "failed": 0
    }
  }'
```

Response:
```json
{
  "error": "This file processing has already been completed"
}
```

### Missing authorization
```bash
curl -X GET http://localhost:8081/progress
```

Response:
```json
{
  "status": 401,
  "body": "Missing Authorization header"
}
```