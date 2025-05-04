# Modules

This document describes the actual module structure and architectural philosophy of the **idp-server** project.

## Modular Design

The idp-server is structured into multiple modules, each with a focused responsibility and minimal external dependencies.

### 🔥🚀 `idp-server-core`

- Defines domain models and core interfaces
- No dependency on frameworks (pure Java)
- Examples:
    - `OAurhrotocol`, `TokenProtocol`, `AuthenticationFlow`, `Grant`, `SecurityEvent`, `Tenant`, etc.

### 🔌 `idp-server-core-adapter`

- Provides SPI-like adapter implementations and infrastructure logic
- Contains pluggable components for HTTP, events, transactions, etc.
- Designed to be reused by any entrypoint (Spring Boot or CLI)

### 🛠 `idp-server-core-supporter`

- Shared utility components for internal use
- Includes JSON handling, HTTP client factories, and conversion utilities
- Lightweight and framework-agnostic

### 🗄 `idp-server-database`

- Database access and repository implementations
- Raw SQL & typed mapping logic
- Supports PostgreSQL / MySQL / Cloud Spanner via dialect abstraction

### 📱 `idp-server-fcm-adapter`

- Firebase Cloud Messaging integration
- Used in CIBA Push mode for user device notifications
- Abstracted via `NotificationSender` interface

### 🌱 `idp-server-springboot-adapter`

- Spring Boot REST API implementation layer
- Bootstraps the full stack via DI
- Delegates to use-case layer and core

### 📦 `idp-server-use-cases`

- Coordination layer for domain flows
- Implements use cases like Authorization, Token issuance, CIBA handling
- Separates orchestration from domain logic

### 🔐 `idp-server-webauthn4j-adapter`

- WebAuthn / Passkey handling using `webauthn4j`
- Provides credential registration and assertion validation
- Cleanly pluggable into authentication flow

---

## 🔄 Communication Between Modules

- `springboot-adapter` calls into `use-cases`
- `use-cases` invokes `core` logic and interfaces
- `core-adapter` + `database` fulfill interfaces with actual implementations
- All flows are **explicitly tenant-aware** and rely on injected context (no static or global state)

---


## 🔧 Philosophy

- ✅ Framework-agnostic: core can run outside of Spring
- ✅ Extensible: add new auth methods, credential issuers, notification channels easily
- ✅ Observable: emits structured `SecurityEvent`s with hook integration
- ✅ Self-contained: all modules are deployable as OSS components

---

👉 Next: [Authentication Flow](./authentication-flow.md) for detailed login and MFA handling.
