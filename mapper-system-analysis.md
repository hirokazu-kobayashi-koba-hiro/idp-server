# idp-server Mapper System Analysis

## Overview

The idp-server platform includes a flexible data mapping system for transforming JSON data using configurable rules and functions. This system is particularly useful for identity claim mapping, attribute transformation, and data integration scenarios.

## Current Architecture

### Core Components

#### 1. Function System (`org.idp.server.platform.mapper.functions`)

**Base Interface: `ValueFunction`**
- Defines contract for transformation functions
- `Object apply(Object input, Map<String, Object> args)` - core transformation method
- `String name()` - unique function identifier

**Function Registry: `FunctionRegistry`**
- Central registry for all available functions
- Auto-registers built-in functions on initialization
- Supports dynamic function registration
- Provides function lookup and existence checking

#### 2. Mapping Rules (`MappingRule`)

**Core Configuration:**
- `from`: JSONPath expression for source data extraction
- `staticValue`: Static value assignment (alternative to `from`)
- `to`: Target field path in output (supports dot notation and "*" expansion)
- `convertType`: Legacy type conversion (deprecated, use `convert_type` function)
- `functions`: Chain of transformation functions to apply

**Key Features:**
- Either `from` (dynamic) or `staticValue` (static) can be specified
- Function chaining for complex transformations
- Flat key notation with automatic nested object composition

#### 3. Processing Engine (`MappingRuleObjectMapper`)

**Execution Flow:**
1. **Base Value Resolution**: Extract from JSONPath or use static value
2. **Function Application**: Apply functions sequentially in chain
3. **Result Writing**: Write to flat map with special "*" expansion handling
4. **Object Composition**: Convert flat map to nested structure

#### 4. Object Composition (`ObjectCompositor`)

**Features:**
- Converts flat dot-notation keys to nested objects
- Automatic array creation for numeric indexes (e.g., `claims.0.type`)
- Supports complex nested structures
- Handles wildcard expansion for map merging

## Current Built-in Functions

### 1. `format` - Template-based string formatting
```java
// Usage: Template replacement with {{value}} placeholder
args: {
  "template": "email:{{value}}"
}
// Input: "user@example.com" → Output: "email:user@example.com"
```

### 2. `random_string` - Secure random string generation
```java
// Usage: Generate cryptographically secure random strings
args: {
  "length": 16,                    // default: 16
  "charset": "ABC123"              // default: alphanumeric
}
// Input: ignored → Output: "A3C1B2A1C3B2A1C3" (example)
```

### 3. `now` - Current timestamp generation
```java
// Usage: Generate current date/time with optional formatting
args: {
  "zone": "Asia/Tokyo",            // default: "UTC"
  "pattern": "yyyy-MM-dd HH:mm:ss" // optional, returns ZonedDateTime if null
}
// Input: ignored → Output: "2025-09-21 10:30:45" or ZonedDateTime object
```

### 4. `exists` - Existence/emptiness checking
```java
// Usage: Check if value exists and is non-empty
// No args required
// Input: null → false
// Input: "" → false
// Input: [] → false
// Input: {} → false
// Input: "value" → true
```

### 5. `convert_type` - Advanced type conversion
```java
// Usage: Comprehensive type conversion with error handling
args: {
  "type": "boolean|string|integer|long|double|datetime",
  "onError": "null|default|throw",     // default: "null"
  "default": <fallback_value>,         // used with onError="default"
  "trim": true|false,                  // default: true
  "locale": "en-US"                    // default: Locale.ROOT
}

// Boolean conversion recognizes: "1"/"true"/"yes"/"y"/"on" → true
//                                "0"/"false"/"no"/"n"/"off" → false
```

## Usage Examples

### Basic Field Mapping
```json
{
  "from": "$.user.email",
  "to": "claims.email"
}
```

### Static Value Assignment
```json
{
  "staticValue": "client_credentials",
  "to": "grant_type"
}
```

### Function Chain Example
```json
{
  "from": "$.user.name",
  "to": "claims.formatted_name",
  "functions": [
    {
      "name": "convert_type",
      "args": {
        "type": "string",
        "trim": true
      }
    },
    {
      "name": "format",
      "args": {
        "template": "Name: {{value}}"
      }
    }
  ]
}
```

### Array and Nested Object Creation
```json
{
  "staticValue": "document",
  "to": "verification.evidence.0.type"
}
// Creates: {"verification": {"evidence": [{"type": "document"}]}}
```

