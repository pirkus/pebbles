# Migration Guide: Single-Tenant to Multi-Tenant

This guide helps you migrate from the single-tenant version of Pebbles to the multi-tenant version.

## Overview of Changes

### API Changes
- **Old**: `/progress`
- **New**: `/clients/{clientKrn}/progress`

### Database Schema Changes
- Added `clientKrn` field to all progress documents
- Updated indexes to include clientKrn

### New Features
- SQS queue consumer support
- Kafka topic consumer support
- Complete client data isolation

## Migration Steps

### 1. Database Migration

Run this MongoDB script to add clientKrn to existing documents:

```javascript
// Connect to your MongoDB instance
use pebbles;

// Add default clientKrn to existing documents
db.progress.updateMany(
  { clientKrn: { $exists: false } },
  { $set: { clientKrn: "krn:default:client:001" } }
);

// Drop old indexes
db.progress.dropIndex({ filename: 1, email: 1 });
db.progress.dropIndex({ email: 1 });

// Create new multi-tenant indexes
db.progress.createIndex(
  { clientKrn: 1, filename: 1, email: 1 }, 
  { unique: true }
);
db.progress.createIndex({ clientKrn: 1, email: 1 });
db.progress.createIndex({ clientKrn: 1 });

// Verify migration
print("Documents without clientKrn: " + 
  db.progress.countDocuments({ clientKrn: { $exists: false } })
);
```

### 2. Application Code Updates

Update your client applications to use the new API endpoints:

#### Before:
```python
# Old API calls
response = requests.post(
    "http://pebbles:8081/progress",
    headers={"Authorization": f"Bearer {token}"},
    json={
        "filename": "data.csv",
        "counts": {"done": 100, "warn": 0, "failed": 0}
    }
)

# Get progress
response = requests.get(
    "http://pebbles:8081/progress?filename=data.csv"
)
```

#### After:
```python
# New API calls with clientKrn
CLIENT_KRN = "krn:mycompany:client:123"

response = requests.post(
    f"http://pebbles:8081/clients/{CLIENT_KRN}/progress",
    headers={"Authorization": f"Bearer {token}"},
    json={
        "filename": "data.csv",
        "counts": {"done": 100, "warn": 0, "failed": 0}
    }
)

# Get progress
response = requests.get(
    f"http://pebbles:8081/clients/{CLIENT_KRN}/progress?filename=data.csv"
)
```

### 3. Environment Configuration

Update your environment variables:

```bash
# Existing configuration
MONGO_URI=mongodb://localhost:27017/pebbles
PORT=8081

# Optional: Enable SQS integration
SQS_ENABLED=true
SQS_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/123456789/my-queue
AWS_REGION=us-east-1

# Optional: Enable Kafka integration
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC_NAME=progress-updates
KAFKA_GROUP_ID=pebbles-consumer
```

### 4. Testing the Migration

1. **Test existing progress retrieval**:
   ```bash
   curl http://localhost:8081/clients/krn:default:client:001/progress
   ```

2. **Test new progress creation**:
   ```bash
   curl -X POST http://localhost:8081/clients/krn:mycompany:client:123/progress \
     -H "Authorization: Bearer YOUR_JWT" \
     -H "Content-Type: application/json" \
     -d '{"filename": "test.csv", "counts": {"done": 1, "warn": 0, "failed": 0}}'
   ```

## Rollback Plan

If you need to rollback:

1. **Database rollback**:
   ```javascript
   // Remove clientKrn field
   db.progress.updateMany({}, { $unset: { clientKrn: "" } });
   
   // Restore old indexes
   db.progress.dropIndex({ clientKrn: 1, filename: 1, email: 1 });
   db.progress.dropIndex({ clientKrn: 1, email: 1 });
   db.progress.dropIndex({ clientKrn: 1 });
   
   db.progress.createIndex({ filename: 1, email: 1 }, { unique: true });
   db.progress.createIndex({ email: 1 });
   ```

2. **Deploy previous version** of the application

## Client KRN Best Practices

### Format Recommendations
- Use a hierarchical format: `krn:{company}:{type}:{id}`
- Examples:
  - `krn:acme:client:prod-001`
  - `krn:widgets:client:staging`
  - `krn:startup:client:dev`

### Assignment Strategy
1. **By Environment**: Different KRNs for dev/staging/prod
2. **By Department**: Different KRNs per business unit
3. **By Customer**: SaaS providers can use customer-specific KRNs

## Gradual Migration Strategy

For large deployments, consider a gradual migration:

### Phase 1: Dual Support (1-2 weeks)
1. Deploy new version with both old and new endpoints
2. Add middleware to map old routes to new routes with default clientKrn
3. Monitor usage and update clients gradually

### Phase 2: Deprecation (2-4 weeks)
1. Add deprecation headers to old endpoints
2. Log warnings for old endpoint usage
3. Notify all clients about deprecation

### Phase 3: Removal
1. Remove old endpoint support
2. Clean up migration code

## Monitoring the Migration

### Key Metrics to Watch
- Error rates on new endpoints
- Number of requests to old vs new endpoints
- Database query performance with new indexes
- SQS/Kafka consumer lag (if enabled)

### Useful Queries

```javascript
// Count progress by clientKrn
db.progress.aggregate([
  { $group: { _id: "$clientKrn", count: { $sum: 1 } } },
  { $sort: { count: -1 } }
]);

// Find recent updates per client
db.progress.aggregate([
  { $sort: { updatedAt: -1 } },
  { $group: { 
    _id: "$clientKrn", 
    lastUpdate: { $first: "$updatedAt" },
    fileCount: { $sum: 1 }
  }}
]);
```

## Support

If you encounter issues during migration:

1. Check the application logs for detailed error messages
2. Verify MongoDB connectivity and permissions
3. Ensure all environment variables are properly set
4. Test with a single client KRN before migrating all data

## FAQ

**Q: Can I use my existing JWT tokens?**
A: Yes, the JWT authentication mechanism remains the same.

**Q: What happens to progress without clientKrn?**
A: The migration script assigns them to a default client. You can reassign them later.

**Q: Can different clients see each other's data?**
A: No, complete isolation is enforced at the API and database level.

**Q: Is the SQS/Kafka integration required?**
A: No, these are optional features. The HTTP API works independently.