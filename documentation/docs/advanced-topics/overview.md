# Overview
## Advanced Topics

This section covers advanced usage, customization, and deployment scenarios for **idp-server**.

## Security Best Practices

- Always enable HTTPS (TLS termination at proxy)
- Enable MFA at tenant and/or client level
- Monitor `acr_values` and enforce strict authentication policies
- Use secure token storage (e.g., encrypted access tokens)
- Sanitize redirect URIs and input parameters

## Logging & Monitoring

- Structured JSON logs emitted by each module
- Security events published per tenant:
    - `LOGIN_FAILURE`, `INVALID_TOKEN`, `NEW_CLIENT`, etc.
- Integration with:
    - Slack (via hook)
    - Generic Webhook
    - SIEM tools (via Log Stream)

## Caching Strategy

- In-memory or external cache (e.g., Redis)
- Cached entities:
    - Tenant configuration
    - Client metadata
    - User credential state
- TTL and cache invalidation supported per-type

## Custom Transaction Handling

- Transaction boundary managed at `@Transaction` annotated entry points
- Fine-grained rollback handling via custom exception layer
- Thread-local vs explicit tenant context switch supported

## Federated Login

- OIDC Federation support (e.g., Azure AD, Google)
- Planned: SAML federation via abstracted adapter layer
- Dynamic IdP resolution per tenant
- Caching of provider metadata and keys

## Hook Extensions

- Define hook triggers in tenant config
- Use `SecurityEventHookResult` to track downstream hook calls
- Retry & backoff policies available
- Future: DLQ support for failed hook executions

## Plugin Architecture (Planned)

- SPI-based extension points
- Credential Issuer plugins (VC, mDL, eKYC)
- External Identity Verifier integration

---

👉 Continue to [Deployment](../deployment/index.md) for practical setup and hosting guidance.
