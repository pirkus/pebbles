# OpenAPI Integration for Pebbles

This document describes how OpenAPI/Swagger documentation is integrated into the Pebbles Pedestal service.

## Overview

Instead of using outdated libraries like `ring-swagger` or `pedestal-api`, we've implemented a custom metadata-based approach that:

1. **Keeps documentation close to code** - OpenAPI specs are defined as metadata on handler functions
2. **Automatic Spec Conversion** - OpenAPI schemas are automatically generated from your existing Clojure specs
3. **Generates specs from routes** - The OpenAPI JSON is automatically generated from your Pedestal routes
4. **Serves interactive documentation** - Swagger UI is available at `/api-docs`

## How It Works

### 1. Handler Metadata

Each handler function includes OpenAPI metadata describing its endpoints:

```clojure
(defn update-progress-handler [db]
  (with-meta
    (fn [request]
      ; ... handler implementation ...
    )
    ;; OpenAPI metadata
    {:post {:summary "Update file processing progress"
            :description "Create or update progress..."
            :tags ["progress"]
            :security [{:bearerAuth []}]
            :requestBody {:required true
                          :content {"application/json"
                                    {:schema (openapi/ref-schema "ProgressUpdate")}}}
            :responses {200 {:description "Progress updated successfully"
                             :content {"application/json"
                                       {:schema (openapi/ref-schema "ProgressResponse")}}}}}}))
```

### 2. Schema Definitions

Common schemas are defined in `src/pebbles/openapi.clj` and can be either:

1. **Manual schemas** - Directly defined OpenAPI schemas
2. **Spec-based schemas** - Automatically generated from Clojure specs using `spec->openapi-schema`

```clojure
(def common-schemas
  {:ProgressUpdate
   (spec->openapi-schema ::specs/progress-update)
   
   :Counts
   (spec->openapi-schema ::specs/counts)
   
   ;; Manual schema example
   :ErrorResponse
   {:type "object"
    :properties {:error {:type "string"}
                 :message {:type "string"}}}})
```

### 3. Automatic Generation

The `generate-openapi-spec` function extracts metadata from routes and generates a complete OpenAPI 3.0 specification:

```clojure
(openapi/generate-openapi-spec routes
  :info {:title "Pebbles API"
         :description "Multitenant file processing progress tracking service"
         :version "1.0.0"})
```

## Spec-to-OpenAPI Schema Generation

One of the key features of our integration is automatic generation of OpenAPI schemas from your existing Clojure specs. This ensures your API documentation stays in sync with your validation logic.

### How It Works

The `pebbles.spec-openapi` namespace provides a multimethod-based converter that translates Clojure spec forms into OpenAPI schemas:

```clojure
;; Your Clojure spec
(s/def ::done (s/and integer? #(>= % 0)))

;; Automatically generates OpenAPI schema
{:type "integer"
 :minimum 0}
```

### Supported Spec Forms

- **Basic predicates**: `string?`, `integer?`, `boolean?`, `number?`, `keyword?`, `symbol?`, `map?`, `coll?`, `sequential?`, `vector?`, `set?`, `any?`
- **Fully qualified predicates**: `clojure.core/string?`, `clojure.core/integer?`, etc.
- **Constraints**: `s/and` with numeric constraints (`>`, `>=`, `<`, `<=`)
- **Objects**: `s/keys` with required (`:req-un`) and optional (`:opt-un`) keys
- **Collections**: `s/coll-of` for arrays with element type specifications
- **Nullable values**: `s/nilable` for optional/nullable fields
- **Alternatives**: `s/or` (generates `oneOf` in OpenAPI)
- **String constraints**: `s/and` with `string?` and count constraints

### Complex Example

Your spec:
```clojure
(s/def ::status (s/nilable #{"processing" "completed" "failed"}))
(s/def ::counts (s/keys :req-un [::done ::warn ::failed]))
(s/def ::progress-update 
  (s/keys :req-un [::filename ::counts]
          :opt-un [::status ::lastUpdate]))
```

