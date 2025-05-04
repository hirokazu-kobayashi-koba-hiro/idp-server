# Features

The **idp-server** is built to be an extensible and standards-compliant identity provider.

## Supported Protocols

- **OAuth 2.0 (RFC 6749)**
    - Authorization Code Grant
    - Implicit Grant
    - Client Credentials Grant
    - Resource Owner Password Credentials Grant

- **OpenID Connect (OIDC)**
    - Authorization Code Flow
    - Implicit Flow
    - Hybrid Flow
    - Request Object (signed, encrypted, or plain)
    - UserInfo Endpoint

- **Financial-grade API (FAPI)**
    - Part 1: Baseline
    - Part 2: Advanced (JARM, PAR)

- **Client-Initiated Backchannel Authentication (CIBA)**
    - Poll / Ping / Push modes
    - Request Object support
    - Device Notification (e.g., FCM/APNs)

## Authentication Features

- Multi-Factor Authentication (MFA)
    - Passkey (WebAuthn)
    - Email code
- Federation
    - OpenID Connect
    - SAML (via plugin)
- Device-based login flow (CIBA)
- User account lifecycle (register, suspend, delete)

## Authorization Features

- Consent Management
    - Per client & per scope
    - Terms of Service versioning
- Authorization Policy
    - Per-tenant / client / user + acr_values + scope combinations
- Token Handling
    - JWT access tokens
    - ID token customization
    - Token revocation / introspection (RFC 7009 / RFC 7662)

## Multi-Tenancy

- Per-tenant configuration (clients, users, credentials, hooks, etc.)
- Shared DB or schema-separated models
- Admin APIs for tenant management

## Hooks & Events

- Security Event Publishing
    - Login failure, suspicious IP, invalid token, MFA bypass, new client detected
- Notification Integration
    - Slack, Webhook, other platforms
- Event Retry and DLQ support

## Verifiable Credentials

- Credential Issuance
    - Custom credential types
    - Workflow orchestration
- Credential Binding
    - With OIDC claims or external evidence

---

👉 Explore how to [configure each feature](../configuration/index.md).
