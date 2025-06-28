# Statistical Pattern Matching for Message Grouping

## Overview

Pebbles includes intelligent statistical pattern matching that automatically groups similar validation messages that differ only in their data values. This feature recognizes patterns in error and warning messages without requiring predefined rules.

## Key Features

### 1. Line-to-Value Mapping
Each consolidated group preserves the exact mapping between line numbers and extracted values:

```json
{
  "pattern": "Invalid account number {NUMBER}",
  "lines": [
    {"line": 10, "values": ["123456"]},
    {"line": 20, "values": ["789012"]},
    {"line": 40, "values": ["999999"]}
  ],
  "message-count": 3
}
```

This ensures you know exactly which data appeared on which line.

### 2. Pattern-Aware Updates
When updating progress, the system:
1. Loads existing patterns from the database
2. Attempts to match new messages against existing patterns
3. Merges matching messages into existing groups
4. Creates new patterns only for unmatched messages

This prevents duplicate patterns and ensures consistent grouping across updates.

### 3. Statistical Pattern Discovery
The system automatically identifies variable data through:
- Token variability analysis across similar messages
- Heuristic detection of common data types (numbers, emails, paths, etc.)
- Similarity scoring using stable (non-variable) tokens

## How It Works

### 1. Tokenization
Messages are split into meaningful tokens while preserving quoted strings and common patterns:
- `"Invalid account number 123456"` → `["Invalid", "account", "number", "123456"]`
- `"Field 'username' is required"` → `["Field", "'username'", "is", "required"]`

### 2. Pattern Recognition
The system automatically identifies variable data in messages:
- Numbers: `123456` → `{NUMBER}`
- Currency: `$1,234.56` → `{AMOUNT}`
- Email addresses: `john@example.com` → `{EMAIL}`
- File names: `document.pdf` → `{FILENAME}`
- Quoted strings: `'username'` → `{QUOTED}`
- And more...

### 3. Similarity Calculation
Messages are grouped based on structural similarity:
- Default threshold: 0.7 (70% similarity)
- Considers both exact matches and pattern matches
- Groups messages with the same structure but different data values

### 4. Consolidation with Pattern Reuse
When new errors/warnings are added:
- Existing patterns are checked first
- Matching messages are merged into existing groups
- New patterns are created only for unmatched messages

## Examples

### Initial Update
```json
{
  "errors": [
    {"line": 10, "message": "Invalid account number 123456"},
    {"line": 20, "message": "Invalid account number 789012"},
    {"line": 30, "message": "Missing required field 'username'"}
  ]
}
```

### Result After First Update
```json
{
  "errors": [
    {
      "pattern": "Invalid account number {NUMBER}",
      "lines": [
        {"line": 10, "values": ["123456"]},
        {"line": 20, "values": ["789012"]}
      ],
      "message-count": 2
    },
    {
      "pattern": "Missing required field {QUOTED}",
      "lines": [
        {"line": 30, "values": ["'username'"]}
      ],
      "message-count": 1
    }
  ]
}
```

### Second Update
```json
{
  "errors": [
    {"line": 40, "message": "Invalid account number 999999"},
    {"line": 50, "message": "Missing required field 'email'"},
    {"line": 60, "message": "New error type"}
  ]
}
```

### Result After Second Update
```json
{
  "errors": [
    {
      "pattern": "Invalid account number {NUMBER}",
      "lines": [
        {"line": 10, "values": ["123456"]},
        {"line": 20, "values": ["789012"]},
        {"line": 40, "values": ["999999"]}  // Merged into existing pattern
      ],
      "message-count": 3
    },
    {
      "pattern": "Missing required field {QUOTED}",
      "lines": [
        {"line": 30, "values": ["'username'"]},
        {"line": 50, "values": ["'email'"]}  // Merged into existing pattern
      ],
      "message-count": 2
    },
    {
      "pattern": "New error type",
      "lines": [
        {"line": 60}  // No values for exact matches
      ],
      "message-count": 1
    }
  ]
}
```

## Benefits

1. **Intelligent Grouping**: Automatically recognizes and groups similar messages without manual configuration
2. **Pattern Reuse**: Updates intelligently merge with existing patterns instead of creating duplicates
3. **Complete Traceability**: Every line number and its associated data values are preserved
4. **Reduced Storage**: Similar messages share the same pattern, reducing redundancy
5. **Better Analysis**: Easily identify the most common error patterns and their variations

## Configuration

### Using Pattern Matching (Default)
```clojure
;; Pattern matching is enabled by default
(db/prepare-progress-data progress-data)

;; Or explicitly enable it
(db/prepare-progress-data progress-data true)
```

### Using Exact Matching (Legacy)
```clojure
;; Disable pattern matching for exact-match-only behavior
(db/prepare-progress-data progress-data false)
```

### Custom Similarity Threshold
```clojure
;; Adjust the similarity threshold (default is 0.7)
(sg/group-similar-messages messages {:threshold 0.8}) ; Stricter grouping
(sg/group-similar-messages messages {:threshold 0.6}) ; Looser grouping
```

## Technical Details

### Supported Pattern Types

| Pattern | Example | Placeholder |
|---------|---------|-------------|
| Numbers | `123`, `456789` | `{NUMBER}` |
| Numbers with units | `15MB`, `30GB` | `{NUMBER}` |
| Currency | `$1,234.56` | `{AMOUNT}` |
| Percentages | `95%` | `{PERCENT}` |
| Email addresses | `user@example.com` | `{EMAIL}` |
| File paths | `/var/log/app.log` | `{PATH}` |
| File names | `document.pdf` | `{FILENAME}` |
| Quoted strings | `'value'`, `"value"` | `{QUOTED}` |
| Parenthetical info | `(size: 15MB)` | `{INFO}` |
| Time durations | `30s`, `5m`, `2h` | `{DURATION}` |

### Algorithm

1. **Tokenization**: Messages are split on whitespace while preserving quoted strings
2. **Variability Analysis**: Token positions are analyzed across similar messages to identify which positions contain variable data
3. **Pattern Extraction**: Variable tokens are replaced with placeholders to create patterns
4. **Similarity Scoring**: Messages are compared using token similarity, considering both exact matches and normalized tokens
5. **Pattern Matching**: New messages are first matched against existing patterns before creating new groups
6. **Consolidation**: Messages are grouped by pattern, preserving line-to-value mappings

## Performance Considerations

- Pattern matching adds minimal overhead during message consolidation
- The algorithm scales linearly with the number of unique message patterns
- Pattern reuse reduces the growth of stored patterns over time
- For very large datasets, consider adjusting the similarity threshold for optimal grouping