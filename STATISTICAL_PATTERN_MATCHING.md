# Statistical Pattern Matching for Message Grouping

## Overview

Pebbles includes intelligent statistical pattern matching that automatically groups similar validation messages that differ only in their data values. This feature recognizes patterns in error and warning messages without requiring predefined rules.

## How It Works

### 1. Automatic Pattern Recognition
The system automatically identifies variable data in messages:
- Numbers: `123456` → `{NUMBER}`
- Currency: `$1,234.56` → `{AMOUNT}`
- Email addresses: `john@example.com` → `{EMAIL}`
- File names: `document.pdf` → `{FILENAME}`
- Quoted strings: `'username'` → `{QUOTED}`
- And more...

### 2. Line-to-Value Mapping
Each consolidated group preserves the exact mapping between line numbers and extracted values:

```json
{
  "pattern": "Invalid account number {NUMBER}",
  "lines": [
    {"line": 10, "values": ["123456"]},
    {"line": 20, "values": ["789012"]},
    {"line": 40, "values": ["999999"]}
  ]
}
```

### 3. Pattern-Aware Updates
When updating progress, the system:
1. Loads existing patterns from the database
2. Attempts to match new messages against existing patterns
3. Merges matching messages into existing groups
4. Creates new patterns only for unmatched messages

This prevents duplicate patterns and ensures consistent grouping across updates.

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
      ]
    },
    {
      "pattern": "Missing required field {QUOTED}",
      "lines": [
        {"line": 30, "values": ["'username'"]}
      ]
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
      ]
    },
    {
      "pattern": "Missing required field {QUOTED}",
      "lines": [
        {"line": 30, "values": ["'username'"]},
        {"line": 50, "values": ["'email'"]}  // Merged into existing pattern
      ]
    },
    {
      "pattern": "New error type",
      "lines": [
        {"line": 60}  // No values for exact matches
      ]
    }
  ]
}
```

## Supported Pattern Types

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