### Wildcard Expansion
```json
{
  "from": "$.metadata",
  "to": "*"
}
// If $.metadata is {"key1": "val1", "key2": "val2"}
// Results in top-level: {"key1": "val1", "key2": "val2"}
```

## Current Limitations and Missing Features

### 1. **Conditional Logic Functions**
**Missing:**
- `if/then/else` conditional processing
- `switch/case` multi-condition logic
- Value-based branching

**Impact:** Cannot handle conditional mappings based on input values

### 2. **Collection Processing Functions**
**Missing:**
- `map` - transform each element in array
- `filter` - filter array elements by criteria
- `reduce` - aggregate array values
- `join` - concatenate array elements
- `split` - split strings into arrays
- `sort` - sort array elements

**Impact:** Limited ability to process arrays and collections

### 3. **String Manipulation Functions**
**Current:** Only basic `format` with template replacement
**Missing:**
- `substring` / `slice` - extract string portions
- `replace` / `regex_replace` - pattern-based replacement
- `upper` / `lower` / `title_case` - case transformations
- `trim` / `pad` - whitespace and padding operations
- `length` - string/array length calculation

### 4. **Mathematical and Numerical Functions**
**Missing:**
- `add` / `subtract` / `multiply` / `divide` - basic arithmetic
- `round` / `ceil` / `floor` - rounding operations
- `abs` / `min` / `max` - numerical comparisons
- `hash` - cryptographic hashing (MD5, SHA256, etc.)

### 5. **Date/Time Manipulation**
**Current:** Only `now` for current timestamp
**Missing:**
- `date_add` / `date_subtract` - date arithmetic
- `date_format` - format existing dates
- `date_parse` - parse dates from strings
- `date_diff` - calculate date differences

### 6. **Validation and Assertion Functions**
**Missing:**
- `validate_email` - email format validation
- `validate_regex` - regex pattern validation
- `assert_not_null` - null value assertions
- `validate_length` - length constraints

### 7. **Advanced JSONPath Operations**
**Current:** Basic JSONPath reading via `from` field
**Missing:**
- `jsonpath_query` function for dynamic path evaluation
- Multiple path extraction in single rule
- Path existence checking
- Complex path expressions with variables

### 8. **Error Handling and Debugging**
**Limitations:**
- Limited error context in function failures
- No debugging/tracing capabilities for rule execution
- Basic logging without detailed transformation tracking

### 9. **Performance Optimizations**
**Missing:**
- Function result caching
- Compiled expression evaluation
- Batch processing capabilities

### 10. **Security and Sanitization**
**Missing:**
- `escape_html` / `escape_sql` - injection prevention
- `sanitize` - general input sanitization
- `mask` / `redact` - PII masking functions

## Recommended Enhancements

### Priority 1: Essential Missing Functions
1. **Conditional Logic**: `if`, `switch` functions
2. **String Operations**: `substring`, `replace`, `upper`, `lower`
3. **Collection Processing**: `map`, `filter`, `join`, `split`
4. **Validation**: `validate_email`, `validate_regex`

### Priority 2: Advanced Features
1. **Mathematical Functions**: Basic arithmetic operations
2. **Date Manipulation**: `date_add`, `date_format`
3. **Security Functions**: `hash`, `mask`, `escape_html`
4. **Advanced JSONPath**: Dynamic path evaluation

### Priority 3: System Improvements
1. **Enhanced Error Handling**: Better error messages and context
2. **Performance**: Function caching and optimization
3. **Debugging**: Transformation tracing and detailed logging
4. **Documentation**: Interactive function reference with examples

## Architecture Strengths

1. **Extensible Design**: Clean function interface for adding new capabilities
2. **Function Chaining**: Powerful composition of transformations
3. **Flexible Input Sources**: Support for both static values and JSONPath extraction
4. **Nested Object Support**: Automatic composition from flat key notation
5. **Error Resilience**: Graceful handling of function failures
6. **Type Safety**: Strong typing with comprehensive type conversion

## Conclusion

The current mapper system provides a solid foundation for basic data transformation needs. However, significant gaps exist in conditional logic, collection processing, and advanced string manipulation that limit its applicability for complex identity claim transformations. The architecture is well-designed for extension, making it feasible to add the missing functionality incrementally.

The priority should be on implementing essential missing functions (conditional logic, string operations, collection processing) to make the system suitable for production identity scenarios, followed by advanced features and performance optimizations.