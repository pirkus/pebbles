# Kafka Consumer Usage

This document describes how to use the Kafka consumer for processing progress updates in the Pebbles application.

## Overview

The Kafka consumer provides an alternative to the SQS consumer for processing progress update messages. It implements the same message processing logic but uses Apache Kafka as the messaging system instead of AWS SQS.

## Features

- **Message Processing**: Handles the same progress update messages as the SQS consumer
- **Validation**: Uses the same spec validation for message format
- **Error Handling**: Robust error handling with detailed logging
- **Component Lifecycle**: Proper start/stop lifecycle management
- **Concurrent Processing**: Processes messages concurrently with manual offset commits
- **Integration**: Seamless integration with existing HTTP handlers and database operations

## Configuration

The Kafka consumer is configured via environment variables:

```bash
# Required: Kafka bootstrap servers
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Optional: Topic name (defaults to "progress-updates")
KAFKA_TOPIC=progress-updates

# Optional: Consumer group ID (defaults to "pebbles-progress-consumer")
KAFKA_GROUP_ID=pebbles-progress-consumer
```

## Message Format

Messages should be JSON with the following structure:

```json
{
  "clientKrn": "krn:clnt:example-client",
  "email": "user@example.com",
  "filename": "data.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  },
  "total": 1000,
  "isLast": false,
  "errors": [
    {
      "line": 10,
      "message": "Invalid date format"
    }
  ],
  "warnings": [
    {
      "line": 15,
      "message": "Deprecated field used"
    }
  ]
}
```

### Required Fields
- `clientKrn`: Client identifier in KRN format
- `email`: User email address
- `filename`: Name of the file being processed
- `counts`: Object with `done`, `warn`, and `failed` counts

### Optional Fields
- `total`: Total number of records to process
- `isLast`: Boolean indicating if this is the final update
- `errors`: Array of error objects with line numbers and messages
- `warnings`: Array of warning objects with line numbers and messages

## Usage Examples

### Basic Producer Example (Java)

```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

KafkaProducer<String, String> producer = new KafkaProducer<>(props);

String message = """
{
  "clientKrn": "krn:clnt:my-client",
  "email": "user@example.com",
  "filename": "import.csv",
  "counts": {"done": 50, "warn": 0, "failed": 0},
  "total": 1000
}
""";

ProducerRecord<String, String> record = new ProducerRecord<>("progress-updates", message);
producer.send(record);
producer.close();
```

### Clojure Producer Example

```clojure
(require '[cheshire.core :as json])
(import '(org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
        '(org.apache.kafka.common.serialization StringSerializer))

(defn send-progress-update [bootstrap-servers topic message-data]
  (let [props (doto (java.util.Properties.)
                (.put "bootstrap.servers" bootstrap-servers)
                (.put "key.serializer" (.getName StringSerializer))
                (.put "value.serializer" (.getName StringSerializer)))
        producer (KafkaProducer. props)
        message (json/generate-string message-data)
        record (ProducerRecord. topic nil message)]
    (.send producer record)
    (.close producer)))

;; Usage
(send-progress-update 
  "localhost:9092" 
  "progress-updates"
  {:clientKrn "krn:clnt:my-client"
   :email "user@example.com"
   :filename "data.csv"
   :counts {:done 25 :warn 1 :failed 0}
   :total 500})
```

## Integration with SQS

The Kafka consumer can run alongside the SQS consumer. The system will automatically enable components based on configuration:

- **SQS Consumer**: Enabled when `SQS_QUEUE_URL` is set
- **Kafka Consumer**: Enabled when `KAFKA_BOOTSTRAP_SERVERS` is set
- **Both**: Can run simultaneously if both are configured

Both consumers process messages using the same business logic and store results in the same database.

## Testing

### Unit Tests

Run the Kafka consumer unit tests:

```bash
clojure -M:test -n pebbles.kafka.consumer-test
```

### Integration Tests

Run integration tests with Testcontainers:

```bash
clojure -M:test -n pebbles.kafka.consumer-integration-test -i integration
```

The integration tests will automatically start a Kafka container using Testcontainers. To use an existing Kafka instance instead:

```bash
export USE_EXISTING_KAFKA=true
# Make sure Kafka is running on localhost:9092
```

## Monitoring and Logging

The Kafka consumer provides detailed logging:

- **INFO**: Successful message processing
- **WARN**: Validation failures
- **ERROR**: Parsing errors, processing failures
- **DEBUG**: Message reception details

Log entries include:
- Topic, partition, and offset information
- Client KRN, filename, and email
- Processing results and error details

## Error Handling

The consumer handles various error scenarios:

1. **Invalid JSON**: Logs error and continues processing
2. **Schema Validation**: Validates against specs and rejects invalid messages
3. **Business Logic Errors**: Handles duplicate users, completed files, etc.
4. **Database Errors**: Proper error handling and logging
5. **Connection Issues**: Automatic reconnection and retry logic

## Performance Considerations

- **Manual Commit**: Offsets are committed only after successful processing
- **Batch Processing**: Processes messages in batches for efficiency
- **Error Isolation**: Single message failures don't affect batch processing
- **Graceful Shutdown**: Proper cleanup when stopping the consumer

## Comparison with SQS Consumer

| Feature | Kafka Consumer | SQS Consumer |
|---------|----------------|--------------|
| Message Format | Same JSON format | Same JSON format |
| Processing Logic | Identical | Identical |
| Error Handling | Same approach | Same approach |
| Scalability | High throughput | AWS managed |
| Ordering | Per-partition ordering | No ordering guarantee |
| Durability | Configurable retention | 14 days max |
| Cost | Self-hosted | Pay per message |
| Setup Complexity | Requires Kafka cluster | Uses AWS SQS |

## Best Practices

1. **Partitioning**: Use client KRN as partition key for ordering
2. **Monitoring**: Monitor consumer lag and processing rates
3. **Error Handling**: Implement dead letter queues for failed messages
4. **Scaling**: Scale consumers horizontally by increasing partitions
5. **Testing**: Use Testcontainers for integration testing
6. **Configuration**: Tune consumer properties for your use case

## Troubleshooting

### Consumer Not Starting
- Check `KAFKA_BOOTSTRAP_SERVERS` environment variable
- Verify Kafka cluster is accessible
- Check network connectivity and security groups

### Messages Not Processing
- Verify topic exists and has messages
- Check consumer group offset positions
- Review logs for processing errors

### Performance Issues
- Monitor consumer lag
- Check database connection pool
- Review batch size and commit frequency
- Consider scaling consumers horizontally 