Generated OpenAPI schema:
```json
{
  "type": "object",
  "properties": {
    "filename": {"type": "string"},
    "counts": {
      "type": "object",
      "properties": {
        "done": {"type": "integer", "minimum": 0},
        "warn": {"type": "integer", "minimum": 0},
        "failed": {"type": "integer", "minimum": 0}
      },
      "required": ["done", "warn", "failed"]
    },
    "status": {
      "oneOf": [
        {"type": "string", "enum": ["processing", "completed", "failed"]},
        {"type": "null"}
      ]
    },
    "lastUpdate": {"type": "string"}
  },
  "required": ["filename", "counts"]
}
```

### Using Spec-based Schemas

To use a spec-based schema in your OpenAPI documentation:

```clojure
;; In openapi.clj
(require '[pebbles.spec-openapi :as spec-openapi])

;; Convert spec to schema
(def common-schemas
  {:MySchema (spec-openapi/spec->openapi-schema ::specs/my-spec)})

;; Reference in handler metadata
{:requestBody {:content {"application/json" 
                         {:schema (ref-schema "MySchema")}}}}
```

This approach ensures:
- **Single source of truth** - Your specs define both validation and documentation
- **Type safety** - OpenAPI schemas match your runtime validation
- **Less maintenance** - No need to update schemas separately from specs
- **Automatic synchronization** - Changes to specs automatically update API documentation

## Available Endpoints

- **`/openapi.json`** - Returns the OpenAPI specification as JSON
- **`/api-docs`** - Interactive Swagger UI documentation

## Usage

### Viewing Documentation

Start your service and navigate to:
- http://localhost:8081/api-docs - Interactive API documentation
- http://localhost:8081/openapi.json - Raw OpenAPI specification

### Adding New Endpoints

1. Create your handler with OpenAPI metadata:

```clojure
(defn my-handler []
  (with-meta
    (fn [request]
      ; ... implementation ...
    )
    {:get {:summary "My endpoint"
           :tags ["my-tag"]
           :parameters [{:name "param"
                         :in "query"
                         :schema {:type "string"}}]
           :responses {200 {:description "Success"}}}}))
```

2. If using spec-based schemas, ensure your spec is defined and add it to `common-schemas`:

```clojure
;; Define your spec
(s/def ::my-data (s/keys :req-un [::field1 ::field2]))

;; Add to common-schemas
{:MyData (spec-openapi/spec->openapi-schema ::my-data)}
```

## Implementation Details

### Metadata Extraction

The system uses a custom approach to extract metadata from Pedestal interceptors:

1. **Handler Detection** - Identifies handler functions in the interceptor chain
2. **Metadata Preservation** - Uses `with-meta` to attach OpenAPI specs to handler functions
3. **Route Analysis** - Automatically extracts path parameters from route patterns

### Spec Conversion

The spec-to-OpenAPI converter (`spec->openapi`) uses Clojure's multimethod dispatch:

1. **Form Analysis** - Examines the spec form to determine its type
2. **Recursive Conversion** - Handles nested specs and complex forms
3. **Constraint Extraction** - Parses anonymous functions to extract numeric constraints
4. **Type Mapping** - Maps Clojure predicates to OpenAPI types

## Parameter Handling

### Path Parameters vs Query Parameters

The OpenAPI integration handles different types of parameters differently:

1. **Path Parameters** (e.g., `:clientKrn` in `/progress/:clientKrn`)
   - Automatically extracted from the route definition
   - The system detects `:paramName` patterns in routes and converts them to OpenAPI path parameters
   - No manual declaration needed in handler metadata

2. **Query Parameters** (e.g., `?filename=test.csv&email=user@example.com`)
   - Must be manually declared in handler metadata
   - Pedestal routes don't include query parameter definitions
   - Use the `common-parameters` helper for consistency

Example:
```clojure
;; Path parameter :clientKrn is automatically extracted from route
;; Query parameters must be declared in metadata
{:get {:summary "Retrieve progress information"
       :parameters [(openapi/common-parameters :filename)
                    (openapi/common-parameters :email)]}}
```

This ensures all parameters are properly documented in the OpenAPI spec while maintaining consistency with how Pedestal handles routing. 