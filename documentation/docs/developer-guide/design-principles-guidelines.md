# Design Principles and Guidelines

## 1. Design Principles

The core design philosophy of idp-server is to **honor the worldview of OIDC**.

### 1.1 Protocol Validity

* Strict compliance with OAuth 2.0 and OIDC specifications to ensure interoperability and clarity
* Deviation from the standards is strictly prohibited (extensions must be encapsulated)

### 1.2 Balance of Extension and Compatibility

* Supports flexible implementation of extended specs such as CIBA, FAPI, and OID4IDA
* Extensions must integrate seamlessly into standard flows

### 1.3 Freedom of Implementation via Abstraction

* Authentication methods and data storage mechanisms are implementation-dependent
* Parts not covered by OIDC (authentication, persistence, notifications, etc.) are designed to be pluggable

---

## 2. Design Guidelines

### 2.1 Class & Responsibility Design

* **Controller**: Handles only request/response formatting
* **UseCase**: Contains the core business logic per use case; depends on DTOs
* **Repository**: Abstracts data access; decouples from DB or external services

### 2.2 Module Structure

* Clear separation between core, use-cases, adapters, and springboot-adapter
* Distinction between protocol layer (OAuth, OIDC) and domain layer

### 2.3 Naming and Type Design

* Use meaningful names based on specifications (e.g., IdToken, Grant, Subject)
* Represent value semantics with meaningful types (e.g., ClientId, AcrValue)

### 2.4 Extension Points

* Use components like AuthenticationInteractor and FederationInteractor to isolate flow branching
* Avoid raw `Map<String, Object>` or JSON; favor strongly-typed extension models

### 2.5 Testing Strategy

* Combine unit tests for UseCases with E2E tests via REST API
* Automate conformance tests to ensure protocol compliance

---

## 3. Coding Rules

* Always wrap `throw` statements with common exception types (e.g., `OAuthBadRequestException`)
* Pass `TenantIdentifier` explicitly; avoid thread-local context
* Standardize JSON conversion via `JsonNodeWrapper` / `JsonConverter`
* Introduce new specs via abstract `Protocol` layers to shield callers from change

---

## 4. Summary

idp-server aims to grow as a sustainable and orderly open source project
by being faithful to the OIDC standard while ensuring extensibility and implementation flexibility.
