# Pebbles SQS Usage Examples

## Prerequisites
- AWS SQS Queue configured with Pebbles SQS Consumer
- Pebbles service running with SQS consumer enabled
- AWS SDK or SQS client library in your processing service

## 1. Basic SQS Message Structure

### Required Fields
Every SQS message must include these fields:
```json
{
  "clientKrn": "krn:clnt:your-company",
  "email": "processing-service@your-company.com",
  "filename": "data-file.csv",
  "counts": {
    "done": 100,
    "warn": 5,
    "failed": 2
  }
}
```

### Optional Fields
```json
{
  "total": 10000,
  "isLast": false,
  "errors": [
    {"line": 45, "message": "Error description"}
  ],
  "warnings": [
    {"line": 12, "message": "Warning description"}
  ]
}
```

## 2. AWS SDK Examples

### Java (AWS SDK v2)
```java
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Arrays;

public class PebblesProgressSender {
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;
    
    public PebblesProgressSender(String queueUrl) {
        this.sqsClient = SqsClient.create();
        this.objectMapper = new ObjectMapper();
        this.queueUrl = queueUrl;
    }
    
    public void sendProgress(ProgressMessage message) throws Exception {
        String messageBody = objectMapper.writeValueAsString(message);
        
        SendMessageRequest request = SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody(messageBody)
            .build();
            
        SendMessageResponse response = sqsClient.sendMessage(request);
        System.out.println("Message sent: " + response.messageId());
    }
}

// Data Classes
class ProgressMessage {
    private String clientKrn;
    private String email;
    private String filename;
    private Counts counts;
    private Integer total;
    private List<ErrorMessage> errors;
    private List<WarningMessage> warnings;
    private Boolean isLast;
    
    // Constructors, getters, and setters
    public ProgressMessage() {}
    
    public ProgressMessage(String clientKrn, String email, String filename, Counts counts) {
        this.clientKrn = clientKrn;
        this.email = email;
        this.filename = filename;
        this.counts = counts;
    }
    
    // ... getters and setters
}

class Counts {
    private int done;
    private int warn;
    private int failed;
    
    public Counts(int done, int warn, int failed) {
        this.done = done;
        this.warn = warn;
        this.failed = failed;
    }
    
    // ... getters and setters
}

class ErrorMessage {
    private int line;
    private String message;
    
    public ErrorMessage(int line, String message) {
        this.line = line;
        this.message = message;
    }
    
    // ... getters and setters
}

class WarningMessage {
    private int line;
    private String message;
    
    public WarningMessage(int line, String message) {
        this.line = line;
        this.message = message;
    }
    
    // ... getters and setters
}

// Usage Example
public static void main(String[] args) {
    PebblesProgressSender sender = new PebblesProgressSender(
        "https://sqs.us-east-1.amazonaws.com/123456789012/pebbles-progress-queue"
    );
    
    ProgressMessage progress = new ProgressMessage(
        "krn:clnt:my-company",
        "data-processor@my-company.com",
        "customer-import.csv",
        new Counts(1000, 5, 2)
    );
    
    progress.setTotal(50000);
    progress.setErrors(Arrays.asList(
        new ErrorMessage(45, "Invalid email format: user@invalid"),
        new ErrorMessage(67, "Missing required field: phone")
    ));
    progress.setWarnings(Arrays.asList(
        new WarningMessage(12, "Deprecated field usage: fax_number")
    ));
    
    try {
        sender.sendProgress(progress);
    } catch (Exception e) {
        System.err.println("Error sending message: " + e.getMessage());
    }
}
```

### Kotlin (AWS SDK v2)
```kotlin
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

class PebblesProgressSender(private val queueUrl: String) {
    private val sqsClient = SqsClient.create()
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    
    fun sendProgress(message: ProgressMessage) {
        try {
            val messageBody = objectMapper.writeValueAsString(message)
            
            val request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build()
                
            val response = sqsClient.sendMessage(request)
            println("Message sent: ${response.messageId()}")
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }
}

// Data Classes
data class ProgressMessage(
    val clientKrn: String,
    val email: String,
    val filename: String,
    val counts: Counts,
    val total: Int? = null,
    val errors: List<ErrorMessage>? = null,
    val warnings: List<WarningMessage>? = null,
    val isLast: Boolean? = null
)

data class Counts(
    val done: Int,
    val warn: Int,
    val failed: Int
)

data class ErrorMessage(
    val line: Int,
    val message: String
)

data class WarningMessage(
    val line: Int,
    val message: String
)

// Usage Example
fun main() {
    val sender = PebblesProgressSender(
        "https://sqs.us-east-1.amazonaws.com/123456789012/pebbles-progress-queue"
    )
    
    val progress = ProgressMessage(
        clientKrn = "krn:clnt:my-company",
        email = "data-processor@my-company.com",
        filename = "customer-import.csv",
        counts = Counts(done = 1000, warn = 5, failed = 2),
        total = 50000,
        errors = listOf(
            ErrorMessage(45, "Invalid email format: user@invalid"),
            ErrorMessage(67, "Missing required field: phone")
        ),
        warnings = listOf(
            WarningMessage(12, "Deprecated field usage: fax_number")
        )
    )
    
    sender.sendProgress(progress)
}
```
