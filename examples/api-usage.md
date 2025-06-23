# Pebbles Multi-Tenant API Usage Examples

## Prerequisites
- Pebbles service running on `http://localhost:8081`
- Valid Google JWT token
- Client KRN (e.g., `krn:acme:client:123`)

## Example: Processing a CSV File for a Client

### 1. Start processing - First update with total
```bash
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
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
  "clientKrn": "krn:acme:client:123",
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
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
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
  "clientKrn": "krn:acme:client:123",
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
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
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
  "clientKrn": "krn:acme:client:123",
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
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "sales_data_2024.csv",
    "counts": {
      "done": 9088,
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
  "clientKrn": "krn:acme:client:123",
  "filename": "sales_data_2024.csv",
  "counts": {
    "done": 9988,
    "warn": 15,
    "failed": 5
  },
  "total": 10000,
  "isCompleted": true
}
```

### 4. Get progress for specific file within client
```bash
curl -X GET "http://localhost:8081/clients/krn:acme:client:123/progress?filename=sales_data_2024.csv"
```

Response:
```json
{
  "id": "507f1f77bcf86cd799439011",
  "clientKrn": "krn:acme:client:123",
  "filename": "sales_data_2024.csv",
  "email": "user@acme.com",
  "counts": {
    "done": 9988,
    "warn": 15,
    "failed": 5
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

### 5. Get all progress for a user within a client
```bash
curl -X GET "http://localhost:8081/clients/krn:acme:client:123/progress?email=user@acme.com"
```

Response:
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:acme:client:123",
    "filename": "sales_data_2024.csv",
    "email": "user@acme.com",
    "counts": {
      "done": 9988,
      "warn": 15,
      "failed": 5
    },
    "total": 10000,
    "isCompleted": true,
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:15:00Z"
  },
  {
    "id": "507f1f77bcf86cd799439012",
    "clientKrn": "krn:acme:client:123",
    "filename": "inventory_update.csv",
    "email": "user@acme.com",
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

### 6. Get all progress for a client
```bash
curl -X GET "http://localhost:8081/clients/krn:acme:client:123/progress"
```

Response:
```json
[
  {
    "id": "507f1f77bcf86cd799439011",
    "clientKrn": "krn:acme:client:123",
    "filename": "sales_data_2024.csv",
    "email": "user@acme.com",
    "counts": {
      "done": 9988,
      "warn": 15,
      "failed": 5
    },
    "total": 10000,
    "isCompleted": true,
    "createdAt": "2024-01-01T10:00:00Z",
    "updatedAt": "2024-01-01T10:15:00Z"
  },
  {
    "id": "507f1f77bcf86cd799439013",
    "clientKrn": "krn:acme:client:123",
    "filename": "customer_import.csv",
    "email": "etl-service@acme.com",
    "counts": {
      "done": 450,
      "warn": 0,
      "failed": 0
    },
    "total": 5000,
    "isCompleted": false,
    "createdAt": "2024-01-01T12:00:00Z",
    "updatedAt": "2024-01-01T12:30:00Z"
  }
]
```

## SQS Integration Examples

### Sending progress update via SQS
```python
import boto3
import json

sqs = boto3.client('sqs', region_name='us-east-1')
queue_url = 'https://sqs.us-east-1.amazonaws.com/123456789/pebbles-progress'

message = {
    "clientKrn": "krn:acme:client:123",
    "email": "worker@acme.com",
    "filename": "large_dataset.csv",
    "counts": {
        "done": 1000,
        "warn": 5,
        "failed": 2
    },
    "total": 100000,
    "errors": [
        {
            "line": 567,
            "message": "Invalid format"
        }
    ]
}

response = sqs.send_message(
    QueueUrl=queue_url,
    MessageBody=json.dumps(message)
)
```

## Kafka Integration Examples

### Sending progress update via Kafka
```python
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

message = {
    "clientKrn": "krn:widgets:client:456",
    "email": "stream-processor@widgets.com",
    "filename": "realtime_data.csv",
    "counts": {
        "done": 5000,
        "warn": 10,
        "failed": 5
    },
    "isLast": False
}

producer.send('progress-updates', value=message)
producer.flush()
```

## Multi-Client Examples

### Different clients processing same filename
```bash
# Client 1: ACME Corp
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Authorization: Bearer ACME_USER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "common_data.csv",
    "counts": {"done": 100, "warn": 0, "failed": 0}
  }'

# Client 2: Widgets Inc (same filename, different client)
curl -X POST http://localhost:8081/clients/krn:widgets:client:456/progress \
  -H "Authorization: Bearer WIDGETS_USER_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "common_data.csv",
    "counts": {"done": 200, "warn": 5, "failed": 1}
  }'

# Each client's progress is isolated
```

## Error Examples

### Attempting to update another user's file
```bash
# User A creates progress
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Authorization: Bearer USER_A_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "protected_file.csv",
    "counts": {"done": 100, "warn": 0, "failed": 0}
  }'

# User B tries to update it
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Authorization: Bearer USER_B_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "protected_file.csv",
    "counts": {"done": 50, "warn": 0, "failed": 0}
  }'
```

Response:
```json
{
  "error": "Only the original creator can update this file's progress"
}
```

### Attempting to update completed progress
```bash
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "completed_file.csv",
    "counts": {"done": 10, "warn": 0, "failed": 0}
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
curl -X POST http://localhost:8081/clients/krn:acme:client:123/progress \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "test.csv",
    "counts": {"done": 10, "warn": 0, "failed": 0}
  }'
```

Response:
```json
{
  "status": 401,
  "body": "Missing Authorization header"
}
```
